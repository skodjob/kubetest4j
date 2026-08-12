/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class ServiceTypeTest {

    private KubernetesClient kubernetesClient;
    private ServiceType target;

    @BeforeEach
    void setup() {
        target = new ServiceType(kubernetesClient.services());
    }

    @Test
    void testMetadata() {
        assertEquals("Service", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        Service resource = new ServiceBuilder()
            .withNewMetadata()
                .withName("test-svc")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        Service created = kubernetesClient.services().inNamespace("default").withName("test-svc").get();
        assertNotNull(created);

        target.replace(resource, svc -> svc.getMetadata().getLabels().put("k", "v"));

        Service updated = kubernetesClient.services().inNamespace("default").withName("test-svc").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        Service deleted = kubernetesClient.services().inNamespace("default").withName("test-svc").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new Service()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new Service()));
    }
}
