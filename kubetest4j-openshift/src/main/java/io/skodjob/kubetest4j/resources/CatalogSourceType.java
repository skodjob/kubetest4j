/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSourceList;
import io.skodjob.kubetest4j.interfaces.ResourceType;

import java.util.function.Consumer;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class CatalogSourceType implements ResourceType<CatalogSource> {

    private final MixedOperation<CatalogSource, CatalogSourceList, Resource<CatalogSource>> client;

    /**
     * Constructor
     */
    public CatalogSourceType() {
        this.client = KubeResourceManager.get().kubeClient().getOpenShiftClient().operatorHub().catalogSources();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "CatalogSource";
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
     * Creates specific {@link CatalogSource} resource
     *
     * @param resource {@link CatalogSource} resource
     */
    @Override
    public void create(CatalogSource resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link CatalogSource} resource
     *
     * @param resource {@link CatalogSource} resource that will be updated
     */
    @Override
    public void update(CatalogSource resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link CatalogSource} resource from Namespace in current context
     *
     * @param resource {@link CatalogSource} resource that will be deleted
     */
    @Override
    public void delete(CatalogSource resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link CatalogSource} resource using {@link Consumer}
     * from which is the current {@link CatalogSource} resource updated
     *
     * @param resource {@link CatalogSource} resource that will be replaced
     * @param editor       {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(CatalogSource resource, Consumer<CatalogSource> editor) {
        CatalogSource toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link CatalogSource} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(CatalogSource resource) {
        return resource != null;
    }

    /**
     * Waits for {@link CatalogSource} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(CatalogSource resource) {
        return resource == null;
    }
}
