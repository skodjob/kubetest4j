/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class DaemonSetTypeTest {

    private KubernetesClient kubernetesClient;
    private DaemonSetType target;

    @BeforeEach
    void setup() {
        target = new DaemonSetType(kubernetesClient.apps().daemonSets());
    }

    @Test
    void testMetadata() {
        assertEquals("DaemonSet", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        DaemonSet resource = new DaemonSetBuilder()
            .withNewMetadata()
                .withName("test-ds")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        DaemonSet created = kubernetesClient.apps().daemonSets().inNamespace("default").withName("test-ds").get();
        assertNotNull(created);

        target.replace(resource, ds -> ds.getMetadata().getLabels().put("key", "value"));

        DaemonSet updated = kubernetesClient.apps().daemonSets().inNamespace("default").withName("test-ds").get();
        assertEquals("value", updated.getMetadata().getLabels().get("key"));

        target.delete(resource);

        DaemonSet deleted = kubernetesClient.apps().daemonSets().inNamespace("default").withName("test-ds").get();
        assertNull(deleted);
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new DaemonSet()));
    }
}
