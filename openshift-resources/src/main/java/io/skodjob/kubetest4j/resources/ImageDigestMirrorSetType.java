/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.config.v1.ImageDigestMirrorSet;
import io.fabric8.openshift.api.model.config.v1.ImageDigestMirrorSetList;
import io.skodjob.kubetest4j.interfaces.ResourceType;

import java.util.function.Consumer;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class ImageDigestMirrorSetType implements ResourceType<ImageDigestMirrorSet> {

    private final NonNamespaceOperation<ImageDigestMirrorSet, ImageDigestMirrorSetList,
        Resource<ImageDigestMirrorSet>> client;

    /**
     * Constructor
     */
    public ImageDigestMirrorSetType() {
        this.client = KubeResourceManager.get().kubeClient().getOpenShiftClient().config().imageDigestMirrorSets();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "ImageDigestMirrorSet";
    }

    /**
     * Get specific client for resource
     *
     * @return specific client
     */
    @Override
    public NonNamespaceOperation<?, ?, ?> getClient() {
        return client;
    }

    /**
     * Creates specific {@link ImageDigestMirrorSet} resource
     *
     * @param resource {@link ImageDigestMirrorSet} resource
     */
    @Override
    public void create(ImageDigestMirrorSet resource) {
        client.resource(resource).create();
    }

    /**
     * Updates specific {@link ImageDigestMirrorSet} resource
     *
     * @param resource {@link ImageDigestMirrorSet} resource that will be updated
     */
    @Override
    public void update(ImageDigestMirrorSet resource) {
        client.resource(resource).update();
    }

    /**
     * Deletes {@link ImageDigestMirrorSet} resource from Namespace in current context
     *
     * @param resource {@link ImageDigestMirrorSet} resource that will be deleted
     */
    @Override
    public void delete(ImageDigestMirrorSet resource) {
        client.withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link ImageDigestMirrorSet} resource using {@link Consumer}
     * from which is the current {@link ImageDigestMirrorSet} resource updated
     *
     * @param resource {@link ImageDigestMirrorSet} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(ImageDigestMirrorSet resource, Consumer<ImageDigestMirrorSet> editor) {
        ImageDigestMirrorSet toBeReplaced = client.withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link ImageDigestMirrorSet} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(ImageDigestMirrorSet resource) {
        return resource != null;
    }

    /**
     * Waits for {@link ImageDigestMirrorSet} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(ImageDigestMirrorSet resource) {
        return resource == null;
    }
}
