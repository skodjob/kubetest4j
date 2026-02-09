/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamList;
import io.skodjob.kubetest4j.interfaces.ResourceType;

import java.util.function.Consumer;

/**
 * Implementation of ResourceType for handling the {@link ImageStream} resource.
 */
public class ImageStreamType implements ResourceType<ImageStream> {

    private final MixedOperation<ImageStream, ImageStreamList, Resource<ImageStream>> client;

    /**
     * {@link ImageStream} constructor for initializing the client.
     */
    public ImageStreamType() {
        this.client = KubeResourceManager.get().kubeClient().getOpenShiftClient().imageStreams();
    }

    /**
     * Returns {@link ImageStream} client.
     *
     * @return  client for ImageStreams
     */
    @Override
    public NonNamespaceOperation<?, ?, ?> getClient() {
        return client;
    }

    /**
     * Returns kind of the resource - in this case {@link ImageStream}
     *
     * @return  kind of resource
     */
    @Override
    public String getKind() {
        return "ImageStream";
    }

    /**
     * Creates specific {@link ImageStream} resource
     *
     * @param resource {@link ImageStream} resource
     */
    @Override
    public void create(ImageStream resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link ImageStream} resource
     *
     * @param resource {@link ImageStream} resource that will be updated
     */
    @Override
    public void update(ImageStream resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link ImageStream} resource from Namespace in current context
     *
     * @param resource {@link ImageStream} resource that will be deleted
     */
    @Override
    public void delete(ImageStream resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).delete();
    }

    /**
     * Replaces {@link ImageStream} resource using {@link Consumer}
     * from which is the current {@link ImageStream} resource updated
     *
     * @param resource  {@link ImageStream} resource that will be replaced
     * @param editor    {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(ImageStream resource, Consumer<ImageStream> editor) {
        ImageStream toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link ImageStream} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(ImageStream resource) {
        return client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).isReady();
    }

    /**
     * Waits for {@link ImageStream} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(ImageStream resource) {
        return resource == null;
    }
}
