/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class IngressTypeTest {

    private KubernetesClient kubernetesClient;
    private IngressType target;

    @BeforeEach
    void setup() {
        KubeResourceManager.get().kubeClient().testReconnect(kubernetesClient.getConfiguration());
        target = new IngressType();
    }

    @Test
    void testConstructorsAndMetadata() {
        IngressType custom = new IngressType(kubernetesClient.network().v1().ingresses());
        assertEquals("Ingress", target.getKind());
        assertEquals("Ingress", custom.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        Ingress resource = new IngressBuilder()
            .withNewMetadata()
                .withName("test-ingress")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        Ingress created = kubernetesClient.network().v1().ingresses()
            .inNamespace("default").withName("test-ingress").get();
        assertNotNull(created);

        resource.getMetadata().getLabels().put("k1", "v1");
        target.update(resource);

        Ingress updated = kubernetesClient.network().v1().ingresses()
            .inNamespace("default").withName("test-ingress").get();
        assertEquals("v1", updated.getMetadata().getLabels().get("k1"));

        target.replace(resource, ing -> ing.getMetadata().getLabels().put("k2", "v2"));

        Ingress replaced = kubernetesClient.network().v1().ingresses()
            .inNamespace("default").withName("test-ingress").get();
        assertEquals("v2", replaced.getMetadata().getLabels().get("k2"));

        target.delete(resource);

        Ingress deleted = kubernetesClient.network().v1().ingresses()
            .inNamespace("default").withName("test-ingress").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new Ingress()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new Ingress()));
    }
}
