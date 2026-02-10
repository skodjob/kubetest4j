/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.clients.KubeClusterException;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ResourceManager(asyncDeletion = true)
final class KubeResourceManagerCleanerIT extends AbstractIT {

    @BeforeAll
    void setupAll() {
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder().withNewMetadata().withName(nsName1).endMetadata().build());
    }

    @BeforeEach
    void setupEach() {
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder().withNewMetadata().withName(nsName2).endMetadata().build());
    }

    @AfterAll
    void afterAll() {
        assertNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName2).get());
        assertNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName3).get());
        assertTrue(isDeleteHandlerCalled.get());
    }

    @Test
    void createResource() {
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName(nsName3).endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);
        assertTrue(isCreateHandlerCalled.get());
        KubeResourceManager.get().createOrUpdateResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName3).get()
            .getMetadata().getLabels().get("test-label"));
        KubeResourceManager.get().printAllResources(Level.INFO);
    }

    @Test
    void testKubeClientNamespacesExists() {
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName1).get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName2).get());
        assertNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName3).get());
    }

    @Test
    void testKubeCmdClientNamespacesExists() {
        assertNotNull(KubeResourceManager.get().kubeCmdClient().get("namespace", nsName1));
        assertNotNull(KubeResourceManager.get().kubeCmdClient().get("namespace", nsName2));
        assertThrows(KubeClusterException.class, () ->
            KubeResourceManager.get().kubeCmdClient().get("namespace", nsName3));
    }

    @Test
    void testCreateMultipleResourcesAsync() throws IOException {
        List<HasMetadata> resources = KubeResourceManager.get().kubeClient()
            .readResourcesFromFile(getClass().getClassLoader().getResourceAsStream("metrics-example.yaml"));
        KubeResourceManager.get().createOrUpdateResourceAsyncWait(resources.toArray(new HasMetadata[0]));
        assertTrue(KubeResourceManager.get().kubeClient().namespaceExists("metrics-test"));
    }

    @Test
    void testMultiClusterAccess() throws Exception {
        try (var ctx = KubeResourceManager.get().useContext(KubeTestConstants.DEFAULT_CONTEXT_NAME)) {
            KubeResourceManager.get().createResourceWithWait(
                new NamespaceBuilder().withNewMetadata().withName("kornys").endMetadata().build());
        }
        assertTrue(KubeResourceManager.get().kubeClient().namespaceExists("kornys"));
    }

    @Test
    void testKubeCmdWithTimeout() {
        assertTrue(KubeResourceManager.get().kubeCmdClient().inNamespace("default").withTimeout(10_000)
            .exec("get", "secrets").exitStatus());
    }
}
