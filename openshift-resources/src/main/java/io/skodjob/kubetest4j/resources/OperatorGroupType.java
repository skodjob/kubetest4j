/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroupList;
import io.skodjob.kubetest4j.interfaces.ResourceType;

import java.util.function.Consumer;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class OperatorGroupType implements ResourceType<OperatorGroup> {

    private final MixedOperation<OperatorGroup, OperatorGroupList, Resource<OperatorGroup>> client;

    /**
     * Constructor
     */
    public OperatorGroupType() {
        this.client = KubeResourceManager.get().kubeClient().getOpenShiftClient().operatorHub().operatorGroups();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "OperatorGroup";
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
     * Creates specific {@link OperatorGroup} resource
     *
     * @param resource {@link OperatorGroup} resource
     */
    @Override
    public void create(OperatorGroup resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link OperatorGroup} resource
     *
     * @param resource {@link OperatorGroup} resource that will be updated
     */
    @Override
    public void update(OperatorGroup resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link OperatorGroup} resource from Namespace in current context
     *
     * @param resource {@link OperatorGroup} resource that will be deleted
     */
    @Override
    public void delete(OperatorGroup resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link OperatorGroup} resource using {@link Consumer}
     * from which is the current {@link OperatorGroup} resource updated
     *
     * @param resource {@link OperatorGroup} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(OperatorGroup resource, Consumer<OperatorGroup> editor) {
        OperatorGroup toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link OperatorGroup} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(OperatorGroup resource) {
        return resource != null;
    }

    /**
     * Waits for {@link OperatorGroup} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(OperatorGroup resource) {
        return resource == null;
    }
}
