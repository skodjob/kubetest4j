/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class ValidatingWebhookConfigurationType implements ResourceType<ValidatingWebhookConfiguration> {


    private final NonNamespaceOperation<ValidatingWebhookConfiguration, ValidatingWebhookConfigurationList,
        Resource<ValidatingWebhookConfiguration>> client;

    /**
     * Constructor
     */
    public ValidatingWebhookConfigurationType() {
        this.client = KubeResourceManager.get().kubeClient().getClient()
            .admissionRegistration()
            .v1()
            .validatingWebhookConfigurations();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "ValidatingWebhookConfiguration";
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
     * Creates specific {@link ValidatingWebhookConfiguration} resource
     *
     * @param resource {@link ValidatingWebhookConfiguration} resource
     */
    @Override
    public void create(ValidatingWebhookConfiguration resource) {
        client.resource(resource).create();
    }

    /**
     * Updates specific {@link ValidatingWebhookConfiguration} resource
     *
     * @param resource {@link ValidatingWebhookConfiguration} resource that will be updated
     */
    @Override
    public void update(ValidatingWebhookConfiguration resource) {
        client.resource(resource).update();
    }

    /**
     * Deletes {@link ValidatingWebhookConfiguration} resource from Namespace in current context
     *
     * @param resource {@link ValidatingWebhookConfiguration} resource that will be deleted
     */
    @Override
    public void delete(ValidatingWebhookConfiguration resource) {
        client.withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link ValidatingWebhookConfiguration} resource using {@link Consumer}
     * from which is the current {@link ValidatingWebhookConfiguration} resource updated
     *
     * @param resource {@link ValidatingWebhookConfiguration} resource that will be replaced
     * @param editor   {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(ValidatingWebhookConfiguration resource, Consumer<ValidatingWebhookConfiguration> editor) {
        ValidatingWebhookConfiguration toBeReplaced = client.withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link ValidatingWebhookConfiguration} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(ValidatingWebhookConfiguration resource) {
        return resource != null;
    }

    /**
     * Waits for {@link ValidatingWebhookConfiguration} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(ValidatingWebhookConfiguration resource) {
        return resource == null;
    }
}
