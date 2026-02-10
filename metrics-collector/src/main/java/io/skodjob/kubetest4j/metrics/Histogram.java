/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * Histogram metric type
 */
public class Histogram extends Metric {
    Map<Double, Double> buckets = new HashMap<>();
    double sum;
    int count;

    /**
     * Constructor
     *
     * @param name          name of metric
     * @param labels        labels
     * @param stringMetric  original (not parsed) metric in String
     */
    public Histogram(String name, Map<String, String> labels, String stringMetric) {
        super(name, labels, MetricType.HISTOGRAM, stringMetric);
    }

    /**
     * Set bucket
     *
     * @param upperBound upperbound
     * @param count      count
     */
    public void addBucket(double upperBound, double count) {
        buckets.put(upperBound, count);
    }

    /**
     * Set sum
     *
     * @param sum sum
     */
    public void setSum(double sum) {
        this.sum = sum;
    }

    /**
     * Set count
     *
     * @param count count
     */
    public void setCount(int count) {
        this.count = count;
    }

    /**
     * Return sum
     *
     * @return sum
     */
    public double getSum() {
        return sum;
    }

    /**
     * Return count
     *
     * @return count
     */
    public int getCount() {
        return count;
    }

    /**
     * Return all buckets
     *
     * @return buckets
     */
    public Map<Double, Double> getBuckets() {
        return buckets;
    }

    /**
     * Metric string representation
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Histogram{" +
            "name='" + name + '\'' +
            ", labels=" + labels +
            ", buckets=" + buckets +
            ", sum=" + sum +
            ", count=" + count +
            ", type=" + type +
            ", stringMetric=" + stringMetric +
            '}';
    }
}
