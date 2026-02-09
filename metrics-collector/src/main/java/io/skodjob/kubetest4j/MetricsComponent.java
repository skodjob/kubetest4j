/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.LabelSelector;

/**
 * Interface for Kubernetes components to support metrics collection.
 * Implementations of this interface should provide details necessary to collect metrics,
 * such as the default metrics port, path, and a method to fetch applicable pods.
 */
public interface MetricsComponent {

    /**
     * Returns the default port number used for metrics collection.
     *
     * @return int representing the default metrics port.
     */
    int getDefaultMetricsPort();

    /**
     * Returns the default path used for metrics collection.
     *
     * @return String representing the default metrics path.
     */
    String getDefaultMetricsPath();

    /**
     * Returns the label selector used to fetch the pods relevant to the component.
     *
     * @return LabelSelector for identifying relevant pods.
     */
    LabelSelector getLabelSelector();
}
