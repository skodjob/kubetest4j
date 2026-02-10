/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.interfaces;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.skodjob.kubetest4j.KubeTestConstants;

/**
 * Class for encapsulating methods related to {@link T} resource.
 *
 * @param <T> resource type
 */
public interface ResourceType<T extends HasMetadata> {

    /**
     * Get specific client for resoruce
     *
     * @return specific client
     */
    NonNamespaceOperation<?, ?, ?> getClient();

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    String getKind();

    /**
     * Timeout for resource readiness.
     * Defaults to {@link KubeTestConstants#GLOBAL_TIMEOUT_MEDIUM}.
     *
     * @return  timeout for resource readiness
     */
    default Long getTimeoutForResourceReadiness() {
        return KubeTestConstants.GLOBAL_TIMEOUT_MEDIUM;
    }

    /**
     * Creates specific {@link T} resource
     *
     * @param resource {@link T} resource
     */
    void create(T resource);

    /**
     * Updates specific {@link T} resource
     *
     * @param resource {@link T} resource that will be updated
     */
    void update(T resource);

    /**
     * Deletes {@link T} resource from Namespace in current context
     *
     * @param resource {@link T} resource that will be deleted
     */
    void delete(T resource);

    /**
     * Replaces {@link T} resource using {@link Consumer}
     * from which is the current {@link T} resource updated
     *
     * @param resource {@link T} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    void replace(T resource, Consumer<T> editor);

    /**
     * Confirms that {@link T} is ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    boolean isReady(T resource);

    /**
     * Confirms that {@link T} is deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    boolean isDeleted(T resource);
}
