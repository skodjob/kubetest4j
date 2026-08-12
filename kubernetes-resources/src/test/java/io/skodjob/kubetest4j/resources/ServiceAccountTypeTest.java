/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class ServiceAccountTypeTest {

    private KubernetesClient kubernetesClient;
    private ServiceAccountType target;

    @BeforeEach
    void setup() {
        target = new ServiceAccountType(kubernetesClient.serviceAccounts());
    }

    @Test
    void testMetadata() {
        assertEquals("ServiceAccount", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        ServiceAccount resource = new ServiceAccountBuilder()
            .withNewMetadata()
                .withName("test-sa")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        ServiceAccount created = kubernetesClient.serviceAccounts().inNamespace("default").withName("test-sa").get();
        assertNotNull(created);

        target.replace(resource, sa -> sa.getMetadata().getLabels().put("k", "v"));

        ServiceAccount updated = kubernetesClient.serviceAccounts().inNamespace("default").withName("test-sa").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        ServiceAccount deleted = kubernetesClient.serviceAccounts().inNamespace("default").withName("test-sa").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new ServiceAccount()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new ServiceAccount()));
    }
}
