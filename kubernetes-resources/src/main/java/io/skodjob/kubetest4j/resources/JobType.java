/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class JobType implements ResourceType<Job> {

    private final MixedOperation<Job, JobList, ScalableResource<Job>> client;

    /**
     * Constructor
     */
    public JobType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().batch().v1().jobs();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "Job";
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
     * Creates specific {@link Job} resource
     *
     * @param resource {@link Job} resource
     */
    @Override
    public void create(Job resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link Job} resource
     *
     * @param resource {@link Job} resource that will be updated
     */
    @Override
    public void update(Job resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link Job} resource from Namespace in current context
     *
     * @param resource {@link Job} resource that will be deleted
     */
    @Override
    public void delete(Job resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link Job} resource using {@link Consumer}
     * from which is the current {@link Job} resource updated
     *
     * @param resource {@link Job} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(Job resource, Consumer<Job> editor) {
        Job toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link Job} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(Job resource) {
        return client.resource(resource).isReady();
    }

    /**
     * Waits for {@link Job} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(Job resource) {
        return resource == null;
    }
}
