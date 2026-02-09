/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.metrics;

import java.util.Map;

/**
 * Counter metric type
 */
public class Counter extends Metric {
    double value;

    /**
     * Constructor
     *
     * @param name          name of metric
     * @param labels        labels
     * @param value         value
     * @param stringMetric  original (not parsed) metric in String
     */
    public Counter(String name, Map<String, String> labels, String stringMetric, double value) {
        super(name, labels, MetricType.COUNTER, stringMetric);
        this.value = value;
    }

    /**
     * Returns value
     *
     * @return value
     */
    public double getValue() {
        return value;
    }

    /**
     * Metric string representation
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Counter{" +
            "name='" + name + '\'' +
            ", labels=" + labels +
            ", value=" + value +
            ", type=" + type +
            ", stringMetric=" + stringMetric +
            '}';
    }
}
