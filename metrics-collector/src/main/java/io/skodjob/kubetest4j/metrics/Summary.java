/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * Summary metric type
 */
public class Summary extends Metric {
    Map<Double, Double> quantiles = new HashMap<>();
    double sum;
    int count;

    /**
     * Constructor
     *
     * @param name          name of metric
     * @param labels        labels
     * @param stringMetric  original (not parsed) metric in String
     */
    public Summary(String name, Map<String, String> labels, String stringMetric) {
        super(name, labels, MetricType.SUMMARY, stringMetric);
    }

    /**
     * Sets quantile
     *
     * @param quantile quantile
     * @param value    value
     */
    public void addQuantile(double quantile, double value) {
        quantiles.put(quantile, value);
    }

    /**
     * Sets sum
     *
     * @param sum sum
     */
    public void setSum(double sum) {
        this.sum = sum;
    }

    /**
     * Sets count
     *
     * @param count count
     */
    public void setCount(int count) {
        this.count = count;
    }

    /**
     * Returns quantiles
     *
     * @return quantiles
     */
    public Map<Double, Double> getQuantiles() {
        return quantiles;
    }

    /**
     * Returns sum
     *
     * @return sum
     */
    public double getSum() {
        return sum;
    }

    /**
     * Returns count
     *
     * @return cont
     */
    public int getCount() {
        return count;
    }

    /**
     * Metric string representation
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Summary{" +
            "name='" + name + '\'' +
            ", labels=" + labels +
            ", quantiles=" + quantiles +
            ", sum=" + sum +
            ", count=" + count +
            ", type=" + type +
            ", stringMetric=" + stringMetric +
            '}';
    }
}
