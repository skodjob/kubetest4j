/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionList;
import io.skodjob.kubetest4j.interfaces.ResourceType;

import java.util.function.Consumer;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class SubscriptionType implements ResourceType<Subscription> {

    private final MixedOperation<Subscription, SubscriptionList, Resource<Subscription>> client;

    /**
     * Constructor
     */
    public SubscriptionType() {
        this.client = KubeResourceManager.get().kubeClient().getOpenShiftClient().operatorHub().subscriptions();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "Subscription";
    }

    /**
     * Get specific client for resoruce
     *
     * @return specific client
     */
    @Override
    public MixedOperation<?, ?, ?> getClient() {
        return client;
    }

    /**
     * Creates specific {@link Subscription} resource
     *
     * @param resource {@link Subscription} resource
     */
    @Override
    public void create(Subscription resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link Subscription} resource
     *
     * @param resource {@link Subscription} resource that will be updated
     */
    @Override
    public void update(Subscription resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link Subscription} resource from Namespace in current context
     *
     * @param resource {@link Subscription} resource that will be deleted
     */
    @Override
    public void delete(Subscription resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link Subscription} resource using {@link Consumer}
     * from which is the current {@link Subscription} resource updated
     *
     * @param resource {@link Subscription} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(Subscription resource, Consumer<Subscription> editor) {
        Subscription toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link Subscription} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(Subscription resource) {
        return resource != null;
    }

    /**
     * Waits for {@link Subscription} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(Subscription resource) {
        return resource == null;
    }
}
