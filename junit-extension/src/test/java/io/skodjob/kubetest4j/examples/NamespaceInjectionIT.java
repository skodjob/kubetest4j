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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example test demonstrating namespace injection with @InjectNamespaces.
 * This test shows how to:
 * - Inject a Map of namespace objects into test fields (key=namespace name, value=Namespace object)
 * - Access namespace metadata and properties
 * - Use both created and existing namespaces
 */
@RequiresKubernetes
@KubernetesTest(
    namespaces = {"namespace-test-1", "namespace-test-2", "namespace-test-3"},
    cleanup = CleanupStrategy.AUTOMATIC,
    namespaceLabels = {"test-type=namespace-injection", "framework=kubetest-junit"},
    namespaceAnnotations = {"description=Test for namespace injection functionality"}
)
class NamespaceInjectionIT {

    @InjectNamespaces
    Map<String, Namespace> namespaces;

    @InjectNamespace(name = "namespace-test-2")
    Namespace test2;

    @Test
    void testNamespaceInjection() {
        // Verify that all namespaces are injected
        assertNotNull(namespaces, "Namespaces map should be injected");
        assertEquals(3, namespaces.size(), "Should have 3 injected namespaces");

        // Verify namespace names are in the map as keys
        assertTrue(namespaces.containsKey("namespace-test-1"));
        assertTrue(namespaces.containsKey("namespace-test-2"));
        assertTrue(namespaces.containsKey("namespace-test-3"));

        // Verify namespace objects are properly mapped
        assertEquals("namespace-test-1", namespaces.get("namespace-test-1").getMetadata().getName());
        assertEquals("namespace-test-2", namespaces.get("namespace-test-2").getMetadata().getName());
        assertEquals("namespace-test-3", namespaces.get("namespace-test-3").getMetadata().getName());

        // Verify single namespace injection
        assertEquals("namespace-test-2", test2.getMetadata().getName());
    }

    @Test
    void testNamespaceMetadata() {
        // Verify namespace labels are applied correctly
        for (Namespace namespace : namespaces.values()) {
            assertNotNull(namespace.getMetadata().getLabels());
            assertEquals("namespace-injection", namespace.getMetadata().getLabels().get("test-type"));
            assertEquals("kubetest-junit", namespace.getMetadata().getLabels().get("framework"));
        }

        // Verify namespace annotations are applied
        for (Namespace namespace : namespaces.values()) {
            assertNotNull(namespace.getMetadata().getAnnotations());
            assertEquals("Test for namespace injection functionality",
                namespace.getMetadata().getAnnotations().get("description"));
        }
    }

    @Test
    void testParameterInjection(@InjectNamespaces Map<String, Namespace> paramNamespaces) {
        // Demonstrate parameter injection for namespaces
        assertNotNull(paramNamespaces, "Parameter namespaces should be injected");
        assertEquals(3, paramNamespaces.size(), "Parameter should have 3 namespaces");

        // Verify parameter injection has same namespaces as field injection
        for (String namespaceName : namespaces.keySet()) {
            assertTrue(paramNamespaces.containsKey(namespaceName),
                "Parameter injection should contain namespace: " + namespaceName);
            assertEquals(namespaces.get(namespaceName).getMetadata().getName(),
                paramNamespaces.get(namespaceName).getMetadata().getName());
        }
    }

    @Test
    void testNamespaceStatus() {
        // Verify namespaces are active and ready
        for (Namespace namespace : namespaces.values()) {
            assertNotNull(namespace.getStatus());
            assertEquals("Active", namespace.getStatus().getPhase());
        }
    }
}