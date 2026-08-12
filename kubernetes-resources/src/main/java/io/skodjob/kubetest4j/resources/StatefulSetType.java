/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for StatefulSet resource
 */
public class StatefulSetType implements ResourceType<StatefulSet> {

    private final MixedOperation<StatefulSet, StatefulSetList, RollableScalableResource<StatefulSet>> client;

    /**
     * Constructor
     */
    public StatefulSetType() {
        this(KubeResourceManager.get().kubeClient().getClient().apps().statefulSets());
    }

    /**
     * Constructor with client for testing
     *
     * @param client client
     */
    StatefulSetType(MixedOperation<StatefulSet, StatefulSetList,
            RollableScalableResource<StatefulSet>> client) {
        this.client = client;
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "StatefulSet";
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
     * Creates specific {@link StatefulSet} resource
     *
     * @param resource {@link StatefulSet} resource
     */
    @Override
    public void create(StatefulSet resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link StatefulSet} resource
     *
     * @param resource {@link StatefulSet} resource that will be updated
     */
    @Override
    public void update(StatefulSet resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link StatefulSet} resource from Namespace in current context
     *
     * @param resource {@link StatefulSet} resource that will be deleted
     */
    @Override
    public void delete(StatefulSet resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link StatefulSet} resource using {@link Consumer}
     * from which is the current {@link StatefulSet} resource updated
     *
     * @param resource {@link StatefulSet} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(StatefulSet resource, Consumer<StatefulSet> editor) {
        StatefulSet toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link StatefulSet} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(StatefulSet resource) {
        return client.resource(resource).isReady();
    }

    /**
     * Waits for {@link StatefulSet} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(StatefulSet resource) {
        return resource == null;
    }
}
