/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.MethodNamespace;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.clients.KubeClient;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A {@link MethodNamespace} in a non-default kube context is deleted after its test method, and a client for
 * that context can be injected without any namespace annotation creating the context first.
 */
@KubernetesTest
@TestVisualSeparator
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MethodNamespaceKubeContextIT {
    private static String createdName;

    // No @ClassNamespace for "staging" in this class: the client must be created on demand.
    @InjectKubeClient(kubeContext = "staging")
    KubeClient staging;

    @Test
    @Order(1)
    void createsMethodNamespaceInOtherContext(
        @MethodNamespace(prefix = "mnkc", kubeContext = "staging") Namespace ns) {
        createdName = ns.getMetadata().getName();
        assertNotNull(staging.getClient().namespaces().withName(createdName).get());
    }

    @Test
    @Order(2)
    void previousMethodNamespaceWasDeleted() {
        assertNotNull(createdName, "the first test did not run");
        assertNull(staging.getClient().namespaces().withName(createdName).get());
    }
}
