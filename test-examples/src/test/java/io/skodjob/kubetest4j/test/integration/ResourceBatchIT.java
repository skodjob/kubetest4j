/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for the ResourceBatch feature.
 * Verifies that resources created in separate calls form separate batches
 * and are deleted in correct batch-LIFO order.
 */
final class ResourceBatchIT extends AbstractIT {

    @Test
    void testImplicitBatchCreationAndDeletion() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("batch-it-ns").endMetadata().build();
        ConfigMap cm = new ConfigMapBuilder().withNewMetadata()
            .withName("batch-it-cm").withNamespace("batch-it-ns").endMetadata()
            .addToData("key", "value").build();
        ServiceAccount sa = new ServiceAccountBuilder().withNewMetadata()
            .withName("batch-it-sa").withNamespace("batch-it-ns").endMetadata().build();

        // Batch 1: infrastructure (namespace)
        KubeResourceManager.get().createResourceWithWait(ns);

        // Batch 2: workloads (ConfigMap + ServiceAccount together)
        KubeResourceManager.get().createResourceWithWait(cm, sa);

        // Verify all resources exist
        List<HasMetadata> tracked = KubeResourceManager.get().getCurrentResources();
        assertEquals(3, tracked.size(), "Should track 3 resources across 2 batches");

        assertNotNull(KubeResourceManager.get().kubeClient().getClient()
            .namespaces().withName("batch-it-ns").get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient()
            .configMaps().inNamespace("batch-it-ns").withName("batch-it-cm").get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient()
            .serviceAccounts().inNamespace("batch-it-ns").withName("batch-it-sa").get());

        // Delete — batch 2 (cm + sa) first, then batch 1 (ns)
        KubeResourceManager.get().deleteResources();

        // Verify all cleaned up
        assertNull(KubeResourceManager.get().kubeClient().getClient()
            .configMaps().inNamespace("batch-it-ns").withName("batch-it-cm").get());
        assertNull(KubeResourceManager.get().kubeClient().getClient()
            .serviceAccounts().inNamespace("batch-it-ns").withName("batch-it-sa").get());
        assertEquals(0, KubeResourceManager.get().getCurrentResources().size());
    }

    @Test
    void testExplicitBatchGrouping() throws Exception {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("explicit-batch-ns").endMetadata().build();

        // Batch 1: namespace
        KubeResourceManager.get().createResourceWithWait(ns);

        // Batch 2: explicit batch grouping multiple create calls
        try (AutoCloseable ignored = KubeResourceManager.get().openBatch()) {
            KubeResourceManager.get().createResourceWithWait(
                new ConfigMapBuilder().withNewMetadata()
                    .withName("eb-cm-1").withNamespace("explicit-batch-ns").endMetadata()
                    .addToData("k1", "v1").build());
            KubeResourceManager.get().createResourceWithWait(
                new ConfigMapBuilder().withNewMetadata()
                    .withName("eb-cm-2").withNamespace("explicit-batch-ns").endMetadata()
                    .addToData("k2", "v2").build());
        }

        // All 3 resources tracked
        List<HasMetadata> tracked = KubeResourceManager.get().getCurrentResources();
        assertEquals(3, tracked.size(), "Should track ns + 2 CMs");

        // Delete — the explicit batch (both CMs) is deleted first, then the namespace
        KubeResourceManager.get().deleteResources();

        assertEquals(0, KubeResourceManager.get().getCurrentResources().size());
    }
}
