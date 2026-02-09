/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class CustomResourceDefinitionType implements ResourceType<CustomResourceDefinition> {

    private final NonNamespaceOperation<CustomResourceDefinition, CustomResourceDefinitionList,
        Resource<CustomResourceDefinition>> client;

    /**
     * Constructor
     */
    public CustomResourceDefinitionType() {
        this.client = KubeResourceManager.get().kubeClient()
            .getClient().apiextensions().v1().customResourceDefinitions();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "CustomResourceDefinition";
    }

    /**
     * Get specific client for resoruce
     *
     * @return specific client
     */
    @Override
    public NonNamespaceOperation<?, ?, ?> getClient() {
        return client;
    }

    /**
     * Creates specific {@link CustomResourceDefinition} resource
     *
     * @param resource {@link CustomResourceDefinition} resource
     */
    @Override
    public void create(CustomResourceDefinition resource) {
        client.resource(resource).create();
    }

    /**
     * Updates specific {@link CustomResourceDefinition} resource
     *
     * @param resource {@link CustomResourceDefinition} resource that will be updated
     */
    @Override
    public void update(CustomResourceDefinition resource) {
        client.resource(resource).update();
    }

    /**
     * Deletes {@link CustomResourceDefinition} resource from Namespace in current context
     *
     * @param resource {@link CustomResourceDefinition} resource that will be deleted
     */
    @Override
    public void delete(CustomResourceDefinition resource) {
        client.withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link CustomResourceDefinition} resource using {@link Consumer}
     * from which is the current {@link CustomResourceDefinition} resource updated
     *
     * @param resource {@link CustomResourceDefinition} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(CustomResourceDefinition resource, Consumer<CustomResourceDefinition> editor) {
        CustomResourceDefinition toBeUpdated = client.withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    /**
     * Waits for {@link CustomResourceDefinition} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(CustomResourceDefinition resource) {
        return resource != null;
    }

    /**
     * Waits for {@link CustomResourceDefinition} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(CustomResourceDefinition resource) {
        return resource == null;
    }
}
