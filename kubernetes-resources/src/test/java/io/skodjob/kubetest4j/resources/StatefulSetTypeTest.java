/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class StatefulSetTypeTest {

    private KubernetesClient kubernetesClient;
    private StatefulSetType target;

    @BeforeEach
    void setup() {
        target = new StatefulSetType(kubernetesClient.apps().statefulSets());
    }

    @Test
    void testMetadata() {
        assertEquals("StatefulSet", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        StatefulSet resource = new StatefulSetBuilder()
            .withNewMetadata()
                .withName("test-sts")
                .withNamespace("default")
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
            .endSpec()
            .build();

        target.create(resource);

        StatefulSet created = kubernetesClient.apps().statefulSets().inNamespace("default").withName("test-sts").get();
        assertNotNull(created);

        target.replace(resource, sts -> sts.getSpec().setReplicas(2));

        StatefulSet updated = kubernetesClient.apps().statefulSets().inNamespace("default").withName("test-sts").get();
        assertEquals(2, updated.getSpec().getReplicas());

        target.delete(resource);

        StatefulSet deleted = kubernetesClient.apps().statefulSets().inNamespace("default").withName("test-sts").get();
        assertNull(deleted);
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new StatefulSet()));
    }
}
