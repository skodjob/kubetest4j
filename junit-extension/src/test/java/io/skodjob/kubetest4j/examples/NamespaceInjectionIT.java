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
 * - Inject multiple namespace objects into static fields
 * - Access namespace metadata and properties
 */
@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
class NamespaceInjectionIT {

    @ClassNamespace(name = "namespace-test-1",
        labels = {"test-type=namespace-injection", "framework=kubetest-junit"},
        annotations = {"description=Test for namespace injection functionality"})
    static Namespace ns1;

    @ClassNamespace(name = "namespace-test-2",
        labels = {"test-type=namespace-injection", "framework=kubetest-junit"},
        annotations = {"description=Test for namespace injection functionality"})
    static Namespace ns2;

    @ClassNamespace(name = "namespace-test-3",
        labels = {"test-type=namespace-injection", "framework=kubetest-junit"},
        annotations = {"description=Test for namespace injection functionality"})
    static Namespace ns3;

    @Test
    void testNamespaceInjection() {
        // Verify that all namespaces are injected
        assertNotNull(ns1, "Namespace 1 should be injected");
        assertNotNull(ns2, "Namespace 2 should be injected");
        assertNotNull(ns3, "Namespace 3 should be injected");

        // Verify namespace names
        assertEquals("namespace-test-1", ns1.getMetadata().getName());
        assertEquals("namespace-test-2", ns2.getMetadata().getName());
        assertEquals("namespace-test-3", ns3.getMetadata().getName());
    }

    @Test
    void testNamespaceMetadata() {
        // Verify namespace labels are applied correctly
        for (Namespace namespace : new Namespace[]{ns1, ns2, ns3}) {
            assertNotNull(namespace.getMetadata().getLabels());
            assertEquals("namespace-injection", namespace.getMetadata().getLabels().get("test-type"));
            assertEquals("kubetest-junit", namespace.getMetadata().getLabels().get("framework"));
        }

        // Verify namespace annotations are applied
        for (Namespace namespace : new Namespace[]{ns1, ns2, ns3}) {
            assertNotNull(namespace.getMetadata().getAnnotations());
            assertEquals("Test for namespace injection functionality",
                namespace.getMetadata().getAnnotations().get("description"));
        }
    }

    @Test
    void testNamespaceStatus() {
        // Verify namespaces are active and ready
        for (Namespace namespace : new Namespace[]{ns1, ns2, ns3}) {
            assertNotNull(namespace.getStatus());
            assertEquals("Active", namespace.getStatus().getPhase());
        }
    }
}
