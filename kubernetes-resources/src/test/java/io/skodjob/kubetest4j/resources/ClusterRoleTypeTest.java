/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class ClusterRoleTypeTest {

    private KubernetesClient kubernetesClient;
    private ClusterRoleType target;

    @BeforeEach
    void setup() {
        target = new ClusterRoleType(kubernetesClient.rbac().clusterRoles());
    }

    @Test
    void testMetadata() {
        assertEquals("ClusterRole", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        ClusterRole resource = new ClusterRoleBuilder()
            .withNewMetadata()
                .withName("test-cr")
            .endMetadata()
            .build();

        target.create(resource);

        ClusterRole created = kubernetesClient.rbac().clusterRoles().withName("test-cr").get();
        assertNotNull(created);

        target.replace(resource, cr -> cr.getMetadata().getLabels().put("k", "v"));

        ClusterRole updated = kubernetesClient.rbac().clusterRoles().withName("test-cr").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        ClusterRole deleted = kubernetesClient.rbac().clusterRoles().withName("test-cr").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new ClusterRole()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new ClusterRole()));
    }
}
