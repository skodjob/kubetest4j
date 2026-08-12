/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class ClusterRoleBindingTypeTest {

    private KubernetesClient kubernetesClient;
    private ClusterRoleBindingType target;

    @BeforeEach
    void setup() {
        target = new ClusterRoleBindingType(kubernetesClient.rbac().clusterRoleBindings());
    }

    @Test
    void testMetadata() {
        assertEquals("ClusterRoleBinding", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        ClusterRoleBinding resource = new ClusterRoleBindingBuilder()
            .withNewMetadata()
                .withName("test-crb")
            .endMetadata()
            .build();

        target.create(resource);

        ClusterRoleBinding created = kubernetesClient.rbac().clusterRoleBindings().withName("test-crb").get();
        assertNotNull(created);

        target.replace(resource, crb -> crb.getMetadata().getLabels().put("k", "v"));

        ClusterRoleBinding updated = kubernetesClient.rbac().clusterRoleBindings().withName("test-crb").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        ClusterRoleBinding deleted = kubernetesClient.rbac().clusterRoleBindings().withName("test-crb").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new ClusterRoleBinding()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new ClusterRoleBinding()));
    }
}
