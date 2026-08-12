/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for PersistentVolumeClaim resource
 */
public class PersistentVolumeClaimType implements ResourceType<PersistentVolumeClaim> {

    private final MixedOperation<PersistentVolumeClaim, PersistentVolumeClaimList,
        Resource<PersistentVolumeClaim>> client;

    /**
     * Constructor
     */
    public PersistentVolumeClaimType() {
        this(KubeResourceManager.get().kubeClient().getClient().persistentVolumeClaims());
    }

    /**
     * Constructor with client for testing
     *
     * @param client client
     */
    PersistentVolumeClaimType(MixedOperation<PersistentVolumeClaim, PersistentVolumeClaimList,
            Resource<PersistentVolumeClaim>> client) {
        this.client = client;
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "PersistentVolumeClaim";
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
     * Creates specific {@link PersistentVolumeClaim} resource
     *
     * @param resource {@link PersistentVolumeClaim} resource
     */
    @Override
    public void create(PersistentVolumeClaim resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link PersistentVolumeClaim} resource
     *
     * @param resource {@link PersistentVolumeClaim} resource that will be updated
     */
    @Override
    public void update(PersistentVolumeClaim resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link PersistentVolumeClaim} resource from Namespace in current context
     *
     * @param resource {@link PersistentVolumeClaim} resource that will be deleted
     */
    @Override
    public void delete(PersistentVolumeClaim resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link PersistentVolumeClaim} resource using {@link Consumer}
     * from which is the current {@link PersistentVolumeClaim} resource updated
     *
     * @param resource {@link PersistentVolumeClaim} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(PersistentVolumeClaim resource, Consumer<PersistentVolumeClaim> editor) {
        PersistentVolumeClaim toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link PersistentVolumeClaim} to be ready (phase == "Bound")
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(PersistentVolumeClaim resource) {
        return resource != null
            && resource.getStatus() != null
            && "Bound".equals(resource.getStatus().getPhase());
    }

    /**
     * Waits for {@link PersistentVolumeClaim} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(PersistentVolumeClaim resource) {
        return resource == null;
    }
}
