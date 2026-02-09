/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class ClusterRoleType implements ResourceType<ClusterRole> {

    private final NonNamespaceOperation<ClusterRole, ClusterRoleList, Resource<ClusterRole>> client;

    /**
     * Constructor
     */
    public ClusterRoleType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().rbac().clusterRoles();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "ClusterRole";
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
     * Creates specific {@link ClusterRole} resource
     *
     * @param resource {@link ClusterRole} resource
     */
    @Override
    public void create(ClusterRole resource) {
        client.resource(resource).create();
    }

    /**
     * Updates specific {@link ClusterRole} resource
     *
     * @param resource {@link ClusterRole} resource that will be updated
     */
    @Override
    public void update(ClusterRole resource) {
        client.resource(resource).update();
    }

    /**
     * Deletes {@link ClusterRole} resource from Namespace in current context
     *
     * @param resource {@link ClusterRole} resource that will be deleted
     */
    @Override
    public void delete(ClusterRole resource) {
        client.withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link ClusterRole} resource using {@link Consumer}
     * from which is the current {@link ClusterRole} resource updated
     *
     * @param resource {@link ClusterRole} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(ClusterRole resource, Consumer<ClusterRole> editor) {
        ClusterRole toBeUpdated = client.withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link ClusterRole} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(ClusterRole resource) {
        return resource != null;
    }

    /**
     * Waits for {@link ClusterRole} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(ClusterRole resource) {
        return resource == null;
    }
}
