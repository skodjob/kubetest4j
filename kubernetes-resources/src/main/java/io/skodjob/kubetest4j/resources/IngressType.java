/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for Ingress resource
 */
public class IngressType implements ResourceType<Ingress> {

    private final MixedOperation<Ingress, IngressList, Resource<Ingress>> client;

    /**
     * Constructor
     */
    public IngressType() {
        this(KubeResourceManager.get().kubeClient().getClient().network().v1().ingresses());
    }

    /**
     * Constructor with client for testing
     *
     * @param client client
     */
    IngressType(MixedOperation<Ingress, IngressList, Resource<Ingress>> client) {
        this.client = client;
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "Ingress";
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
     * Creates specific {@link Ingress} resource
     *
     * @param resource {@link Ingress} resource
     */
    @Override
    public void create(Ingress resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link Ingress} resource
     *
     * @param resource {@link Ingress} resource that will be updated
     */
    @Override
    public void update(Ingress resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link Ingress} resource from Namespace in current context
     *
     * @param resource {@link Ingress} resource that will be deleted
     */
    @Override
    public void delete(Ingress resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link Ingress} resource using {@link Consumer}
     * from which is the current {@link Ingress} resource updated
     *
     * @param resource {@link Ingress} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(Ingress resource, Consumer<Ingress> editor) {
        Ingress toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link Ingress} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(Ingress resource) {
        return resource != null;
    }

    /**
     * Waits for {@link Ingress} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(Ingress resource) {
        return resource == null;
    }
}
