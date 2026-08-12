/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class NamespaceTypeTest {

    private KubernetesClient kubernetesClient;
    private NamespaceType target;

    @BeforeEach
    void setup() {
        target = new NamespaceType(kubernetesClient.namespaces());
    }

    @Test
    void testMetadata() {
        assertEquals("Namespace", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        Namespace resource = new NamespaceBuilder()
            .withNewMetadata()
                .withName("test-ns")
            .endMetadata()
            .build();

        target.create(resource);

        Namespace created = kubernetesClient.namespaces().withName("test-ns").get();
        assertNotNull(created);

        target.replace(resource, ns -> ns.getMetadata().getLabels().put("k", "v"));

        Namespace updated = kubernetesClient.namespaces().withName("test-ns").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        Namespace deleted = kubernetesClient.namespaces().withName("test-ns").get();
        assertNull(deleted);
    }

    @Test
    void testCreateByName() {
        target.create("created-by-string");
        Namespace created = kubernetesClient.namespaces().withName("created-by-string").get();
        assertNotNull(created);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new Namespace()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new Namespace()));
    }
}
