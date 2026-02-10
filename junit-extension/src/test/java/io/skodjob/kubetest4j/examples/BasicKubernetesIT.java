/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.skodjob.kubetest4j.annotations.RequiresKubernetes;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectCmdKubeClient;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Example test demonstrating basic usage of the Kubernetes test framework extension.
 * This test shows how to:
 * - Use @KubernetesTest annotation
 * - Inject KubeClient and KubeResourceManager
 * - Inject namespace information
 * - Create and manage resources
 */
@RequiresKubernetes
@KubernetesTest(
    namespaces = {"basic-test"},
    cleanup = CleanupStrategy.AUTOMATIC,
    namespaceLabels = {"test-type=basic", "framework=test-frame"},
    namespaceAnnotations = {"description=Basic test example"}
)
class BasicKubernetesIT {

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @InjectCmdKubeClient
    KubeCmdClient<?> kubeCmdClient;

    @Test
    void testBasicFunctionality() {
        // Verify injections work
        assertNotNull(client, "KubeClient should be injected");
        assertNotNull(resourceManager, "KubeResourceManager should be injected");
    }

    @Test
    void testResourceCreation(@InjectKubeClient KubeClient paramClient,
                              @InjectResourceManager KubeResourceManager paramResourceManager) {
        // Demonstrate parameter injection
        assertNotNull(paramClient, "Parameter injection should work for KubeClient");
        assertNotNull(paramResourceManager, "Parameter injection should work for KubeResourceManager");

        // Create a ConfigMap
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test-config")
            .withNamespace("basic-test")
            .endMetadata()
            .addToData("key1", "value1")
            .addToData("key2", "value2")
            .build();

        // Create and verify the resource
        resourceManager.createResourceWithWait(configMap);

        kubeCmdClient.exec("get", "ns");

        ConfigMap createdConfigMap = client.getClient().configMaps()
            .inNamespace("basic-test")
            .withName("test-config")
            .get();

        assertNotNull(createdConfigMap, "ConfigMap should be created");
        assertEquals("value1", createdConfigMap.getData().get("key1"));
        assertEquals("value2", createdConfigMap.getData().get("key2"));
    }

    @Test
    void testPodCreation() {
        // Create a simple pod
        Pod pod = new PodBuilder()
            .withNewMetadata()
            .withName("test-pod")
            .withNamespace("basic-test")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("test-container")
            .withImage("quay.io/prometheus/busybox")
            .withCommand("sleep", "300")
            .endContainer()
            .withRestartPolicy("Never")
            .endSpec()
            .build();

        resourceManager.createResourceWithWait(pod);

        Pod createdPod = client.getClient().pods()
            .inNamespace("basic-test")
            .withName("test-pod")
            .get();

        assertNotNull(createdPod, "Pod should be created");
        assertEquals("test-pod", createdPod.getMetadata().getName());
        assertEquals("basic-test", createdPod.getMetadata().getNamespace());
    }
}
