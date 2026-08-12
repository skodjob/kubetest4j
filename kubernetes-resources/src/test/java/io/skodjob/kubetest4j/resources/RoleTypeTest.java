/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class RoleTypeTest {

    private KubernetesClient kubernetesClient;
    private RoleType target;

    @BeforeEach
    void setup() {
        target = new RoleType(kubernetesClient.rbac().roles());
    }

    @Test
    void testMetadata() {
        assertEquals("Role", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        Role resource = new RoleBuilder()
            .withNewMetadata()
                .withName("test-role")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        Role created = kubernetesClient.rbac().roles().inNamespace("default").withName("test-role").get();
        assertNotNull(created);

        target.replace(resource, role -> role.getMetadata().getLabels().put("k", "v"));

        Role updated = kubernetesClient.rbac().roles().inNamespace("default").withName("test-role").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        Role deleted = kubernetesClient.rbac().roles().inNamespace("default").withName("test-role").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new Role()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new Role()));
    }
}
