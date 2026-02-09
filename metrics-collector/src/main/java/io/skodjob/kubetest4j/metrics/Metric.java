/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.metrics;

import java.util.Map;

/**
 * Abstract representation of metric
 */
public abstract class Metric {
    String name;
    Map<String, String> labels;
    MetricType type;
    String stringMetric;

    /**
     * constructor
     *
     * @param name          name of metric
     * @param labels        labels
     * @param type          type
     * @param stringMetric  original (not parsed) metric in String
     */
    Metric(String name, Map<String, String> labels, MetricType type, String stringMetric) {
        this.name = name;
        this.labels = labels;
        this.type = type;
        this.stringMetric = stringMetric;
    }

    /**
     * Return labels
     *
     * @return labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Return type
     *
     * @return type of metric
     */
    public MetricType getType() {
        return type;
    }

    /**
     * Get name
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Get original metric, from which was the object created.
     *
     * @return original metric
     */
    public String getStringMetric() {
        return stringMetric;
    }

    /**
     * Metric string representation
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Metric{" +
            "name='" + name + '\'' +
            ", labels=" + labels +
            ", type=" + type +
            ", stringMetric=" + stringMetric +
            '}';
    }
}
