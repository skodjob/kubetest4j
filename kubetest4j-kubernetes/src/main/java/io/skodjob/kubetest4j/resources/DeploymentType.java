/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.skodjob.kubetest4j.TestFrameConstants;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class DeploymentType implements ResourceType<Deployment> {

    private final MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> client;

    /**
     * Constructor
     */
    public DeploymentType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().apps().deployments();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "Deployment";
    }

    /**
     * Timeout for resource readiness
     *
     * @return timeout for resource readiness
     */
    @Override
    public Long getTimeoutForResourceReadiness() {
        return TestFrameConstants.GLOBAL_TIMEOUT;
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
     * Creates specific {@link Deployment} resource
     *
     * @param resource {@link Deployment} resource
     */
    @Override
    public void create(Deployment resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link Deployment} resource
     *
     * @param resource {@link Deployment} resource that will be updated
     */
    @Override
    public void update(Deployment resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link Deployment} resource from Namespace in current context
     *
     * @param resource {@link Deployment} resource that will be deleted
     */
    @Override
    public void delete(Deployment resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link Deployment} resource using {@link Consumer}
     * from which is the current {@link Deployment} resource updated
     *
     * @param resource {@link Deployment} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(Deployment resource, Consumer<Deployment> editor) {
        Deployment toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link Deployment} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(Deployment resource) {
        return client.resource(resource).isReady();
    }

    /**
     * Waits for {@link Deployment} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(Deployment resource) {
        return resource == null;
    }
}
