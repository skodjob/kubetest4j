/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class RoleBindingType implements ResourceType<RoleBinding> {

    private final MixedOperation<RoleBinding, RoleBindingList, Resource<RoleBinding>> client;

    /**
     * Constructor
     */
    public RoleBindingType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().rbac().roleBindings();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "RoleBinding";
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
     * Creates specific {@link RoleBinding} resource
     *
     * @param resource {@link RoleBinding} resource
     */
    @Override
    public void create(RoleBinding resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link RoleBinding} resource
     *
     * @param resource {@link RoleBinding} resource that will be updated
     */
    @Override
    public void update(RoleBinding resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link RoleBinding} resource from Namespace in current context
     *
     * @param resource {@link RoleBinding} resource that will be deleted
     */
    @Override
    public void delete(RoleBinding resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link RoleBinding} resource using {@link Consumer}
     * from which is the current {@link RoleBinding} resource updated
     *
     * @param resource {@link RoleBinding} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(RoleBinding resource, Consumer<RoleBinding> editor) {
        RoleBinding toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link RoleBinding} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(RoleBinding resource) {
        return resource != null;
    }

    /**
     * Waits for {@link RoleBinding} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(RoleBinding resource) {
        return resource == null;
    }
}
