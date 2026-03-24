/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.annotations.MethodNamespace;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example test demonstrating method namespace injection with @MethodNamespace.
 * <p>
 * This test shows how to:
 * - Use @MethodNamespace for per-test-method isolated namespaces
 * - Combine @MethodNamespace with @ClassNamespace (class-level namespace)
 * - Use @MethodNamespace as method parameter injection
 * - Use custom labels on method namespaces
 * - Create resources inside method namespaces
 */
@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
class PerMethodNamespaceIT {

    @ClassNamespace(name = "shared-ns")
    static Namespace sharedNs;

    @MethodNamespace(prefix = "worker")
    Namespace workerNs;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testMethodNamespaceIsCreated() {
        // Method namespace should be created and injected
        assertNotNull(workerNs, "Method namespace should be injected");
        assertNotNull(workerNs.getMetadata().getName(), "Method namespace should have a name");
        assertTrue(workerNs.getMetadata().getName().startsWith("worker-"),
            "Method namespace name should start with prefix 'worker-'");
    }

    @Test
    void testMethodNamespaceIsIsolated() {
        // Method namespace should be different from the class-level namespace
        assertNotNull(sharedNs, "Class namespace should be injected");
        assertNotNull(workerNs, "Method namespace should be injected");
        assertNotEquals(sharedNs.getMetadata().getName(), workerNs.getMetadata().getName(),
            "Method namespace should be different from class namespace");
    }

    @Test
    void testResourceCreationInMethodNamespace() {
        // Resources can be created inside the method namespace
        String nsName = workerNs.getMetadata().getName();

        resourceManager.createResourceWithWait(
            new ConfigMapBuilder()
                .withNewMetadata()
                .withName("dynamic-config")
                .withNamespace(nsName)
                .endMetadata()
                .addToData("key", "value")
                .build()
        );

        assertNotNull(resourceManager.kubeClient().getClient()
            .configMaps()
            .inNamespace(nsName)
            .withName("dynamic-config")
            .get());
    }

    @Test
    void testMethodNamespaceAsParameter(
            @MethodNamespace(prefix = "param") Namespace paramNs) {
        // Method namespace can be injected as method parameter
        assertNotNull(paramNs, "Parameter method namespace should be injected");
        assertTrue(paramNs.getMetadata().getName().startsWith("param-"),
            "Parameter namespace name should start with prefix 'param-'");

        // It should be different from the field-injected method namespace
        assertNotEquals(workerNs.getMetadata().getName(), paramNs.getMetadata().getName(),
            "Field and parameter method namespaces should be different");
    }

    @Test
    void testMethodNamespaceWithLabels(
            @MethodNamespace(prefix = "labeled",
                labels = {"team=qe", "env=test"},
                annotations = {"description=test namespace"}) Namespace labeledNs) {
        // Method namespace should have custom labels
        assertNotNull(labeledNs, "Labeled namespace should be injected");
        assertNotNull(labeledNs.getMetadata().getLabels());
        assertEquals("qe", labeledNs.getMetadata().getLabels().get("team"));
        assertEquals("test", labeledNs.getMetadata().getLabels().get("env"));
    }

    @Test
    void testMethodNamespaceIsActive() {
        // Method namespace should be in Active phase
        assertNotNull(workerNs.getStatus());
        assertEquals("Active", workerNs.getStatus().getPhase());
    }
}
