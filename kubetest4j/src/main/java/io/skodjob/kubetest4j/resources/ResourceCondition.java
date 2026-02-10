/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.Objects;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Represents a condition that can be applied to Kubernetes resources.
 *
 * @param predicate     predicate function
 * @param conditionName conditionName
 * @param <T>           Type of Kubernetes resource.
 */
public record ResourceCondition<T extends HasMetadata>(Predicate<T> predicate, String conditionName) {

    /**
     * Creates a ResourceCondition representing readiness of a resource of the given type.
     *
     * @param <T>  Type of Kubernetes resource.
     * @param type The resource type.
     * @return The ResourceCondition representing readiness.
     */
    public static <T extends HasMetadata> ResourceCondition<T> readiness(ResourceType<T> type) {
        return new ResourceCondition<>(type::isReady, "readiness");
    }

    /**
     * Creates a ResourceCondition representing deletion of a resource.
     *
     * @param <T> Type of Kubernetes resource.
     * @return The ResourceCondition representing deletion.
     */
    public static <T extends HasMetadata> ResourceCondition<T> deletion() {
        return new ResourceCondition<>(Objects::isNull, "deletion");
    }
}
