/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigList;
import io.fabric8.openshift.client.dsl.BuildConfigResource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

import java.util.function.Consumer;

/**
 * Implementation of ResourceType for handling the {@link BuildConfig} resource.
 */
public class BuildConfigType implements ResourceType<BuildConfig> {

    private final MixedOperation<BuildConfig, BuildConfigList, BuildConfigResource<BuildConfig, Void, Build>> client;

    /**
     * {@link BuildConfig} constructor for initializing the client.
     */
    public BuildConfigType() {
        this.client = KubeResourceManager.get().kubeClient().getOpenShiftClient().buildConfigs();
    }

    /**
     * Returns {@link BuildConfig} client.
     *
     * @return  client for BuildConfigs
     */
    @Override
    public NonNamespaceOperation<?, ?, ?> getClient() {
        return client;
    }

    /**
     * Returns kind of the resource - in this case {@link BuildConfig}
     *
     * @return  kind of resource
     */
    @Override
    public String getKind() {
        return "BuildConfig";
    }

    /**
     * Creates specific {@link BuildConfig} resource
     *
     * @param resource {@link BuildConfig} resource
     */
    @Override
    public void create(BuildConfig resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link BuildConfig} resource
     *
     * @param resource {@link BuildConfig} resource that will be updated
     */
    @Override
    public void update(BuildConfig resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link BuildConfig} resource from Namespace in current context
     *
     * @param resource {@link BuildConfig} resource that will be deleted
     */
    @Override
    public void delete(BuildConfig resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).delete();
    }

    /**
     * Replaces {@link BuildConfig} resource using {@link Consumer}
     * from which is the current {@link BuildConfig} resource updated
     *
     * @param resource  {@link BuildConfig} resource that will be replaced
     * @param editor    {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(BuildConfig resource, Consumer<BuildConfig> editor) {
        BuildConfig toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link BuildConfig} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(BuildConfig resource) {
        return client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).isReady();
    }

    /**
     * Waits for {@link BuildConfig} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(BuildConfig resource) {
        return resource == null;
    }
}
