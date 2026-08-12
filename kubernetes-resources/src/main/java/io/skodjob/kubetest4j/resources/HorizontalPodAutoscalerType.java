/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for HorizontalPodAutoscaler resource
 */
public class HorizontalPodAutoscalerType implements ResourceType<HorizontalPodAutoscaler> {

    private final MixedOperation<HorizontalPodAutoscaler, HorizontalPodAutoscalerList,
            Resource<HorizontalPodAutoscaler>> client;

    /**
     * Constructor
     */
    public HorizontalPodAutoscalerType() {
        this(KubeResourceManager.get().kubeClient().getClient().autoscaling().v2().horizontalPodAutoscalers());
    }

    /**
     * Constructor with client for testing
     *
     * @param client client
     */
    HorizontalPodAutoscalerType(MixedOperation<HorizontalPodAutoscaler, HorizontalPodAutoscalerList,
            Resource<HorizontalPodAutoscaler>> client) {
        this.client = client;
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "HorizontalPodAutoscaler";
    }

    /**
     * Get specific client for resource
     *
     * @return specific client
     */
    @Override
    public MixedOperation<?, ?, ?> getClient() {
        return client;
    }

    /**
     * Creates specific {@link HorizontalPodAutoscaler} resource
     *
     * @param resource {@link HorizontalPodAutoscaler} resource
     */
    @Override
    public void create(HorizontalPodAutoscaler resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link HorizontalPodAutoscaler} resource
     *
     * @param resource {@link HorizontalPodAutoscaler} resource that will be updated
     */
    @Override
    public void update(HorizontalPodAutoscaler resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link HorizontalPodAutoscaler} resource from Namespace in current context
     *
     * @param resource {@link HorizontalPodAutoscaler} resource that will be deleted
     */
    @Override
    public void delete(HorizontalPodAutoscaler resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link HorizontalPodAutoscaler} resource using {@link Consumer}
     * from which is the current {@link HorizontalPodAutoscaler} resource updated
     *
     * @param resource {@link HorizontalPodAutoscaler} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(HorizontalPodAutoscaler resource, Consumer<HorizontalPodAutoscaler> editor) {
        HorizontalPodAutoscaler toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link HorizontalPodAutoscaler} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(HorizontalPodAutoscaler resource) {
        return resource != null;
    }

    /**
     * Waits for {@link HorizontalPodAutoscaler} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(HorizontalPodAutoscaler resource) {
        return resource == null;
    }
}
