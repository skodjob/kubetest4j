/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSetList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for DaemonSet resource
 */
public class DaemonSetType implements ResourceType<DaemonSet> {

    private final MixedOperation<DaemonSet, DaemonSetList, Resource<DaemonSet>> client;

    /**
     * Constructor
     */
    public DaemonSetType() {
        this(KubeResourceManager.get().kubeClient().getClient().apps().daemonSets());
    }

    /**
     * Constructor with client for testing
     *
     * @param client client
     */
    DaemonSetType(MixedOperation<DaemonSet, DaemonSetList, Resource<DaemonSet>> client) {
        this.client = client;
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "DaemonSet";
    }

    /**
     * Timeout for resource readiness
     *
     * @return timeout for resource readiness
     */
    @Override
    public Long getTimeoutForResourceReadiness() {
        return KubeTestConstants.GLOBAL_TIMEOUT;
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
     * Creates specific {@link DaemonSet} resource
     *
     * @param resource {@link DaemonSet} resource
     */
    @Override
    public void create(DaemonSet resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link DaemonSet} resource
     *
     * @param resource {@link DaemonSet} resource that will be updated
     */
    @Override
    public void update(DaemonSet resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link DaemonSet} resource from Namespace in current context
     *
     * @param resource {@link DaemonSet} resource that will be deleted
     */
    @Override
    public void delete(DaemonSet resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link DaemonSet} resource using {@link Consumer}
     * from which is the current {@link DaemonSet} resource updated
     *
     * @param resource {@link DaemonSet} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(DaemonSet resource, Consumer<DaemonSet> editor) {
        DaemonSet toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link DaemonSet} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(DaemonSet resource) {
        return client.resource(resource).isReady();
    }

    /**
     * Waits for {@link DaemonSet} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(DaemonSet resource) {
        return resource == null;
    }
}
