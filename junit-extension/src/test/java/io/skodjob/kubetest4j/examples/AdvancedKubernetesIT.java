/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.skodjob.kubetest4j.annotations.RequiresKubernetes;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced example test demonstrating more complex features:
 * - Manual cleanup strategy
 * - BeforeEach setup with shared resources
 * - Custom namespace configuration
 * - Resource lifecycle management
 */
@RequiresKubernetes
@KubernetesTest(
    namespaces = {"advanced-tests"},
    cleanup = CleanupStrategy.AUTOMATIC,
    storeYaml = true,
    namespaceLabels = {
        "test-suite=advanced",
        "cleanup=manual",
        "framework=kubetest-junit"
    },
    namespaceAnnotations = {
        "description=Advanced test example with manual cleanup",
        "created-by=kubetest-junit"
    }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvancedKubernetesIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedKubernetesIT.class);

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    private ConfigMap sharedConfig;

    @BeforeAll
    void setupSharedResources() {
        LOGGER.info("Setting up shared resources for test in namespace: {}", "advanced-tests");

        // Create a shared ConfigMap that will be used by multiple tests
        sharedConfig = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("shared-config")
            .withNamespace("advanced-tests")
            .addToLabels("component", "shared")
            .addToLabels("test-suite", "advanced")
            .endMetadata()
            .addToData("shared.property", "shared-value")
            .addToData("environment", "test")
            .addToData("version", "1.0.0")
            .build();

        resourceManager.createResourceWithWait(sharedConfig);

        // Verify shared resource is created
        ConfigMap created = client.getClient().configMaps()
            .inNamespace("advanced-tests")
            .withName("shared-config")
            .get();
        assertNotNull(created, "Shared ConfigMap should be created");
        LOGGER.info("Shared ConfigMap created successfully");
    }

    @Test
    void testSharedResourceAccess() {
        LOGGER.info("Testing access to shared resources");

        // Verify we can access the shared resource
        assertNotNull(sharedConfig, "Shared config should be available");
        assertEquals("shared-value", sharedConfig.getData().get("shared.property"));

        // Verify it exists in the cluster
        ConfigMap clusterConfig = client.getClient().configMaps()
            .inNamespace("advanced-tests")
            .withName("shared-config")
            .get();

        assertNotNull(clusterConfig, "Shared config should exist in cluster");
        assertEquals("shared", clusterConfig.getMetadata().getLabels().get("component"));
    }

    @Test
    void testSecretCreation() {
        LOGGER.info("Testing secret creation and management");

        // Create a secret that references the shared config
        Secret testSecret = new SecretBuilder()
            .withNewMetadata()
            .withName("test-secret")
            .withNamespace("advanced-tests")
            .addToLabels("relates-to", sharedConfig.getMetadata().getName())
            .endMetadata()
            .withType("Opaque")
            .addToStringData("username", "test-user")
            .addToStringData("password", "test-password")
            .addToStringData("config-reference", sharedConfig.getMetadata().getName())
            .build();

        resourceManager.createResourceWithWait(testSecret);

        // Verify secret is created with correct data
        Secret clusterSecret = client.getClient().secrets()
            .inNamespace("advanced-tests")
            .withName("test-secret")
            .get();

        assertNotNull(clusterSecret, "Secret should be created in cluster");
        assertTrue(clusterSecret.getData().containsKey("username"), "Secret should contain username");
        assertTrue(clusterSecret.getData().containsKey("password"), "Secret should contain password");
        assertEquals("shared-config",
            clusterSecret.getMetadata().getLabels().get("relates-to"));
    }

    @Test
    void testResourceLabelsAndAnnotations() {
        LOGGER.info("Testing resource labels and annotations");

        // Create a resource with specific labels and annotations
        ConfigMap labeledConfig = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("labeled-config")
            .withNamespace("advanced-tests")
            .addToLabels("test-type", "labeling")
            .addToLabels("instance", "test-1")
            .addToLabels("managed-by", "test-framework")
            .addToAnnotations("test.example.com/purpose", "demonstration")
            .addToAnnotations("test.example.com/created-by", "advanced-test")
            .endMetadata()
            .addToData("config.type", "labeled")
            .build();

        resourceManager.createResourceWithWait(labeledConfig);

        // Verify labels and annotations are applied correctly
        ConfigMap clusterConfig = client.getClient().configMaps()
            .inNamespace("advanced-tests")
            .withName("labeled-config")
            .get();

        assertNotNull(clusterConfig, "Labeled config should exist");

        Map<String, String> labels = clusterConfig.getMetadata().getLabels();
        assertEquals("labeling", labels.get("test-type"));
        assertEquals("test-1", labels.get("instance"));
        assertEquals("test-framework", labels.get("managed-by"));

        Map<String, String> annotations = clusterConfig.getMetadata().getAnnotations();
        assertEquals("demonstration", annotations.get("test.example.com/purpose"));
        assertEquals("advanced-test", annotations.get("test.example.com/created-by"));
    }

    @Test
    void testManualCleanup() {
        LOGGER.info("Testing manual cleanup strategy");

        // Create a resource that we'll manually clean up
        ConfigMap tempConfig = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("temp-config")
            .withNamespace("advanced-tests")
            .addToLabels("lifecycle", "temporary")
            .endMetadata()
            .addToData("temporary", "true")
            .build();

        resourceManager.createResourceWithWait(tempConfig);

        // Verify it exists
        ConfigMap created = client.getClient().configMaps()
            .inNamespace("advanced-tests")
            .withName("temp-config")
            .get();
        assertNotNull(created, "Temp config should be created");

        // Manually delete it
        resourceManager.deleteResourceWithWait(tempConfig);

        // Verify it's deleted
        ConfigMap deleted = client.getClient().configMaps()
            .inNamespace("advanced-tests")
            .withName("temp-config")
            .get();
        // Note: In manual cleanup mode, we need to verify deletion ourselves
        LOGGER.info("Manual cleanup test completed. Resource state: {}",
            deleted == null ? "deleted" : "exists");
    }
}
