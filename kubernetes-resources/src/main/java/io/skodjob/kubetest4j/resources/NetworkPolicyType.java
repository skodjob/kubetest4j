/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class NetworkPolicyType implements ResourceType<NetworkPolicy> {

    private final MixedOperation<NetworkPolicy, NetworkPolicyList, Resource<NetworkPolicy>> client;

    /**
     * Constructor
     */
    public NetworkPolicyType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().network().networkPolicies();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "NetworkPolicy";
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
     * Creates specific {@link NetworkPolicy} resource
     *
     * @param resource {@link NetworkPolicy} resource
     */
    @Override
    public void create(NetworkPolicy resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link NetworkPolicy} resource
     *
     * @param resource {@link NetworkPolicy} resource that will be updated
     */
    @Override
    public void update(NetworkPolicy resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link NetworkPolicy} resource from Namespace in current context
     *
     * @param resource {@link NetworkPolicy} resource that will be deleted
     */
    @Override
    public void delete(NetworkPolicy resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link NetworkPolicy} resource using {@link Consumer}
     * from which is the current {@link NetworkPolicy} resource updated
     *
     * @param resource {@link NetworkPolicy} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(NetworkPolicy resource, Consumer<NetworkPolicy> editor) {
        NetworkPolicy toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link NetworkPolicy} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(NetworkPolicy resource) {
        return resource != null;
    }

    /**
     * Waits for {@link NetworkPolicy} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(NetworkPolicy resource) {
        return resource == null;
    }
}
