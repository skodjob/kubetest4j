/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.metrics.Counter;
import io.skodjob.kubetest4j.metrics.Gauge;
import io.skodjob.kubetest4j.metrics.Histogram;
import io.skodjob.kubetest4j.metrics.Metric;
import io.skodjob.kubetest4j.metrics.MetricType;
import io.skodjob.kubetest4j.metrics.PrometheusTextFormatParser;
import io.skodjob.kubetest4j.metrics.Summary;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PrometheusTextFormatParserTest {

    @Test
    void testParseGaugeMetric() throws IOException {
        String data = "# TYPE my_gauge gauge\n" +
            "my_gauge{label=\"value\"} 123.45\n";
        List<Metric> metrics = PrometheusTextFormatParser.parse(data);
        assertEquals(1, metrics.size());
        Metric m = metrics.get(0);
        assertTrue(m instanceof Gauge);
        assertEquals(123.45, ((Gauge) m).getValue(), 0.0001);
        assertEquals("value", m.getLabels().get("label"));
        assertEquals("my_gauge", m.getName());
        assertEquals("my_gauge{label=\"value\"} 123.45", m.getStringMetric());
        assertTrue(m.toString().contains("my_gauge"));
        assertEquals(MetricType.GAUGE, m.getType());

    }

    @Test
    void testParseCounterMetric() throws IOException {
        String data = "# TYPE my_counter counter\n" +
            "my_counter_total{label=\"cnt\"} 10\n" +
            "my_counter_count{label=\"cnt\"} 10\n";
        List<Metric> metrics = PrometheusTextFormatParser.parse(data);
        assertEquals(2, metrics.size());
        for (Metric m : metrics) {
            assertTrue(m instanceof Counter);
            assertEquals(10, ((Counter) m).getValue(), 0.0001);
            assertEquals(MetricType.COUNTER, m.getType());
        }

    }

    @Test
    void testParseHistogramMetrics() throws IOException {
        String data = "# TYPE my_histogram histogram\n" +
            "my_histogram_bucket{le=\"0.1\"} 2\n" +
            "my_histogram_bucket{le=\"1.0\"} 5\n" +
            "my_histogram_bucket{le=\"+Inf\"} 7\n" +
            "my_histogram_sum 10.0\n" +
            "my_histogram_count 7\n";
        List<Metric> metrics = PrometheusTextFormatParser.parse(data);
        assertEquals(2, metrics.size());
        Histogram hist = (Histogram) metrics.get(0);
        assertEquals(MetricType.HISTOGRAM, hist.getType());
        assertEquals(10.0, hist.getSum(), 0.0001);
        assertEquals(7, hist.getCount());
        Map<Double, Double> buckets = hist.getBuckets();
        assertEquals(3, buckets.size());
        assertEquals(2, buckets.get(0.1), 0.0001);
        assertEquals(5, buckets.get(1.0), 0.0001);
        assertEquals(7, buckets.get(Double.MAX_VALUE), 0.0001);
    }

    @Test
    void testParseSummaryMetrics() throws IOException {
        String data = "# TYPE my_summary summary\n" +
            "my_summary{quantile=\"0.5\"} 3.0\n" +
            "my_summary{quantile=\"0.9\"} 4.0\n" +
            "my_summary_sum 7.0\n" +
            "my_summary_count 2\n";
        List<Metric> metrics = PrometheusTextFormatParser.parse(data);
        // Only one summary should be created.
        assertEquals(2, metrics.size());
        Summary summary = (Summary) metrics.get(0);
        assertEquals(MetricType.SUMMARY, summary.getType());
        assertEquals(7.0, summary.getSum(), 0.0001);
        assertEquals(2, summary.getCount());
        Map<Double, Double> quantiles = summary.getQuantiles();
        assertEquals(2, quantiles.size());
        assertEquals(3.0, quantiles.get(0.5), 0.0001);
        assertEquals(4.0, quantiles.get(0.9), 0.0001);
    }

    @Test
    void testInvalidLineSkipped() throws IOException {
        String data = "invalid_line_without_space";
        List<Metric> metrics = PrometheusTextFormatParser.parse(data);
        assertEquals(0, metrics.size());
    }

    @Test
    void testParseMultipleGaugeMetrics() throws IOException {
        String data = "# TYPE custom_memory_used_bytes gauge\n" +
            "custom_memory_used_bytes{type=\"nonheap\",segment=\"segmentA\",} 1000.0\n" +
            "custom_memory_used_bytes{type=\"heap\",segment=\"segmentB\",} 2000.0\n" +
            "custom_memory_used_bytes{type=\"heap\",segment=\"segmentC\",} 3000.0\n" +
            "custom_memory_used_bytes{type=\"nonheap\",segment=\"segmentD\",} 4000.0\n" +
            "custom_memory_used_bytes{type=\"nonheap\",segment=\"segmentE\",} 5000.0\n" +
            "custom_memory_used_bytes{type=\"heap\",segment=\"segmentF\",} 6000.0\n" +
            "custom_memory_used_bytes{type=\"nonheap\",segment=\"segmentG\",} 7000.0\n" +
            "custom_memory_used_bytes{type=\"nonheap\",segment=\"segmentH\",} 8000.0\n";
        List<Metric> metrics = PrometheusTextFormatParser.parse(data);

        assertEquals(8, metrics.size());
        metrics.forEach(m -> assertTrue(m instanceof Gauge));
    }

    @Test
    void testParseGaugeSumWithTrailingComma() throws IOException {
        String data = "# TYPE custom_operation_duration_seconds_sum gauge\n" +
            "custom_operation_duration_seconds_sum{operation=\"write\",source=\"serviceA\",} 1.23456789\n";
        List<Metric> metrics = PrometheusTextFormatParser.parse(data);
        assertEquals(1, metrics.size());
        Metric m = metrics.get(0);
        assertTrue(m instanceof Gauge);
        assertEquals(1.23456789, ((Gauge) m).getValue(), 0.000001);
    }

    @Test
    void testParseHugeMixedMetrics() throws IOException {
        // A single blob containing:
        // - Gauges
        // - Counters (with _total suffix)
        // - Histograms (with buckets, _sum, and _count)
        // - Summaries (with quantiles, _sum, and _count)
        // - Various label orders, trailing commas, scientific notation, etc.
        String data = """
            # TYPE test_gauge gauge
            test_gauge 123.456
    
            # TYPE test_counter counter
            test_counter_total{mode="simple"} 777
            test_counter_total{mode="complex",extra="true",} 888.888e2
    
            # TYPE test_histogram histogram
            test_histogram_bucket{le="0.1",type="speed"} 2
            test_histogram_bucket{type="speed",le="1.0"} 5
            test_histogram_bucket{le="+Inf",type="speed"} 9
            test_histogram_sum{type="speed"} 22.0
            test_histogram_count{type="speed"} 9
    
            # TYPE test_summary summary
            test_summary{quantile="0.5",app="myapp"} 42
            test_summary{quantile="0.9",app="myapp"} 84
            test_summary{quantile="0.9",app="myapp"} 84
            test_summary_sum{app="myapp"} 139
            test_summary_count{app="myapp"} 2
    
            # TYPE custom_sum_is_gauge gauge
            custom_sum_is_gauge_sum{label="trailing_comma",} 3.14
    
            # Additional lines
            # TYPE fancy_metric gauge
            fancy_metric{label1="X", label2="Y"} 456.789
            """;

        List<Metric> metrics = PrometheusTextFormatParser.parse(data);

        assertEquals(9, metrics.size(), "Expected 7 top-level Metric objects to be parsed");

        // Check we have a histogram with correct sum and count
        Histogram hist = metrics.stream()
            .filter(m -> m instanceof Histogram && m.getName().startsWith("test_histogram_bucket"))
            .map(m -> (Histogram) m)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No histogram named 'test_histogram_bucket' found"));

        // Buckets: 0.1 =>2, 1.0 =>5, +Inf =>9
        assertEquals(3, hist.getBuckets().size());
        assertEquals(2, hist.getBuckets().get(0.1), 0.0001);
        assertEquals(5, hist.getBuckets().get(1.0), 0.0001);
        assertEquals(9, hist.getBuckets().get(Double.MAX_VALUE), 0.0001);
        assertEquals(22.0, hist.getSum(), 0.0001);
        assertEquals(9, hist.getCount());

        // Check summary with two quantiles
        Summary summ = metrics.stream()
            .filter(m -> m instanceof Summary && m.getName().equals("test_summary"))
            .map(m -> (Summary) m)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No summary named 'test_summary' found"));

        assertEquals(2, summ.getQuantiles().size());
        assertEquals(42, summ.getQuantiles().get(0.5), 0.0001);
        assertEquals(84, summ.getQuantiles().get(0.9), 0.0001);
        assertEquals(139, summ.getSum(), 0.0001);
        assertEquals(2, summ.getCount());

        Gauge customSumGauge = metrics.stream()
            .filter(m -> m instanceof Gauge && m.getName().equals("custom_sum_is_gauge_sum"))
            .map(m -> (Gauge) m)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No gauge named 'custom_sum_is_gauge_sum' found"));
        assertEquals(3.14, customSumGauge.getValue(), 0.0001);

        List<Counter> counters = metrics.stream()
            .filter(m -> m instanceof Counter && m.getName().equals("test_counter_total"))
            .map(m -> (Counter) m)
            .toList();
        assertEquals(2, counters.size());

        double[] expectedValues = {777.0, 88888.8};
        for (int i = 0; i < counters.size(); i++) {
            assertEquals(expectedValues[i], counters.get(i).getValue(), 0.001);
        }
    }
}
