/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for CronJob resource
 */
public class CronJobType implements ResourceType<CronJob> {

    private final MixedOperation<CronJob, CronJobList, Resource<CronJob>> client;

    /**
     * Constructor
     */
    public CronJobType() {
        this(KubeResourceManager.get().kubeClient().getClient().batch().v1().cronjobs());
    }

    /**
     * Constructor with client for testing
     *
     * @param client client
     */
    CronJobType(MixedOperation<CronJob, CronJobList, Resource<CronJob>> client) {
        this.client = client;
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "CronJob";
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
     * Creates specific {@link CronJob} resource
     *
     * @param resource {@link CronJob} resource
     */
    @Override
    public void create(CronJob resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link CronJob} resource
     *
     * @param resource {@link CronJob} resource that will be updated
     */
    @Override
    public void update(CronJob resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link CronJob} resource from Namespace in current context
     *
     * @param resource {@link CronJob} resource that will be deleted
     */
    @Override
    public void delete(CronJob resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link CronJob} resource using {@link Consumer}
     * from which is the current {@link CronJob} resource updated
     *
     * @param resource {@link CronJob} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(CronJob resource, Consumer<CronJob> editor) {
        CronJob toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link CronJob} to be ready (created/running)
     *
     * CronJob readiness is not tracked by Kubernetes; this always returns {@code true}.
     *
     * @param resource resource
     * @return {@code true}
     */
    @Override
    public boolean isReady(CronJob resource) {
        return resource != null;
    }

    /**
     * Waits for {@link CronJob} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(CronJob resource) {
        return resource == null;
    }
}
