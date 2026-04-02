/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Example test demonstrating class namespace injection with @ClassNamespace.
 * This test shows how to:
 * - Inject specific namespace objects by name into static fields
 * - Use @ClassNamespace with labels and annotations
 * - Access namespace metadata
 */
@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
class SingleNamespaceInjectionIT {

    @ClassNamespace(name = "frontend-ns",
        labels = {"test-type=single-namespace-injection", "framework=kubetest-junit"},
        annotations = {"description=Test for single namespace injection functionality"})
    static Namespace frontendNamespace;

    @ClassNamespace(name = "backend-ns",
        labels = {"test-type=single-namespace-injection", "framework=kubetest-junit"},
        annotations = {"description=Test for single namespace injection functionality"})
    static Namespace backendNamespace;

    @ClassNamespace(name = "monitoring-ns",
        labels = {"test-type=single-namespace-injection", "framework=kubetest-junit"},
        annotations = {"description=Test for single namespace injection functionality"})
    static Namespace monitoringNamespace;

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
        assertEquals("single-namespace-injection",
            frontendNamespace.getMetadata().getLabels().get("test-type"));
        assertEquals("kubetest-junit",
            frontendNamespace.getMetadata().getLabels().get("framework"));

        assertEquals("single-namespace-injection",
            backendNamespace.getMetadata().getLabels().get("test-type"));
        assertEquals("single-namespace-injection",
            monitoringNamespace.getMetadata().getLabels().get("test-type"));

        // Verify namespace annotations are applied
        for (Namespace namespace : new Namespace[]{frontendNamespace, backendNamespace, monitoringNamespace}) {
            assertNotNull(namespace.getMetadata().getAnnotations());
            assertEquals("Test for single namespace injection functionality",
                namespace.getMetadata().getAnnotations().get("description"));
        }
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
