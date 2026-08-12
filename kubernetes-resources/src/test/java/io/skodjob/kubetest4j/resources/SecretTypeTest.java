/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class SecretTypeTest {

    private KubernetesClient kubernetesClient;
    private SecretType target;

    @BeforeEach
    void setup() {
        target = new SecretType(kubernetesClient.secrets());
    }

    @Test
    void testMetadata() {
        assertEquals("Secret", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        Secret resource = new SecretBuilder()
            .withNewMetadata()
                .withName("test-secret")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        Secret created = kubernetesClient.secrets().inNamespace("default").withName("test-secret").get();
        assertNotNull(created);

        target.replace(resource, s -> s.getMetadata().getLabels().put("k", "v"));

        Secret updated = kubernetesClient.secrets().inNamespace("default").withName("test-secret").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        Secret deleted = kubernetesClient.secrets().inNamespace("default").withName("test-secret").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new Secret()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new Secret()));
    }
}
