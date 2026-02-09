/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.metrics;

/**
 * Metric types
 */
public enum MetricType {
    /**
     * Gauge
     */
    GAUGE("gauge"),

    /**
     * Counter
     */
    COUNTER("counter"),

    /**
     * Histogram
     */
    HISTOGRAM("histogram"),

    /**
     * Summary
     */
    SUMMARY("summary"),

    /**
     * Untyped
     */
    UNTYPED("untyped");

    private final String type;

    /**
     * Set type of metric from string
     *
     * @param type metric type
     */
    MetricType(String type) {
        this.type = type;
    }

    /**
     * Return type
     *
     * @return type
     */
    public String getType() {
        return type;
    }
}
