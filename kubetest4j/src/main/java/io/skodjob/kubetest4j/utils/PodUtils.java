/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.wait.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents utils class for pod
 */
public final class PodUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PodUtils.class);
    private static final long READINESS_TIMEOUT = Duration.ofMinutes(10).toMillis();

    private PodUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Wait for all pods in namespace to be ready
     *
     * @param namespaceName   name of the namespace
     * @param containersReady flag wait for all containers
     * @param onTimeout       callback on timeout
     */
    public static void waitForPodsReady(String namespaceName, boolean containersReady, Runnable onTimeout) {
        Wait.until("readiness of all Pods in namespace " + namespaceName,
            KubeTestConstants.GLOBAL_POLL_INTERVAL_MEDIUM, READINESS_TIMEOUT,
            () -> {
                List<Pod> pods = KubeResourceManager.get().kubeClient().getClient()
                    .pods().inNamespace(namespaceName).list().getItems();
                if (pods.isEmpty()) {
                    LOGGER.debug("There are no existing Pods in Namespace {}", namespaceName);
                    return false;
                }
                for (Pod pod : pods) {
                    if (!(Readiness.isPodReady(pod) || Readiness.isPodSucceeded(pod))) {
                        LOGGER.debug("There is not ready Pod {}/{}", namespaceName, pod.getMetadata().getName());
                        return false;
                    } else {
                        if (containersReady) {
                            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                                if (!(Boolean.TRUE.equals(cs.getReady())
                                    || cs.getState().getTerminated().getReason().equals("Completed"))) {
                                    LOGGER.debug("Container {} of Pod {}/{} is not ready",
                                        cs.getName(), namespaceName, pod.getMetadata().getName());
                                    return false;
                                }
                            }
                        }
                    }
                }
                LOGGER.info("All Pods in Namespace {} are ready", namespaceName);
                return true;
            }, onTimeout);
    }

    /**
     * Wait for pods selected by label selector in namespace to be ready
     *
     * @param namespaceName   namespace
     * @param selector        label selector of the pods
     * @param expectPodsCount expected pods count
     * @param containers      flag wait for all containers
     * @param onTimeout       callback on timeout
     */
    public static void waitForPodsReady(String namespaceName, LabelSelector selector, int expectPodsCount,
                                        boolean containers, Runnable onTimeout) {
        Wait.until("readiness of all Pods matching " + selector + " in Namespace " + namespaceName,
            KubeTestConstants.GLOBAL_POLL_INTERVAL_MEDIUM, READINESS_TIMEOUT,
            () -> {
                List<Pod> pods = KubeResourceManager.get().kubeClient().getClient().pods()
                    .inNamespace(namespaceName).withLabelSelector(selector).list().getItems();
                if (pods.isEmpty() && expectPodsCount == 0) {
                    LOGGER.debug("All expected Pods {} in Namespace {} are ready", selector, namespaceName);
                    return true;
                }
                if (pods.isEmpty()) {
                    LOGGER.debug("Pods matching {}/{} are not ready", namespaceName, selector);
                    return false;
                }
                if (pods.size() != expectPodsCount) {
                    LOGGER.debug("Expected Pods {}/{} are not ready", namespaceName, selector);
                    return false;
                }
                for (Pod pod : pods) {
                    if (!(Readiness.isPodReady(pod) || Readiness.isPodSucceeded(pod))) {
                        LOGGER.debug("Pod is not ready: {}/{}", namespaceName, pod.getMetadata().getName());
                        return false;
                    } else {
                        if (containers) {
                            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                                if (!(Boolean.TRUE.equals(cs.getReady())
                                    || cs.getState().getTerminated().getReason().equals("Completed"))) {
                                    LOGGER.debug("Container {} of Pod {}/{} not ready",
                                        cs.getName(), namespaceName, pod.getMetadata().getName());
                                    return false;
                                }
                            }
                        }
                    }
                }
                LOGGER.info("Pods matching {}/{} are ready", namespaceName, selector);
                return true;
            }, onTimeout);
    }

    /**
     * Wait for pod ready, if not ready for timeout try to restart and check again
     *
     * @param namespaceName     namespace
     * @param selector          label selector
     * @param expectedPodsCount expected pods count
     * @param containersReady   flag wait for containers
     */
    public static void waitForPodsReadyWithRestart(String namespaceName, LabelSelector selector,
                                                   int expectedPodsCount, boolean containersReady) {
        try {
            waitForPodsReady(namespaceName, selector, expectedPodsCount, containersReady, () -> {
            });
        } catch (Exception ex) {
            LOGGER.warn("Pods {}/{} are not ready. Going to restart them", namespaceName, selector);
            KubeResourceManager.get().kubeClient().getClient().pods()
                .inNamespace(namespaceName).withLabelSelector(selector).list().getItems().forEach(p ->
                    KubeResourceManager.get().kubeClient().getClient().resource(p).delete());
            waitForPodsReady(namespaceName, selector, expectedPodsCount, containersReady, () -> {
            });
        }
    }

    /**
     * Returns a map of resource name to resource version for all the pods in the given {@code namespace}
     * matching the given {@code selector}
     *
     * @param namespaceName namesapce
     * @param selector      label selector
     * @return key value map podName -> uid
     */
    public static Map<String, String> podSnapshot(String namespaceName, LabelSelector selector) {
        List<Pod> pods = KubeResourceManager.get().kubeClient().getClient().pods()
            .inNamespace(namespaceName).withLabelSelector(selector).list().getItems();
        return pods.stream()
            .collect(
                Collectors.toMap(pod -> pod.getMetadata().getName(),
                    pod -> pod.getMetadata().getUid()));
    }

    /**
     * Verify if the pod is stable after it is in ready state.
     *
     * @param namespaceName namespace
     * @param selector      label selector
     */
    public static void verifyThatPodsAreStable(String namespaceName, LabelSelector selector) {
        int[] stabilityCounter = {0};
        String phase = "Running";

        Wait.until(String.format("Pods in Namespace '%s' with LabelSelector %s stability in phase %s",
                namespaceName, selector, phase),
            KubeTestConstants.GLOBAL_POLL_INTERVAL_SHORT, KubeTestConstants.GLOBAL_TIMEOUT,
            () -> {
                List<Pod> existingPod = KubeResourceManager.get().kubeClient().getClient().pods()
                    .inNamespace(namespaceName).withLabelSelector(selector).list().getItems();
                LOGGER.debug("Considering the following Pods {}", existingPod.stream()
                    .map(p -> p.getMetadata().getName()).toList());

                for (Pod pod : existingPod) {
                    if (pod == null) {
                        continue;
                    }
                    if (pod.getStatus().getPhase().equals(phase)) {
                        LOGGER.debug("Pod {}/{} is in the {} state. " +
                                "Remaining milliseconds for Pod to be stable {}",
                            namespaceName,
                            pod.getMetadata().getName(),
                            pod.getStatus().getPhase(),
                            KubeTestConstants.GLOBAL_STABILITY_TIME -
                                (KubeTestConstants.GLOBAL_POLL_INTERVAL_SHORT * stabilityCounter[0])
                        );
                    } else {
                        LOGGER.warn("Pod {}/{} is not stable in phase following phase {} ({})" +
                                " reset the stability counter from {}ms to {}ms",
                            namespaceName, pod.getMetadata().getName(), pod.getStatus().getPhase(),
                            phase, stabilityCounter[0], 0);
                        stabilityCounter[0] = 0;
                        return false;
                    }
                }
                stabilityCounter[0]++;

                if (stabilityCounter[0] == KubeTestConstants.GLOBAL_STABILITY_TIME /
                    (KubeTestConstants.GLOBAL_POLL_INTERVAL_SHORT)) {
                    LOGGER.info("All Pods {}/{} are stable", namespaceName, existingPod.stream()
                        .map(p -> p.getMetadata().getName()).collect(Collectors.joining(" ,")));
                    return true;
                }
                return false;
            });
    }
}
