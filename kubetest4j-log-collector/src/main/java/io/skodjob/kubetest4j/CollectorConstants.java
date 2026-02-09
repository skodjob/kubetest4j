/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

/**
 * Class containing constants used in {@link LogCollector}
 */
public interface CollectorConstants {
    /**
     * Pod resource type
     */
    String POD = "pod";
    /**
     * Pod resource type - plural
     */
    String PODS = "pods";
    /**
     * Container resource type
     */
    String CONTAINER = "container";
    /**
     * Events resource type
     */
    String EVENTS = "events";
    /**
     * Prefix for logs files
     */
    String LOGS = "logs";
    /**
     * Prefix for description files
     */
    String DESCRIBE = "describe";

    /**
     * Cluster wide resources folder name
     */
    String CLUSTER_WIDE_FOLDER = "cluster-wide-resources";

    /**
     * Suffix for "previous" logs of Pod and container
     */
    String PREVIOUS = "previous";
}
