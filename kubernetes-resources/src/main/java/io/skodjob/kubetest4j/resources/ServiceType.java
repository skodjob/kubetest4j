/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class ServiceType implements ResourceType<Service> {

    private final MixedOperation<Service, ServiceList, ServiceResource<Service>> client;

    /**
     * Constructor
     */
    public ServiceType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().services();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "Service";
    }

    /**
     * Creates specific {@link Service} resource
     *
     * @param resource {@link Service} resource
     */
    @Override
    public void create(Service resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
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
     * Updates specific {@link Service} resource
     *
     * @param resource {@link Service} resource that will be updated
     */
    @Override
    public void update(Service resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link Service} resource from Namespace in current context
     *
     * @param resource {@link Service} resource that will be deleted
     */
    @Override
    public void delete(Service resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link Service} resource using {@link Consumer}
     * from which is the current {@link Service} resource updated
     *
     * @param resource {@link Service} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(Service resource, Consumer<Service> editor) {
        Service toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link Service} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(Service resource) {
        return resource != null;
    }

    /**
     * Waits for {@link Service} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(Service resource) {
        return resource == null;
    }
}
