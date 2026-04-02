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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example test demonstrating namespace protection functionality.
 * This test shows how the framework:
 * - Protects existing namespaces from deletion (like 'default', 'kube-system')
 * - Only deletes namespaces that were created by the test
 * - Safely mixes existing and test-created namespaces
 */
@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
class NamespaceProtectionIT {

    // Mix of existing namespaces (default) and test-created namespaces
    @ClassNamespace(name = "default")
    static Namespace defaultNamespace; // This should NEVER be deleted (namespace protection)

    @ClassNamespace(name = "protection-test-new-1",
        labels = {"test-type=namespace-protection", "framework=kubetest-junit"},
        annotations = {"description=Test demonstrating namespace protection"})
    static Namespace createdNamespace1; // This will be deleted after test

    @ClassNamespace(name = "protection-test-new-2",
        labels = {"test-type=namespace-protection", "framework=kubetest-junit"},
        annotations = {"description=Test demonstrating namespace protection"})
    static Namespace createdNamespace2; // This will be deleted after test

    @Test
    void testMixedNamespaceUsage() {
        // Verify all namespaces are available
        assertNotNull(defaultNamespace, "Default namespace should be injected");
        assertNotNull(createdNamespace1, "Created namespace 1 should be injected");
        assertNotNull(createdNamespace2, "Created namespace 2 should be injected");

        assertEquals("default", defaultNamespace.getMetadata().getName());
        assertEquals("protection-test-new-1", createdNamespace1.getMetadata().getName());
        assertEquals("protection-test-new-2", createdNamespace2.getMetadata().getName());
    }

    @Test
    void testExistingNamespaceProtection() {
        // Default namespace should exist and have standard properties
        assertEquals("default", defaultNamespace.getMetadata().getName());
        assertNotNull(defaultNamespace.getStatus());
        assertEquals("Active", defaultNamespace.getStatus().getPhase());

        // Default namespace should NOT have our test labels/annotations
        // because it existed before the test and was not modified
        Map<String, String> labels = defaultNamespace.getMetadata().getLabels();
        if (labels != null) {
            // Our test labels should NOT be applied to existing namespaces
            assertTrue(
                !labels.containsKey("test-type") ||
                !"namespace-protection".equals(labels.get("test-type")),
                "Existing 'default' namespace should not have test labels applied"
            );
        }
    }

    @Test
    void testCreatedNamespaceLabeling() {
        // Test-created namespaces should have our labels and annotations
        for (Namespace namespace : new Namespace[]{createdNamespace1, createdNamespace2}) {
            Map<String, String> labels = namespace.getMetadata().getLabels();
            assertNotNull(labels, "Created namespaces should have labels");
            assertEquals("namespace-protection", labels.get("test-type"));
            assertEquals("kubetest-junit", labels.get("framework"));

            Map<String, String> annotations = namespace.getMetadata().getAnnotations();
            assertNotNull(annotations, "Created namespaces should have annotations");
            assertEquals("Test demonstrating namespace protection",
                annotations.get("description"));
        }
    }

    @Test
    void testNamespaceStatus() {
        // All namespaces should be active and ready
        for (Namespace namespace : new Namespace[]{defaultNamespace, createdNamespace1, createdNamespace2}) {
            assertNotNull(namespace.getStatus(),
                "Namespace " + namespace.getMetadata().getName() + " should have status");
            assertEquals("Active", namespace.getStatus().getPhase(),
                "Namespace " + namespace.getMetadata().getName() + " should be active");
        }
    }
}
