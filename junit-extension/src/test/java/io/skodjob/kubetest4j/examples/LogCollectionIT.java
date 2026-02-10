/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.skodjob.kubetest4j.annotations.RequiresKubernetes;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example test demonstrating integrated log collection with @KubernetesTest.
 * Shows how to configure automatic log collection when tests fail or complete.
 *
 * Note: This uses AFTER_EACH strategy for demonstration, but the framework
 * also provides comprehensive failure detection via exception handlers that
 * catch failures from ANY test lifecycle phase (beforeAll, beforeEach, etc.).
 */
@RequiresKubernetes
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@KubernetesTest(
    namespaces = {"log-collection-test"},
    cleanup = CleanupStrategy.AUTOMATIC,

    // ===== Log Collection Configuration =====
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.AFTER_EACH,
    collectPreviousLogs = true,
    collectNamespacedResources = {"pods", "services", "configmaps", "secrets", "deployments"},
    collectClusterWideResources = {"nodes"}
)
class LogCollectionIT {

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    @Order(1)
    void testSuccessfulOperation() {
        // Create a simple ConfigMap - this should succeed
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test-config")
            .withNamespace("log-collection-test")
            .endMetadata()
            .addToData("key", "value")
            .build();

        resourceManager.createResourceWithWait(configMap);

        // Verify it was created
        ConfigMap retrieved = client.getClient().configMaps()
            .inNamespace("log-collection-test")
            .withName("test-config")
            .get();

        assertNotNull(retrieved);
    }

    @Test
    @Order(2)
    void testCheckLogs() {
        assertTrue(Files.exists(Paths.get(System.getProperty("user.dir"),
            "/target/test-logs/io.skodjob.kubetest4j.examples.LogCollectionIT/" +
                "LogCollectionIT/primary/cluster-wide-resources/nodes")));
        assertTrue(Files.exists(Paths.get(System.getProperty("user.dir"),
            "/target/test-logs/io.skodjob.kubetest4j.examples.LogCollectionIT/" +
                "LogCollectionIT/primary/log-collection-test/configmaps")));
    }
}
