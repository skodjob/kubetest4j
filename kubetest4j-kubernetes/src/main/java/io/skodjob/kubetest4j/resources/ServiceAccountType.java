/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.ServiceAccountResource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class ServiceAccountType implements ResourceType<ServiceAccount> {

    private final MixedOperation<ServiceAccount, ServiceAccountList, ServiceAccountResource> client;

    /**
     * Constructor
     */
    public ServiceAccountType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().serviceAccounts();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "ServiceAccount";
    }

    /**
     * Creates specific {@link ServiceAccount} resource
     *
     * @param resource {@link ServiceAccount} resource
     */
    @Override
    public void create(ServiceAccount resource) {
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
     * Updates specific {@link ServiceAccount} resource
     *
     * @param resource {@link ServiceAccount} resource that will be updated
     */
    @Override
    public void update(ServiceAccount resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link ServiceAccount} resource from Namespace in current context
     *
     * @param resource {@link ServiceAccount} resource that will be deleted
     */
    @Override
    public void delete(ServiceAccount resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link ServiceAccount} resource using {@link Consumer}
     * from which is the current {@link ServiceAccount} resource updated
     *
     * @param resource {@link ServiceAccount} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(ServiceAccount resource, Consumer<ServiceAccount> editor) {
        ServiceAccount toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link ServiceAccount} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(ServiceAccount resource) {
        return resource != null;
    }

    /**
     * Waits for {@link ServiceAccount} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(ServiceAccount resource) {
        return resource == null;
    }
}
