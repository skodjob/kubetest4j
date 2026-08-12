/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class RoleBindingTypeTest {

    private KubernetesClient kubernetesClient;
    private RoleBindingType target;

    @BeforeEach
    void setup() {
        target = new RoleBindingType(kubernetesClient.rbac().roleBindings());
    }

    @Test
    void testMetadata() {
        assertEquals("RoleBinding", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        RoleBinding resource = new RoleBindingBuilder()
            .withNewMetadata()
                .withName("test-rb")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        RoleBinding created = kubernetesClient.rbac().roleBindings().inNamespace("default").withName("test-rb").get();
        assertNotNull(created);

        target.replace(resource, rb -> rb.getMetadata().getLabels().put("k", "v"));

        RoleBinding updated = kubernetesClient.rbac().roleBindings().inNamespace("default").withName("test-rb").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        RoleBinding deleted = kubernetesClient.rbac().roleBindings().inNamespace("default").withName("test-rb").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new RoleBinding()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new RoleBinding()));
    }
}
