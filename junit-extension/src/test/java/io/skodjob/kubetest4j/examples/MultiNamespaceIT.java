/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.IntOrStringBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.skodjob.kubetest4j.annotations.RequiresKubernetes;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Example test demonstrating multi-namespace testing with explicit namespace specification.
 * Shows how to create resources in different namespaces and collect logs from all of them.
 */
@RequiresKubernetes
@KubernetesTest(
    // Create multiple namespaces for testing
    namespaces = {"frontend", "backend", "monitoring"},
    cleanup = CleanupStrategy.AUTOMATIC,

    // Log collection from all namespaces automatically
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE,
    logCollectionPath = "target/test-logs/multi-namespace"
)
class MultiNamespaceIT {

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testMultiNamespaceResourceCreation() {
        // Create frontend resources
        ConfigMap frontendConfig = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("frontend-config")
            .withNamespace("frontend")  // Explicit namespace
            .endMetadata()
            .addToData("app.name", "frontend-app")
            .addToData("app.version", "1.0.0")
            .build();

        // Create backend resources
        Service backendService = new ServiceBuilder()
            .withNewMetadata()
            .withName("backend-service")
            .withNamespace("backend")  // Explicit namespace
            .endMetadata()
            .withNewSpec()
            .withSelector(Map.of("app", "backend"))
            .addNewPort()
            .withPort(8080)
            .withTargetPort(new IntOrStringBuilder().withValue(8080).build())
            .endPort()
            .endSpec()
            .build();

        // Create monitoring resources
        Secret monitoringSecret = new SecretBuilder()
            .withNewMetadata()
            .withName("monitoring-credentials")
            .withNamespace("monitoring")  // Explicit namespace
            .endMetadata()
            .addToData("username", "bW9uaXRvcmluZw==")  // base64: monitoring
            .addToData("password", "c2VjcmV0MTIz")      // base64: secret123
            .build();

        // Create all resources
        resourceManager.createResourceWithWait(frontendConfig);
        resourceManager.createResourceWithWait(backendService);
        resourceManager.createResourceWithWait(monitoringSecret);

        // Verify resources were created in correct namespaces
        ConfigMap retrievedConfig = client.getClient().configMaps()
            .inNamespace("frontend")
            .withName("frontend-config")
            .get();
        assertNotNull(retrievedConfig);

        Service retrievedService = client.getClient().services()
            .inNamespace("backend")
            .withName("backend-service")
            .get();
        assertNotNull(retrievedService);

        Secret retrievedSecret = client.getClient().secrets()
            .inNamespace("monitoring")
            .withName("monitoring-credentials")
            .get();
        assertNotNull(retrievedSecret);
    }
}
