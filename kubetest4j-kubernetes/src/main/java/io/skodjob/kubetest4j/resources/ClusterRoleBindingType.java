/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class ClusterRoleBindingType implements ResourceType<ClusterRoleBinding> {

    private final NonNamespaceOperation<ClusterRoleBinding, ClusterRoleBindingList,
        Resource<ClusterRoleBinding>> client;

    /**
     * Constructor
     */
    public ClusterRoleBindingType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().rbac().clusterRoleBindings();
    }

    /**
     * Get specific client for resoruce
     *
     * @return specific client
     */
    @Override
    public NonNamespaceOperation<?, ?, ?> getClient() {
        return client;
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "ClusterRoleBinding";
    }

    /**
     * Creates specific {@link ClusterRoleBinding} resource
     *
     * @param resource {@link ClusterRoleBinding} resource
     */
    @Override
    public void create(ClusterRoleBinding resource) {
        client.resource(resource).create();
    }

    /**
     * Updates specific {@link ClusterRoleBinding} resource
     *
     * @param resource {@link ClusterRoleBinding} resource that will be updated
     */
    @Override
    public void update(ClusterRoleBinding resource) {
        client.resource(resource).update();
    }

    /**
     * Deletes {@link ClusterRoleBinding} resource from Namespace in current context
     *
     * @param resource {@link ClusterRoleBinding} resource that will be deleted
     */
    @Override
    public void delete(ClusterRoleBinding resource) {
        client.withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link ClusterRoleBinding} resource using {@link Consumer}
     * from which is the current {@link ClusterRoleBinding} resource updated
     *
     * @param resource {@link ClusterRoleBinding} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(ClusterRoleBinding resource, Consumer<ClusterRoleBinding> editor) {
        ClusterRoleBinding toBeUpdated = client.withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link ClusterRoleBinding} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(ClusterRoleBinding resource) {
        return resource != null;
    }

    /**
     * Waits for {@link ClusterRoleBinding} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(ClusterRoleBinding resource) {
        return resource == null;
    }
}
