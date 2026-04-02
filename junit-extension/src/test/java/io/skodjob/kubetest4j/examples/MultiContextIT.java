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
import io.skodjob.kubetest4j.annotations.InjectResource;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 3. KubeContext-specific namespace configuration via @ClassNamespace
 * 4. Multi-kubeContext log collection from all kubeContexts
 * 5. Mixed field and parameter injection
 * <p>
 * Prerequisites:
 * - Access to a single Kubernetes cluster (current kubeconfig kubeContext)
 * - Environment variables are set via Maven Failsafe/Surefire plugin configuration in pom.xml:
 *   * KUBECONFIG_STAGING=${env.KUBECONFIG}
 *   * KUBECONFIG_PRODUCTION=${env.KUBECONFIG}
 *   * KUBECONFIG_DEVELOPMENT=${env.KUBECONFIG}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KubernetesTest(
    cleanup = CleanupStrategy.AUTOMATIC,
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE
)
class MultiContextIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiContextIT.class);

    // Default kubeContext namespaces
    @ClassNamespace(name = "local-test",
        labels = {"test-suite=multi-kube-context", "environment=local"},
        annotations = {"test.io/suite=multi-kube-context"})
    static Namespace localTestNamespace;

    @ClassNamespace(name = "local-monitoring",
        labels = {"test-suite=multi-kube-context", "environment=local"},
        annotations = {"test.io/suite=multi-kube-context"})
    static Namespace localMonitoringNamespace;

    // Staging kubeContext namespaces
    @ClassNamespace(name = "stg-frontend", kubeContext = "staging",
        labels = {"environment=staging", "tier=application"},
        annotations = {"deployment.io/stage=staging"})
    static Namespace stagingFrontendNamespace;

    @ClassNamespace(name = "stg-backend", kubeContext = "staging",
        labels = {"environment=staging", "tier=application"},
        annotations = {"deployment.io/stage=staging"})
    static Namespace stagingBackendNamespace;

    // Production kubeContext namespaces
    @ClassNamespace(name = "prod-api", kubeContext = "production",
        labels = {"environment=production"})
    static Namespace productionApiNamespace;

    @ClassNamespace(name = "prod-cache", kubeContext = "production",
        labels = {"environment=production"})
    static Namespace productionCacheNamespace;

    // Development kubeContext namespace
    @ClassNamespace(name = "dev-experimental", kubeContext = "development",
        labels = {"team=platform", "purpose=testing"})
    static Namespace devExperimentalNamespace;

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

    @Test
    void testBasicMultiKubeContextSetup() {
        LOGGER.info("=== Testing Basic Multi-KubeContext Setup ===");

        // Verify all clients are injected and different
        assertNotNull(defaultClient, "Default KubeClient should be injected");
        assertNotNull(stagingClient, "Staging KubeClient should be injected");
        assertNotNull(productionClient, "Production KubeClient should be injected");
        assertNotNull(devClient, "Dev KubeClient should be injected");

        // Verify resource managers
        assertNotNull(defaultResourceManager, "Default ResourceManager should be injected");
        assertNotNull(stagingResourceManager, "Staging ResourceManager should be injected");

        assertNotSame(defaultResourceManager, stagingResourceManager,
            "Multi-context resource managers should be separate instances");

        // Verify namespaces
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
        @InjectResourceManager(kubeContext = "development") KubeResourceManager devManager
    ) {
        LOGGER.info("=== Testing Parameter Injection ===");

        assertNotNull(stgClient, "Staging client should be injected via parameter");
        assertNotNull(devManager, "Dev resource manager should be injected via parameter");

        LOGGER.info("Parameter injection verified successfully");
    }

    @Test
    void testCrossKubeContextResourceOperations() {
        LOGGER.info("=== Testing Cross-KubeContext Resource Operations ===");

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

        // 2. Create Pod in staging kubeContext
        Pod stagingPod = new PodBuilder()
            .withNewMetadata()
            .withName("staging-test-pod")
            .withNamespace(stagingFrontendNamespace.getMetadata().getName())
            .addToLabels("kube-context", "staging")
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

        // 3. Verify resources exist
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

        LOGGER.info("Cross-kubeContext resource operations verified successfully");
    }

    @Test
    void testResourceInjectionWithKubeContext(
        @InjectResource(kubeContext = "staging", value = "test-deployment.yaml")
        Deployment injectedDeployment
    ) {
        LOGGER.info("=== Testing Resource Injection with KubeContext ===");

        assertNotNull(injectedDeployment, "Deployment should be injected from YAML in staging kubeContext");

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
        assertEquals("multi-kube-context",
            localTestNamespace.getMetadata().getLabels().get("test-suite"));
        assertEquals("local",
            localTestNamespace.getMetadata().getLabels().get("environment"));
        assertEquals("multi-kube-context",
            localTestNamespace.getMetadata().getAnnotations().get("test.io/suite"));

        // Check staging namespace labels
        assertEquals("staging",
            stagingFrontendNamespace.getMetadata().getLabels().get("environment"));
        assertEquals("application",
            stagingFrontendNamespace.getMetadata().getLabels().get("tier"));
        assertEquals("staging",
            stagingFrontendNamespace.getMetadata().getAnnotations().get("deployment.io/stage"));

        LOGGER.info("Namespace labels and annotations verified successfully");
    }

    @Test
    void testContextSwitchingBehavior() {
        LOGGER.info("=== Testing Mock KubeContext Switching Behavior ===");

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

        defaultResourceManager.createResourceWithoutWait(defaultService);
        stagingResourceManager.createResourceWithoutWait(stagingService);

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

        assertEquals(8080, defaultSvc.getSpec().getPorts().getFirst().getPort().intValue());
        assertEquals(9090, stagingSvc.getSpec().getPorts().getFirst().getPort().intValue());

        LOGGER.info("Mock kubeContext switching and namespace isolation verified successfully");
    }

    @Test
    void testCmdClientMultiContext() {
        LOGGER.info("=== Testing Mock Command Client Multi-Context ===");

        assertNotNull(defaultCmdClient, "Default cmd client should be injected");

        String defaultClusterInfo = defaultCmdClient.exec("cluster-info").out();
        assertTrue(defaultClusterInfo.contains("Kubernetes"), "Should contain Kubernetes info");

        LOGGER.info("Mock command client multi-kube-context verified successfully");
    }
}
