/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class RoleType implements ResourceType<Role> {

    private final MixedOperation<Role, RoleList, Resource<Role>> client;

    /**
     * Constructor
     */
    public RoleType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().rbac().roles();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "Role";
    }

    /**
     * Creates specific {@link Role} resource
     *
     * @param resource {@link Role} resource
     */
    @Override
    public void create(Role resource) {
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
     * Updates specific {@link Role} resource
     *
     * @param resource {@link Role} resource that will be updated
     */
    @Override
    public void update(Role resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link Role} resource from Namespace in current context
     *
     * @param resource {@link Role} resource that will be deleted
     */
    @Override
    public void delete(Role resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link Role} resource using {@link Consumer}
     * from which is the current {@link Role} resource updated
     *
     * @param resource {@link Role} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(Role resource, Consumer<Role> editor) {
        Role toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link Role} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(Role resource) {
        return resource != null;
    }

    /**
     * Waits for {@link Role} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(Role resource) {
        return resource == null;
    }
}
