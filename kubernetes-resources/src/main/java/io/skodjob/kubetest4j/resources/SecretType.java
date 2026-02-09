/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class SecretType implements ResourceType<Secret> {

    private final MixedOperation<Secret, SecretList, Resource<Secret>> client;

    /**
     * Constructor
     */
    public SecretType() {
        this.client = KubeResourceManager.get().kubeClient().getClient().secrets();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "Secret";
    }

    /**
     * Creates specific {@link Secret} resource
     *
     * @param resource {@link Secret} resource
     */
    @Override
    public void create(Secret resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
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
     * Updates specific {@link Secret} resource
     *
     * @param resource {@link Secret} resource that will be updated
     */
    @Override
    public void update(Secret resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link Secret} resource from Namespace in current context
     *
     * @param resource {@link Secret} resource that will be deleted
     */
    @Override
    public void delete(Secret resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * @param resource name
     * @param editor   modifier
     */
    @Override
    public void replace(Secret resource, Consumer<Secret> editor) {
        Secret toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for resource to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(Secret resource) {
        return resource != null;
    }

    /**
     * Waits for resource to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(Secret resource) {
        return resource == null;
    }
}
