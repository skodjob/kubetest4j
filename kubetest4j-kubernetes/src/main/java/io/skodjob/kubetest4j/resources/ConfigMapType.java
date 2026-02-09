/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class ConfigMapType implements ResourceType<ConfigMap> {
    private final MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> client;

    /**
     * Constructor
     */
    public ConfigMapType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().configMaps();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "ConfigMap";
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
     * Creates specific {@link ConfigMap} resource
     *
     * @param resource {@link ConfigMap} resource
     */
    @Override
    public void create(ConfigMap resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link ConfigMap} resource
     *
     * @param resource {@link ConfigMap} updated resource
     */
    @Override
    public void update(ConfigMap resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Replaces {@link ConfigMap} resource using {@link Consumer}
     * from which is the current {@link ConfigMap} resource updated
     *
     * @param resource {@link ConfigMap} replaced resource
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(ConfigMap resource, Consumer<ConfigMap> editor) {
        ConfigMap toBeUpdated = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Deletes {@link ConfigMap} resource
     *
     * @param resource {@link ConfigMap} deleted resource
     */
    @Override
    public void delete(ConfigMap resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Waits for {@link ConfigMap} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(ConfigMap resource) {
        return resource != null;
    }

    /**
     * Waits for {@link ConfigMap} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(ConfigMap resource) {
        return resource == null;
    }
}
