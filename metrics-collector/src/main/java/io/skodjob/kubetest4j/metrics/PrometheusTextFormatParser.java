/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.metrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser class
 */
public class PrometheusTextFormatParser {

    private PrometheusTextFormatParser() {
        // empty constructor
    }

    /**
     * Parse metrics from string data
     *
     * @param data string data in prometheus text format
     * @return parsed data in list of Metric
     * @throws IOException IOException
     */
    public static List<Metric> parse(String data) throws IOException {
        List<Metric> metrics = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(data));
        String line;
        String type = "";
        Histogram currentHistogram = null;
        Summary currentSummary = null;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                if (line.contains("TYPE")) {
                    String[] parts = line.split(" ");
                    // e.g. # TYPE metric_name histogram => "histogram" is at parts[3]
                    type = parts[3];
                }
                continue;
            }
            int lastSpace = line.lastIndexOf(' ');
            if (lastSpace == -1) {
                // Invalid line, skip
                continue;
            }

            String metricNameAndLabels = line.substring(0, lastSpace);
            String valueStr = line.substring(lastSpace + 1).trim();
            double value = Double.parseDouble(valueStr);

            String[] nameAndLabels = parseNameAndLabels(metricNameAndLabels);
            String name = nameAndLabels[0];
            Map<String, String> labels = parseLabels(nameAndLabels[1]);
            Map<String, String> customLabels = getCustomLabels(labels);

            if (name.endsWith("_total")) {
                // Standard Prometheus pattern for counters
                metrics.add(new Counter(name, customLabels, line, value));

            } else if (name.contains("_bucket")) {
                // It's a bucket line (histogram)
                if (currentHistogram == null
                    || !currentHistogram.name.equals(name)
                    || !currentHistogram.labels.equals(customLabels)) {
                    currentHistogram = new Histogram(name, customLabels, line);
                    metrics.add(currentHistogram);
                }
                double upperBound = labels.get("le").contains("+Inf")
                    ? Double.MAX_VALUE
                    : Double.parseDouble(labels.get("le"));
                currentHistogram.addBucket(upperBound, value);

            } else if (name.endsWith("_sum")) {
                // Possibly part of a histogram or summary
                String baseName = name.substring(0, name.length() - 4);

                // If it matches the ongoing histogram
                if (currentHistogram != null
                    && currentHistogram.name.equals(baseName + "_bucket")
                    && currentHistogram.labels.equals(customLabels)) {
                    currentHistogram.setSum(value);
                // If it matches the ongoing summary
                } else if (currentSummary != null
                    && currentSummary.name.equals(baseName)
                    && currentSummary.labels.equals(customLabels)) {
                    currentSummary.setSum(value);
                }

                // Regardless of histogram/summary match, also treat it as a Gauge for easy collection
                metrics.add(new Gauge(name, customLabels, line, value));

            } else if (name.endsWith("_count")) {
                // Possibly part of a histogram or summary
                String baseName = name.substring(0, name.length() - 6);

                if (currentHistogram != null
                    && currentHistogram.name.equals(baseName + "_bucket")
                    && currentHistogram.labels.equals(customLabels)) {
                    currentHistogram.setCount((int) value);

                } else if (currentSummary != null
                    && currentSummary.name.equals(baseName)
                    && currentSummary.labels.equals(customLabels)) {
                    currentSummary.setCount((int) value);

                } else if (type != null) {
                    // If type is known gauge/counter, parse accordingly
                    if (type.equals("gauge")) {
                        metrics.add(new Gauge(name, customLabels, line, value));
                    } else if (type.equals("counter")) {
                        metrics.add(new Counter(name, customLabels, line, value));
                    }
                }

            } else if (labels.containsKey("quantile")) {
                // Summaries: quantiles
                if (currentSummary == null
                    || !currentSummary.name.equals(name)
                    || !currentSummary.labels.equals(customLabels)) {
                    currentSummary = new Summary(name, customLabels, line);
                    metrics.add(currentSummary);
                }
                double quantile = Double.parseDouble(labels.get("quantile"));
                currentSummary.addQuantile(quantile, value);

            } else {
                // Plain gauge
                metrics.add(new Gauge(name, labels, line, value));
            }
        }
        return metrics;
    }


    /**
     * Method that "removes" Prometheus labels (connected to buckets or summary) and returns just the custom labels
     * provided by the user or application.
     *
     * @param labels    parsed labels from the Prometheus metric
     * @return  custom labels from the metric
     */
    private static Map<String, String> getCustomLabels(Map<String, String> labels) {
        Map<String, String> customLabels = new HashMap<>(labels);
        customLabels.remove("le");
        customLabels.remove("quantile");
        return customLabels;
    }

    private static String[] parseNameAndLabels(String metricNameAndLabels) {
        int labelStartIndex = metricNameAndLabels.indexOf('{');
        if (labelStartIndex == -1) {
            return new String[]{metricNameAndLabels, ""};
        }
        String name = metricNameAndLabels.substring(0, labelStartIndex);
        String labels = metricNameAndLabels.substring(labelStartIndex);
        return new String[]{name, labels};
    }

    private static Map<String, String> parseLabels(String labelString) {
        Map<String, String> labels = new HashMap<>();
        if (labelString.isEmpty()) {
            return labels;
        }
        // Remove surrounding '{' and '}'
        labelString = labelString.substring(1, labelString.length() - 1);
        String[] labelPairs = labelString.split(",");
        for (String labelPair : labelPairs) {
            labelPair = labelPair.trim();
            if (labelPair.isEmpty()) continue;
            String[] keyValue = labelPair.split("=", 2);
            if (keyValue.length < 2) continue;
            String key = keyValue[0].trim();
            String value = keyValue[1].replaceAll("^\"|\"$", "").trim();
            labels.put(key, value);
        }
        return labels;
    }
}
