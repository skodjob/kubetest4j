/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceManager(cleanResources = false) // override default behavior and do not clean resources
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class KubeResourceManagerIT extends AbstractIT {
    Namespace ns1 = new NamespaceBuilder().withNewMetadata().withName(nsName1).endMetadata().build();

    @BeforeEach
    void setupEach() {
        KubeResourceManager.get().createResourceWithWait(ns1);
    }

    @AfterEach
    void afterEach() {
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName2).get());
        KubeResourceManager.get().deleteResources();
    }

    @Test
    void createResource() {
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder().withNewMetadata().withName("test2").endMetadata().build());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName1).get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName2).get());
        KubeResourceManager.get().deleteResourceWithWait(ns1);
        assertTrue(isDeleteHandlerCalled.get());
    }
}
