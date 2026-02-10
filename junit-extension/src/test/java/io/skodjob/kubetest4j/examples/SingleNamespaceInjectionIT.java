/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.RequiresKubernetes;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectNamespace;
import io.skodjob.kubetest4j.annotations.InjectNamespaces;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Example test demonstrating single namespace injection with @InjectNamespace.
 * This test shows how to:
 * - Inject specific namespace objects by name into test fields
 * - Use @InjectNamespace alongside @InjectNamespaces
 * - Access namespace metadata for specific namespaces
 */
@RequiresKubernetes
@KubernetesTest(
    namespaces = {"frontend-ns", "backend-ns", "monitoring-ns"},
    cleanup = CleanupStrategy.AUTOMATIC,
    namespaceLabels = {"test-type=single-namespace-injection", "framework=kubetest-junit"},
    namespaceAnnotations = {"description=Test for single namespace injection functionality"}
)
class SingleNamespaceInjectionIT {

    @InjectNamespace(name = "frontend-ns")
    Namespace frontendNamespace;

    @InjectNamespace(name = "backend-ns")
    Namespace backendNamespace;

    @InjectNamespace(name = "monitoring-ns")
    Namespace monitoringNamespace;

    @InjectNamespaces
    Map<String, Namespace> allNamespaces;

    @Test
    void testSingleNamespaceInjection() {
        // Verify that specific namespaces are injected correctly
        assertNotNull(frontendNamespace, "Frontend namespace should be injected");
        assertNotNull(backendNamespace, "Backend namespace should be injected");
        assertNotNull(monitoringNamespace, "Monitoring namespace should be injected");

        // Verify namespace names
        assertEquals("frontend-ns", frontendNamespace.getMetadata().getName());
        assertEquals("backend-ns", backendNamespace.getMetadata().getName());
        assertEquals("monitoring-ns", monitoringNamespace.getMetadata().getName());
    }

    @Test
    void testNamespaceMetadata() {
        // Verify namespace labels are applied correctly
        assertNotNull(frontendNamespace.getMetadata().getLabels());
        assertEquals("single-namespace-injection", frontendNamespace.getMetadata().getLabels().get("test-type"));
        assertEquals("kubetest-junit", frontendNamespace.getMetadata().getLabels().get("framework"));

        assertEquals("single-namespace-injection", backendNamespace.getMetadata().getLabels().get("test-type"));
        assertEquals("single-namespace-injection", monitoringNamespace.getMetadata().getLabels().get("test-type"));

        // Verify namespace annotations are applied
        for (Namespace namespace : new Namespace[]{frontendNamespace, backendNamespace, monitoringNamespace}) {
            assertNotNull(namespace.getMetadata().getAnnotations());
            assertEquals("Test for single namespace injection functionality",
                namespace.getMetadata().getAnnotations().get("description"));
        }
    }

    @Test
    void testCombinedInjection() {
        // Verify that single namespace injection matches map injection
        assertEquals(3, allNamespaces.size(), "Should have 3 total namespaces");

        assertEquals(frontendNamespace.getMetadata().getName(),
            allNamespaces.get("frontend-ns").getMetadata().getName());
        assertEquals(backendNamespace.getMetadata().getName(),
            allNamespaces.get("backend-ns").getMetadata().getName());
        assertEquals(monitoringNamespace.getMetadata().getName(),
            allNamespaces.get("monitoring-ns").getMetadata().getName());
    }

    @Test
    void testParameterInjection(@InjectNamespace(name = "frontend-ns") Namespace paramFrontend,
                                @InjectNamespace(name = "backend-ns") Namespace paramBackend) {
        // Demonstrate parameter injection for single namespaces
        assertNotNull(paramFrontend, "Parameter frontend namespace should be injected");
        assertNotNull(paramBackend, "Parameter backend namespace should be injected");

        assertEquals("frontend-ns", paramFrontend.getMetadata().getName());
        assertEquals("backend-ns", paramBackend.getMetadata().getName());

        // Verify parameter injection has same namespaces as field injection
        assertEquals(frontendNamespace.getMetadata().getName(),
            paramFrontend.getMetadata().getName());
        assertEquals(backendNamespace.getMetadata().getName(),
            paramBackend.getMetadata().getName());
    }

    @Test
    void testNamespaceStatus() {
        // Verify namespaces are active and ready
        for (Namespace namespace : new Namespace[]{frontendNamespace, backendNamespace, monitoringNamespace}) {
            assertNotNull(namespace.getStatus());
            assertEquals("Active", namespace.getStatus().getPhase());
        }
    }
}