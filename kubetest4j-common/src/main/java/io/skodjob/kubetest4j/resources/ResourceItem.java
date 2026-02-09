/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.skodjob.kubetest4j.interfaces.ThrowableRunner;

/**
 * Represents an item containing a Kubernetes resource and a runnable action.
 *
 * @param throwableRunner delete method
 * @param resource        resource (can be null)
 * @param <T>             Type of kubernetes resource
 */
public record ResourceItem<T extends HasMetadata>(ThrowableRunner throwableRunner, T resource) {

    /**
     * Constructs a ResourceItem with the given runnable action.
     *
     * @param throwableRunner The runnable action to execute.
     */
    public ResourceItem(ThrowableRunner throwableRunner) {
        this(throwableRunner, null);
    }
}
