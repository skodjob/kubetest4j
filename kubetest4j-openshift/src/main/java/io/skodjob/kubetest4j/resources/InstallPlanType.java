/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.InstallPlan;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.InstallPlanList;
import io.skodjob.kubetest4j.interfaces.ResourceType;

import java.util.function.Consumer;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class InstallPlanType implements ResourceType<InstallPlan> {

    private final MixedOperation<InstallPlan, InstallPlanList, Resource<InstallPlan>> client;

    /**
     * Constructor
     */
    public InstallPlanType() {
        this.client = KubeResourceManager.get().kubeClient().getOpenShiftClient().operatorHub().installPlans();
    }

    /**
     * Kind of api resource
     *
     * @return kind name
     */
    @Override
    public String getKind() {
        return "InstallPlan";
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
     * Creates specific {@link InstallPlan} resource
     *
     * @param resource {@link InstallPlan} resource
     */
    @Override
    public void create(InstallPlan resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).create();
    }

    /**
     * Updates specific {@link InstallPlan} resource
     *
     * @param resource {@link InstallPlan} resource that will be updated
     */
    @Override
    public void update(InstallPlan resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).resource(resource).update();
    }

    /**
     * Deletes {@link InstallPlan} resource from Namespace in current context
     *
     * @param resource {@link InstallPlan} resource that will be deleted
     */
    @Override
    public void delete(InstallPlan resource) {
        client.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    /**
     * Replaces {@link InstallPlan} resource using {@link Consumer}
     * from which is the current {@link InstallPlan} resource updated
     *
     * @param resource {@link InstallPlan} resource that will be replaced
     * @param editor       {@link Consumer} containing updates to the resource
     */
    @Override
    public void replace(InstallPlan resource, Consumer<InstallPlan> editor) {
        InstallPlan toBeReplaced = client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeReplaced);
        update(toBeReplaced);
    }

    /**
     * Waits for {@link InstallPlan} to be ready (created/running)
     *
     * @param resource resource
     * @return result of the readiness check
     */
    @Override
    public boolean isReady(InstallPlan resource) {
        return resource != null;
    }

    /**
     * Waits for {@link InstallPlan} to be deleted
     *
     * @param resource resource
     * @return result of the deletion
     */
    @Override
    public boolean isDeleted(InstallPlan resource) {
        return resource == null;
    }
}
