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
 * Example test demonstrating namespace protection functionality.
 * This test shows how the framework:
 * - Protects existing namespaces from deletion (like 'default', 'kube-system')
 * - Only deletes namespaces that were created by the test
 * - Safely mixes existing and test-created namespaces
 */
@RequiresKubernetes
@KubernetesTest(
    // Mix of existing namespaces (default) and test-created namespaces
    namespaces = {"default", "protection-test-new-1", "protection-test-new-2"},
    cleanup = CleanupStrategy.AUTOMATIC,
    namespaceLabels = {"test-type=namespace-protection", "framework=kubetest-junit"},
    namespaceAnnotations = {"description=Test demonstrating namespace protection"}
)
class NamespaceProtectionIT {

    @InjectNamespaces
    Map<String, Namespace> allNamespaces;

    @InjectNamespace(name = "default")
    Namespace defaultNamespace; // This should NEVER be deleted

    @InjectNamespace(name = "protection-test-new-1")
    Namespace createdNamespace1; // This will be deleted after test

    @InjectNamespace(name = "protection-test-new-2")
    Namespace createdNamespace2; // This will be deleted after test

    @Test
    void testMixedNamespaceUsage() {
        // Verify all namespaces are available
        assertNotNull(allNamespaces, "All namespaces should be injected");
        assertEquals(3, allNamespaces.size(), "Should have 3 total namespaces");

        // Verify existing namespace (default) is available
        assertTrue(allNamespaces.containsKey("default"));
        assertNotNull(defaultNamespace);
        assertEquals("default", defaultNamespace.getMetadata().getName());

        // Verify test-created namespaces are available
        assertTrue(allNamespaces.containsKey("protection-test-new-1"));
        assertTrue(allNamespaces.containsKey("protection-test-new-2"));

        assertNotNull(createdNamespace1);
        assertNotNull(createdNamespace2);
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
            // (only to namespaces we create)
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
    void testParameterInjection(
        @InjectNamespace(name = "default") Namespace paramDefault,
        @InjectNamespace(name = "protection-test-new-1") Namespace paramCreated) {

        // Parameter injection should work for both existing and created namespaces
        assertNotNull(paramDefault);
        assertNotNull(paramCreated);

        assertEquals("default", paramDefault.getMetadata().getName());
        assertEquals("protection-test-new-1", paramCreated.getMetadata().getName());

        // Should match field injection
        assertEquals(defaultNamespace.getMetadata().getName(),
            paramDefault.getMetadata().getName());
        assertEquals(createdNamespace1.getMetadata().getName(),
            paramCreated.getMetadata().getName());
    }

    @Test
    void testNamespaceStatus() {
        // All namespaces should be active and ready
        for (String namespaceName : allNamespaces.keySet()) {
            Namespace namespace = allNamespaces.get(namespaceName);
            assertNotNull(namespace.getStatus(),
                "Namespace " + namespaceName + " should have status");
            assertEquals("Active", namespace.getStatus().getPhase(),
                "Namespace " + namespaceName + " should be active");
        }
    }
}