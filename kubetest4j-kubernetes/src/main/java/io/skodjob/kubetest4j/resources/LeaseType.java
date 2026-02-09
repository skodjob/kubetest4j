/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class LeaseType implements ResourceType<Lease> {

    private final MixedOperation<Lease, LeaseList, Resource<Lease>> client;

    /**
     * Constructor
     */
    public LeaseType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().leases();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "Lease";
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
     * Creates specific {@link Lease} resource
     *
     * @param resource {@link Lease} resource
     */
    @Override
    public void create(Lease resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link Lease} resource
     *
     * @param resource {@link Lease} resource that will be updated
     */
    @Override
    public void update(Lease resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link Lease} resource from Namespace in current context
     *
     * @param resource {@link Lease} resource that will be deleted
     */
    @Override
    public void delete(Lease resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link Lease} resource using {@link Consumer}
     * from which is the current {@link Lease} resource updated
     *
     * @param resource {@link Lease} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(Lease resource, Consumer<Lease> editor) {
        Lease toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link Lease} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(Lease resource) {
        return client.resource(resource).isReady();
    }

    /**
     * Waits for {@link Lease} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(Lease resource) {
        return resource == null;
    }
}
