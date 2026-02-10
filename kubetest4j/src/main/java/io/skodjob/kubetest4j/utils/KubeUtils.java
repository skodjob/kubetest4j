/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.InstallPlan;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.InstallPlanBuilder;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.wait.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for Kubernetes and Openshift clusters.
 */
public final class KubeUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubeUtils.class);

    private KubeUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Approve install plan in namespace
     *
     * @param namespaceName   namespace
     * @param installPlanName install plan name
     */
    public static void approveInstallPlan(String namespaceName, String installPlanName) {
        LOGGER.debug("Approving InstallPlan {}", installPlanName);
        Wait.until("InstallPlan approval", KubeTestConstants.GLOBAL_POLL_INTERVAL_SHORT, 15_000, () -> {
            try {
                InstallPlan installPlan =
                    new InstallPlanBuilder(KubeResourceManager.get().kubeClient()
                        .getOpenShiftClient().operatorHub().installPlans()
                        .inNamespace(namespaceName).withName(installPlanName).get())
                        .editSpec()
                        .withApproved()
                        .endSpec()
                        .build();

                KubeResourceManager.get().kubeClient().getOpenShiftClient().operatorHub().installPlans()
                    .inNamespace(namespaceName).withName(installPlanName).patch(installPlan);
                return true;
            } catch (Exception ex) {
                LOGGER.error(String.valueOf(ex));
                return false;
            }
        });
    }

    /**
     * Read not approved install-plans with prefix
     *
     * @param namespaceName namespace
     * @param csvPrefix     prefix of install plans
     * @return list of not approved install-plans
     */
    public static InstallPlan getNonApprovedInstallPlan(String namespaceName, String csvPrefix) {
        return KubeResourceManager.get().kubeClient().getOpenShiftClient().operatorHub().installPlans()
            .inNamespace(namespaceName).list().getItems().stream()
            .filter(installPlan -> !installPlan.getSpec().getApproved()
                && installPlan.getSpec().getClusterServiceVersionNames().toString().contains(csvPrefix))
            .findFirst().orElse(null);
    }

    /**
     * Apply label to namespace and wait for propagation
     *
     * @param namespace namespace name
     * @param key       label key
     * @param value     label value
     */
    public static void labelNamespace(String namespace, String key, String value) {
        if (KubeResourceManager.get().kubeClient().namespaceExists(namespace)) {
            Wait.until(String.format("Namespace %s has label: %s", namespace, key),
                KubeTestConstants.GLOBAL_POLL_INTERVAL_1_SEC, KubeTestConstants.GLOBAL_STABILITY_TIME, () -> {
                    try {
                        KubeResourceManager.get().kubeClient().getClient().namespaces().withName(namespace).edit(n ->
                            new NamespaceBuilder(n)
                                .editMetadata()
                                .addToLabels(key, value)
                                .endMetadata()
                                .build());
                    } catch (Exception ex) {
                        return false;
                    }
                    Namespace n = KubeResourceManager.get().kubeClient()
                        .getClient().namespaces().withName(namespace).get();
                    if (n != null) {
                        return n.getMetadata().getLabels().get(key) != null;
                    }
                    return false;
                });
        }
    }

    /**
     * Is current cluster openshift
     *
     * @return true if cluster is openshift
     */
    public static boolean isOcp() {
        return KubeResourceManager.get().kubeCmdClient()
            .exec(false, false, "api-versions").out().contains("openshift.io");
    }

    /**
     * Is multinode cluster
     *
     * @return true if cluster is multinode
     */
    public static boolean isMultinode() {
        return KubeResourceManager.get().kubeClient().getClient().nodes().list().getItems().size() > 1;
    }
}
