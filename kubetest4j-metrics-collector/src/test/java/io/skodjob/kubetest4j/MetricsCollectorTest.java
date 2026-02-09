/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.skodjob.kubetest4j.exceptions.IncompleteMetricsException;
import io.skodjob.kubetest4j.exceptions.MetricsCollectionException;
import io.skodjob.kubetest4j.exceptions.NoPodsFoundException;
import io.skodjob.kubetest4j.metrics.Gauge;
import io.skodjob.kubetest4j.metrics.Metric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MetricsCollectorTest {

    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        this.metricsCollector = new MetricsCollector.Builder()
            .withNamespaceName("namespace")
            .withScraperPodName("scraperPod")
            .withComponent(new DummyMetricsComponent())
            .build();
    }

    @Test
    void testCollectMetricWithLabelsNoDataAvailable() {
        // Setup
        HashMap<String, List<Metric>> emptyData = new HashMap<>();
        this.metricsCollector.setCollectedData(emptyData);

        // Execution
        List<Metric> results = this.metricsCollector.collectMetricWithLabels("pod", "nonexistentMetric");

        // Verification
        assertTrue(results.isEmpty(), "Results should be empty when no relevant data is available");
    }

    @Test
    void testCollectMetricWithLabelsInvalidDataFormat() {
        // Setup
        HashMap<String, List<Metric>> invalidData = new HashMap<>();
        invalidData.put("pod", Collections.singletonList(new Gauge("metric_name",
            Collections.singletonMap("label", "value"), "metric_name {label=value} 32", 32)));
        this.metricsCollector.setCollectedData(invalidData);

        // Execution
        List<Metric> results = this.metricsCollector.collectMetricWithLabels("pod", "metricName");

        // Verification
        assertTrue(results.isEmpty(), "Results should be empty with invalid data format");
    }

    @Test
    void testBuilderInvalidParameters() {
        // Verification
        assertThrows(InvalidParameterException.class, () -> new MetricsCollector.Builder().build(),
            "Should throw InvalidParameterException when required parameters are not set");
    }

    @Test
    void testCollectMetricWithLabelsMatchingEntries() {
        HashMap<String, List<Metric>> invalidData = new HashMap<>();
        invalidData.put("pod", Collections.singletonList(new Gauge("metric_name",
            Collections.singletonMap("label", "value"), "metric_name {label=value} 100", 100)));
        this.metricsCollector.setCollectedData(invalidData);

        List<Metric>  results = this.metricsCollector.collectMetricWithLabels("pod", "label");
        assertFalse(results.isEmpty());
        assertEquals(100.0, ((Gauge)results.get(0)).getValue(), 0.01);
    }

    @Test
    public void testCollectMetricsHandlesNPE() {
        MetricsCollector collector = spy(metricsCollector);
        doThrow(new NullPointerException("Null pointer access"))
            .when(collector).collectMetricsFromPodsWithoutWait();

        // Assert that a MetricsCollectionException is thrown when an NPE occurs
        MetricsCollectionException ex = assertThrows(MetricsCollectionException.class, () ->
            collector.collectMetricsFromPods(1000));

        // Verify that the exception message contains specific information about the NPE
        assertTrue(ex.getMessage().contains("Null pointer access"),
            "Exception message should indicate the nature of the NPE.");
    }

    @Test
    void testBuilderWithScraperPodImageAndDeploy() {
        MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("namespace")
            .withScraperPodName("scraper-pod")
            .withScraperPodImage("quay.io/curl/curl:7.85")
            .withDeployScraperPod()
            .withComponent(new DummyMetricsComponent())
            .build();

        assertEquals("quay.io/curl/curl:7.85", collector.getScraperPodImage());
        assertTrue(collector.getDeployScraperPod());
    }

    @Test
    void testDetermineExceptionNoData() {
        MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("ns")
            .withScraperPodName("pod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build();

        MetricsCollector.MetricsCollectionStatus status = new MetricsCollector.MetricsCollectionStatus();
        status.setType(MetricsCollector.MetricsCollectionStatus.Type.NO_DATA);
        status.setMessage("No data available");

        MetricsCollectionException ex = collector.determineExceptionFromStatus(status);
        assertInstanceOf(NoPodsFoundException.class, ex);
        assertEquals("No data available", ex.getMessage());
    }

    @Test
    void testDetermineExceptionIncompleteData() {
        MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("ns")
            .withScraperPodName("pod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build();

        MetricsCollector.MetricsCollectionStatus status = new MetricsCollector.MetricsCollectionStatus();
        status.setType(MetricsCollector.MetricsCollectionStatus.Type.INCOMPLETE_DATA);
        status.setMessage("Metrics incomplete");

        MetricsCollectionException ex = collector.determineExceptionFromStatus(status);
        assertInstanceOf(IncompleteMetricsException.class, ex);
        assertEquals("Metrics incomplete", ex.getMessage());
    }

    @Test
    void testDetermineExceptionErrorWithCause() {
        MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("ns")
            .withScraperPodName("pod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build();

        Exception root = new IllegalStateException("Something failed");
        MetricsCollector.MetricsCollectionStatus status = new MetricsCollector.MetricsCollectionStatus();
        status.setType(MetricsCollector.MetricsCollectionStatus.Type.ERROR);
        status.setMessage("Error during scraping");
        status.setException(root);

        MetricsCollectionException ex = collector.determineExceptionFromStatus(status);
        assertInstanceOf(MetricsCollectionException.class, ex);
        assertEquals("Error during scraping", ex.getMessage());
        assertSame(root, ex.getCause());
    }

    @Test
    void testDetermineExceptionDefaultFallback() {
        MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("ns")
            .withScraperPodName("pod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build();

        MetricsCollector.MetricsCollectionStatus status = new MetricsCollector.MetricsCollectionStatus();
        status.setType(null); // default fallback path

        MetricsCollectionException ex = collector.determineExceptionFromStatus(status);
        assertInstanceOf(MetricsCollectionException.class, ex);
        assertEquals("Unknown error occurred during metrics collection", ex.getMessage());
    }

    @Test
    void testWaitTimeoutPathInCollectMetrics() {
        MetricsCollector collector = spy(new MetricsCollector.Builder()
            .withNamespaceName("ns")
            .withScraperPodName("pod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build());

        doThrow(new RuntimeException("Timeout test exception")).when(collector).collectMetricsFromPodsWithoutWait();

        MetricsCollectionException ex = assertThrows(MetricsCollectionException.class,
            () -> collector.collectMetricsFromPods(1000));

        assertTrue(ex.getMessage().contains("Timeout test exception"),
            "Exception message should reflect cause during timeout wait");
    }

    static class DummyMetricsComponent implements MetricsComponent {
        @Override
        public int getDefaultMetricsPort() {
            return 8080; // Return a dummy port typically used for HTTP services
        }

        @Override
        public String getDefaultMetricsPath() {
            return "/metrics"; // Return a dummy path where metrics might be collected
        }

        @Override
        public LabelSelector getLabelSelector() {
            final Map<String, String> matchLabels = new HashMap<>();

            matchLabels.put("app", "mockApp"); // Return a dummy selector

            return new LabelSelectorBuilder()
                .withMatchLabels(matchLabels)
                .build();
        }
    }
}
