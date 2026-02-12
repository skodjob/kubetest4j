/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectCmdKubeClient;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectNamespace;
import io.skodjob.kubetest4j.annotations.InjectNamespaces;
import io.skodjob.kubetest4j.annotations.InjectResource;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock multi-kube-context integration test demonstrating multi-kube-context support using a single cluster.
 * <p>
 * This test showcases:
 * 1. Mock multiple cluster kubeContexts using environment variables that point to the same cluster
 * 2. KubeContext-aware resource injection and management across different namespaces
 * 3. KubeContext-specific namespace configuration and isolation
 * 4. Multi-kubeContext log collection from all kubeContexts
 * 5. Mixed field and parameter injection
 * <p>
 * Prerequisites:
 * - Access to a single Kubernetes cluster (current kubeconfig kubeContext)
 * - Environment variables are set via Maven Failsafe/Surefire plugin configuration in pom.xml:
 * * KUBECONFIG_STAGING=${env.KUBECONFIG}
 * * KUBECONFIG_PRODUCTION=${env.KUBECONFIG}
 * * KUBECONFIG_DEVELOPMENT=${env.KUBECONFIG}
 * * (plus KUBE_URL_* and KUBE_TOKEN_* variants)
 * <p>
 * This allows testing multi-kube-context functionality without requiring multiple real clusters.
 * All kubeContexts (staging, production, development) use the same cluster but different namespaces.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KubernetesTest(
    // Default kubeContext namespaces
    namespaces = {"local-test", "local-monitoring"},
    cleanup = CleanupStrategy.AUTOMATIC,
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE,
    namespaceLabels = {"test-suite=multi-kube-context", "environment=local"},
    namespaceAnnotations = {"test.io/suite=multi-kube-context"},

    // Additional kube contexts for multi-context testing - all contexts use same cluster via env vars
    additionalKubeContexts = {
        @KubernetesTest.AdditionalKubeContext(
            name = "staging",
            namespaces = {"stg-frontend", "stg-backend"},
            namespaceLabels = {"environment=staging", "tier=application"},
            namespaceAnnotations = {"deployment.io/stage=staging"},
            cleanup = CleanupStrategy.AUTOMATIC),
        @KubernetesTest.AdditionalKubeContext(
            name = "production",
            namespaces = {"prod-api", "prod-cache"},
            namespaceLabels = {"environment=production"},
            cleanup = CleanupStrategy.AUTOMATIC),
        @KubernetesTest.AdditionalKubeContext(
            name = "development",
            namespaces = {"dev-experimental"},
            namespaceLabels = {"team=platform", "purpose=testing"},
            cleanup = CleanupStrategy.AUTOMATIC)
    }
)
class MultiContextIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiContextIT.class);

    // Default kubeContext field injections
    @InjectKubeClient
    KubeClient defaultClient;

    @InjectResourceManager
    KubeResourceManager defaultResourceManager;

    @InjectCmdKubeClient
    KubeCmdClient<?> defaultCmdClient;

    // KubeContext-specific field injections (all point to same cluster via env vars)
    @InjectKubeClient(kubeContext = "staging")
    KubeClient stagingClient;

    @InjectResourceManager(kubeContext = "staging")
    KubeResourceManager stagingResourceManager;

    @InjectKubeClient(kubeContext = "production")
    KubeClient productionClient;

    @InjectKubeClient(kubeContext = "development")
    KubeClient devClient;

    // Namespace injections - default kubeContext
    @InjectNamespaces
    Map<String, Namespace> defaultNamespaces;

    @InjectNamespace(name = "local-test")
    Namespace localTestNamespace;

    // Namespace injections - mock kubeContexts (same cluster, different namespaces)
    @InjectNamespaces(kubeContext = "staging")
    Map<String, Namespace> stagingNamespaces;

    @InjectNamespace(kubeContext = "staging", name = "stg-frontend")
    Namespace stagingFrontendNamespace;

    @InjectNamespace(kubeContext = "production", name = "prod-api")
    Namespace productionApiNamespace;

    @Test
    void testBasicMultiKubeContextSetup() {
        LOGGER.info("=== Testing Basic Multi-KubeContext Setup ===");

        // Verify all clients are injected and different
        assertNotNull(defaultClient, "Default KubeClient should be injected");
        assertNotNull(stagingClient, "Staging KubeClient should be injected");
        assertNotNull(productionClient, "Production KubeClient should be injected");
        assertNotNull(devClient, "Dev KubeClient should be injected");

        // Verify resource managers - each context gets its own isolated singleton instance
        assertNotNull(defaultResourceManager, "Default ResourceManager should be injected");
        assertNotNull(stagingResourceManager, "Staging ResourceManager should be injected");

        // Verify that multi-context managers are truly separate instances (not the same singleton)
        assertNotSame(defaultResourceManager, stagingResourceManager,
            "Multi-context resource managers should be separate instances");

        // Log kubeContext information
        LOGGER.info("Staging client: {}", stagingClient.getClass().getSimpleName());
        LOGGER.info("Production client: {}", productionClient.getClass().getSimpleName());

        // Verify namespaces
        assertEquals(2, defaultNamespaces.size(), "Should have 2 default namespaces");
        assertTrue(defaultNamespaces.containsKey("local-test"), "Should contain local-test namespace");
        assertTrue(defaultNamespaces.containsKey("local-monitoring"), "Should contain local-monitoring namespace");

        assertEquals(2, stagingNamespaces.size(), "Should have 2 staging namespaces");
        assertTrue(stagingNamespaces.containsKey("stg-frontend"), "Should contain stg-frontend namespace");
        assertTrue(stagingNamespaces.containsKey("stg-backend"), "Should contain stg-backend namespace");

        // Verify specific namespace injections
        assertNotNull(localTestNamespace, "Local test namespace should be injected");
        assertEquals("local-test", localTestNamespace.getMetadata().getName());

        assertNotNull(stagingFrontendNamespace, "Staging frontend namespace should be injected");
        assertEquals("stg-frontend", stagingFrontendNamespace.getMetadata().getName());

        assertNotNull(productionApiNamespace, "Production API namespace should be injected");
        assertEquals("prod-api", productionApiNamespace.getMetadata().getName());

        LOGGER.info("Basic multi-kube-context setup verified successfully");
    }

    @Test
    void testParameterInjection(
        @InjectKubeClient(kubeContext = "staging") KubeClient stgClient,
        @InjectResourceManager(kubeContext = "development") KubeResourceManager devManager,
        @InjectNamespace(kubeContext = "staging", name = "stg-backend") Namespace stgBackendNs,
        @InjectNamespaces(kubeContext = "development") Map<String, Namespace> devNamespaces
    ) {
        LOGGER.info("=== Testing Parameter Injection ===");

        // Verify parameter injections work (all point to same cluster via env vars)
        assertNotNull(stgClient, "Staging client should be injected via parameter");
        assertNotNull(devManager, "Dev resource manager should be injected via parameter");
        assertNotNull(stgBackendNs, "Staging backend namespace should be injected via parameter");
        assertNotNull(devNamespaces, "Dev namespaces should be injected via parameter");

        // Verify namespace details
        assertEquals("stg-backend", stgBackendNs.getMetadata().getName());
        assertEquals(1, devNamespaces.size(), "Should have 1 dev namespace");
        assertTrue(devNamespaces.containsKey("dev-experimental"));

        LOGGER.info("Parameter injection verified successfully");
    }

    @Test
    void testCrossKubeContextResourceOperations() {
        LOGGER.info("=== Testing Cross-KubeContext Resource Operations ===");

        // Create resources in different kubeContexts

        // 1. Create ConfigMap in default kubeContext
        ConfigMap defaultConfigMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("multi-kube-context-config")
            .withNamespace(localTestNamespace.getMetadata().getName())
            .endMetadata()
            .addToData("environment", "local")
            .addToData("test-type", "multi-kube-context")
            .build();

        defaultResourceManager.createResourceWithoutWait(defaultConfigMap);
        LOGGER.info("Created ConfigMap in default kubeContext: {}", defaultConfigMap.getMetadata().getName());

        // 2. Create Pod in staging kubeContext
        Pod stagingPod = new PodBuilder()
            .withNewMetadata()
            .withName("staging-test-pod")
            .withNamespace(stagingFrontendNamespace.getMetadata().getName())
            .addToLabels("kube-context", "staging")
            .addToLabels("test", "multi-kube-context")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("test-container")
            .withImage("nginx:alpine")
            .addNewPort()
            .withContainerPort(80)
            .endPort()
            .endContainer()
            .endSpec()
            .build();

        stagingResourceManager.createResourceWithWait(stagingPod);
        LOGGER.info("Created Pod in staging kubeContext: {}", stagingPod.getMetadata().getName());

        // 3. Verify resources exist in their respective kubeContexts
        ConfigMap retrievedConfigMap = defaultClient.getClient()
            .configMaps()
            .inNamespace(localTestNamespace.getMetadata().getName())
            .withName("multi-kube-context-config")
            .get();
        assertNotNull(retrievedConfigMap, "ConfigMap should exist in default kubeContext");
        assertEquals("local", retrievedConfigMap.getData().get("environment"));

        Pod retrievedPod = stagingClient.getClient()
            .pods()
            .inNamespace(stagingFrontendNamespace.getMetadata().getName())
            .withName("staging-test-pod")
            .get();
        assertNotNull(retrievedPod, "Pod should exist in staging kubeContext");
        assertEquals("staging", retrievedPod.getMetadata().getLabels().get("kube-context"));

        LOGGER.info("Mock cross-kubeContext resource operations verified successfully");
    }

    @Test
    void testResourceInjectionWithKubeContext(
        @InjectResource(kubeContext = "staging", value = "src/test/resources/test-deployment.yaml")
        Deployment injectedDeployment
    ) {
        LOGGER.info("=== Testing Resource Injection with KubeContext ===");

        // This test assumes test-deployment.yaml exists in test resources
        // The resource will be deployed to the staging kubeContext (same cluster, staging namespace)
        assertNotNull(injectedDeployment, "Deployment should be injected from YAML in staging kubeContext");
        LOGGER.info("Injected deployment: {} in namespace: {}",
            injectedDeployment.getMetadata().getName(),
            injectedDeployment.getMetadata().getNamespace());

        // Verify the deployment was created in the staging kubeContext (same cluster)
        Deployment stagingDeployment = stagingClient.getClient()
            .apps().deployments()
            .inNamespace(injectedDeployment.getMetadata().getNamespace())
            .withName(injectedDeployment.getMetadata().getName())
            .get();

        assertNotNull(stagingDeployment, "Injected deployment should exist in staging kubeContext");
        assertEquals(injectedDeployment.getMetadata().getName(), stagingDeployment.getMetadata().getName());

        LOGGER.info("Resource injection with kubeContext verified successfully");
    }

    @Test
    void testNamespaceLabelsAndAnnotations() {
        LOGGER.info("=== Testing Namespace Labels and Annotations ===");

        // Check default namespace labels
        Namespace defaultNs = defaultNamespaces.get("local-test");

        assertEquals("multi-kube-context", defaultNs.getMetadata().getLabels().get("test-suite"));
        assertEquals("local", defaultNs.getMetadata().getLabels().get("environment"));
        assertEquals("multi-kube-context", defaultNs.getMetadata().getAnnotations().get("test.io/suite"));

        // Check staging namespace labels
        Namespace stagingNs = stagingNamespaces.get("stg-frontend");
        assertEquals("staging", stagingNs.getMetadata().getLabels().get("environment"));
        assertEquals("application", stagingNs.getMetadata().getLabels().get("tier"));
        assertEquals("staging", stagingNs.getMetadata().getAnnotations().get("deployment.io/stage"));

        LOGGER.info("Namespace labels and annotations verified successfully");
    }

    @Test
    void testMockMultiKubeContextLogCollection() {
        LOGGER.info("=== Testing Mock Multi-KubeContext Log Collection ===");

        // Create labeled pods in different mock kubeContexts for log collection testing
        // These pods will have the log collection label to trigger multi-kube-context log collection

        Pod defaultLogPod = new PodBuilder()
            .withNewMetadata()
            .withName("log-test-default")
            .withNamespace(localTestNamespace.getMetadata().getName())
            .addToLabels("collect-logs", "true")  // This label triggers log collection
            .addToLabels("kube-context", "default")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("log-container")
            .withImage("quay.io/prometheus/busybox")
            .withCommand("sh", "-c", "echo 'Default kubeContext log message' && sleep 60")
            .endContainer()
            .endSpec()
            .build();

        Pod stagingLogPod = new PodBuilder()
            .withNewMetadata()
            .withName("log-test-staging")
            .withNamespace(stagingFrontendNamespace.getMetadata().getName())
            .addToLabels("collect-logs", "true")  // This label triggers log collection
            .addToLabels("kube-context", "staging")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("log-container")
            .withImage("quay.io/prometheus/busybox")
            .withCommand("sh", "-c", "echo 'Staging kubeContext log message' && sleep 60")
            .endContainer()
            .endSpec()
            .build();

        Pod productionLogPod = new PodBuilder()
            .withNewMetadata()
            .withName("log-test-production")
            .withNamespace(productionApiNamespace.getMetadata().getName())
            .addToLabels("collect-logs", "true")  // This label triggers log collection
            .addToLabels("kube-context", "production")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("log-container")
            .withImage("quay.io/prometheus/busybox")
            .withCommand("sh", "-c", "echo 'Production kubeContext log message' && sleep 60")
            .endContainer()
            .endSpec()
            .build();

        // Create pods for log collection testing across all mock contexts
        defaultResourceManager.createResourceWithWait(defaultLogPod);
        stagingResourceManager.createResourceWithWait(stagingLogPod);

        KubeResourceManager productionResourceManager = getResourceManagerForContext("production");
        productionResourceManager.createResourceWithWait(productionLogPod);

        LOGGER.info("Created log test pods in multiple mock contexts:");
        LOGGER.info("  Default pod: {} in namespace: {}",
            defaultLogPod.getMetadata().getName(),
            defaultLogPod.getMetadata().getNamespace());
        LOGGER.info("  Staging pod: {} in namespace: {}",
            stagingLogPod.getMetadata().getName(),
            stagingLogPod.getMetadata().getNamespace());
        LOGGER.info("  Production pod: {} in namespace: {}",
            productionLogPod.getMetadata().getName(),
            productionLogPod.getMetadata().getNamespace());

        // Verify pods exist and can be accessed from all mock contexts
        assertNotNull(defaultClient.getClient()
            .pods()
            .inNamespace(localTestNamespace.getMetadata().getName())
            .withName("log-test-default")
            .get(), "Default log test pod should exist");

        assertNotNull(stagingClient.getClient()
            .pods()
            .inNamespace(stagingFrontendNamespace.getMetadata().getName())
            .withName("log-test-staging")
            .get(), "Staging log test pod should exist");

        assertNotNull(productionClient.getClient()
            .pods()
            .inNamespace(productionApiNamespace.getMetadata().getName())
            .withName("log-test-production")
            .get(), "Production log test pod should exist");

        LOGGER.info("Mock multi-kube-context log collection setup verified successfully");
        LOGGER.info("Note: Log collection from all contexts will be triggered automatically by the framework");
        LOGGER.info("      when tests complete or fail, collecting from all labeled namespaces across all contexts");
    }

    @Test
    void testContextSwitchingBehavior() {
        LOGGER.info("=== Testing Mock KubeContext Switching Behavior ===");

        // Create services with same name in different contexts to verify namespace isolation
        // (all in same cluster but different namespaces due to mock environment)
        Service defaultService = new ServiceBuilder()
            .withNewMetadata()
            .withName("isolation-test-service")
            .withNamespace(localTestNamespace.getMetadata().getName())
            .endMetadata()
            .withNewSpec()
            .addToSelector("app", "default-app")
            .addNewPort()
            .withPort(8080)
            .withTargetPort(new IntOrString(80))
            .endPort()
            .endSpec()
            .build();

        Service stagingService = new ServiceBuilder()
            .withNewMetadata()
            .withName("isolation-test-service")
            .withNamespace(stagingFrontendNamespace.getMetadata().getName())
            .endMetadata()
            .withNewSpec()
            .addToSelector("app", "staging-app")
            .addNewPort()
            .withPort(9090)
            .withTargetPort(new IntOrString(80))
            .endPort()
            .endSpec()
            .build();

        // Create services in different mock contexts (same cluster, different namespaces)
        defaultResourceManager.createResourceWithoutWait(defaultService);
        stagingResourceManager.createResourceWithoutWait(stagingService);

        // Verify services exist independently (namespace isolation)
        Service defaultSvc = defaultClient.getClient()
            .services()
            .inNamespace(localTestNamespace.getMetadata().getName())
            .withName("isolation-test-service")
            .get();
        assertNotNull(defaultSvc, "Service should exist in default context");
        assertEquals("default-app", defaultSvc.getSpec().getSelector().get("app"));

        Service stagingSvc = stagingClient.getClient()
            .services()
            .inNamespace(stagingFrontendNamespace.getMetadata().getName())
            .withName("isolation-test-service")
            .get();
        assertNotNull(stagingSvc, "Service should exist in staging context");
        assertEquals("staging-app", stagingSvc.getSpec().getSelector().get("app"));

        // Verify isolation - services with same name but different configs (namespace isolation)
        assertEquals(8080, defaultSvc.getSpec().getPorts().getFirst().getPort().intValue());
        assertEquals(9090, stagingSvc.getSpec().getPorts().getFirst().getPort().intValue());

        LOGGER.info("Mock kubeContext switching and namespace isolation verified successfully");
    }

    @Test
    void testCmdClientMultiContext() {
        LOGGER.info("=== Testing Mock Command Client Multi-Context ===");

        // Test default context cmd client
        assertNotNull(defaultCmdClient, "Default cmd client should be injected");

        // Get cluster info (same cluster for all mock contexts)
        String defaultClusterInfo = defaultCmdClient.exec("cluster-info").out();
        LOGGER.info("Cluster info (all mock contexts point to same cluster): {}", defaultClusterInfo);
        assertTrue(defaultClusterInfo.contains("Kubernetes"), "Should contain Kubernetes info");

        LOGGER.info("Mock command client multi-kube-context verified successfully");
        LOGGER.info("Note: All mock contexts use the same underlying cluster but different namespaces");
    }

    /**
     * Helper method to get resource manager for a specific kubeContext.
     * In a real scenario, this would be injected, but for testing we simulate it.
     */
    private KubeResourceManager getResourceManagerForContext(String kubeContext) {
        // In the mock environment, all kubeContexts point to the same cluster
        // So we can return the default resource manager
        // Note: In a real implementation, this would return context-specific managers based on kubeContext
        LOGGER.debug("Getting resource manager for kubeContext: {}", kubeContext);
        return defaultResourceManager;
    }
}