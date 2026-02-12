/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResource;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Example test demonstrating resource injection from YAML files.
 * This test shows how to:
 * - Inject resources from YAML files using @InjectResource
 * - Automatically apply resources to the cluster
 * - Verify resources are deployed correctly
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KubernetesTest(
    namespaces = {"resource-injection-test"},
    cleanup = CleanupStrategy.AUTOMATIC,
    storeYaml = true,
    yamlStorePath = "target/test-yamls"
)
class ResourceInjectionIT {

    @InjectResource("src/test/resources/test-deployment-2.yaml")
    Deployment deployment;

    @InjectResource(value = "src/test/resources/test-service.yaml", type = Service.class)
    Service service;

    @InjectKubeClient
    KubeClient client;

    @Test
    void testResourcesInjected() {
        // Verify resources are injected
        assertNotNull(deployment, "Deployment should be injected from YAML");
        assertNotNull(service, "Service should be injected from YAML");

        // Verify deployment properties
        assertEquals("test-deployment", deployment.getMetadata().getName());
        assertEquals(2, deployment.getSpec().getReplicas());
        assertEquals("test-app", deployment.getSpec().getSelector().getMatchLabels().get("app"));
    }

    @Test
    void testResourcesAppliedToCluster() {
        // Verify resources are actually deployed to the cluster
        Deployment clusterDeployment = client.getClient().apps().deployments()
            .inNamespace("resource-injection-test")
            .withName("test-deployment")
            .get();

        assertNotNull(clusterDeployment, "Deployment should exist in cluster");
        assertEquals("test-deployment", clusterDeployment.getMetadata().getName());

        Service clusterService = client.getClient().services()
            .inNamespace("resource-injection-test")
            .withName("test-service")
            .get();

        assertNotNull(clusterService, "Service should exist in cluster");
        assertEquals("test-service", clusterService.getMetadata().getName());
        assertEquals("ClusterIP", clusterService.getSpec().getType());
    }

    @Test
    void testParameterInjection(@InjectResource("src/test/resources/test-deployment-2.yaml") Deployment paramDeployment,
                                @InjectResource("src/test/resources/test-service.yaml") Service paramService) {
        // Demonstrate parameter injection for resources
        assertNotNull(paramDeployment, "Parameter deployment should be injected");
        assertNotNull(paramService, "Parameter service should be injected");

        assertEquals("test-deployment", paramDeployment.getMetadata().getName());
        assertEquals("test-service", paramService.getMetadata().getName());
    }

    @Test
    void testResourcesHaveCorrectConfiguration() {
        // Verify detailed configuration of injected resources

        // Check deployment configuration
        assertEquals(2, deployment.getSpec().getReplicas());
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().getFirst();
        assertEquals("test-container", container.getName());
        assertEquals("quay.io/prometheus/busybox", container.getImage());
        assertEquals("test-value", container.getEnv().getFirst().getValue());

        // Check service configuration
        assertEquals("ClusterIP", service.getSpec().getType());
        assertEquals(80, service.getSpec().getPorts().get(0).getPort());
        assertEquals(8080, service.getSpec().getPorts().get(0).getTargetPort().getIntVal());
        assertEquals("test-app", service.getSpec().getSelector().get("app"));
    }
}
