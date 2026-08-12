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
        KubeResourceManager.get().kubeClient().testReconnect(kubernetesClient.getConfiguration());
        target = new DaemonSetType();
    }

    @Test
    void testConstructorsAndMetadata() {
        DaemonSetType custom = new DaemonSetType(kubernetesClient.apps().daemonSets());
        assertEquals("DaemonSet", target.getKind());
        assertEquals("DaemonSet", custom.getKind());
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

        resource.getMetadata().getLabels().put("k1", "v1");
        target.update(resource);

        DaemonSet updated = kubernetesClient.apps().daemonSets().inNamespace("default").withName("test-ds").get();
        assertEquals("v1", updated.getMetadata().getLabels().get("k1"));

        target.replace(resource, ds -> ds.getMetadata().getLabels().put("k2", "v2"));

        DaemonSet replaced = kubernetesClient.apps().daemonSets().inNamespace("default").withName("test-ds").get();
        assertEquals("v2", replaced.getMetadata().getLabels().get("k2"));

        target.delete(resource);

        DaemonSet deleted = kubernetesClient.apps().daemonSets().inNamespace("default").withName("test-ds").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        DaemonSet resource = new DaemonSetBuilder()
            .withNewMetadata()
                .withName("test-ds")
                .withNamespace("default")
            .endMetadata()
            .build();
        assertFalse(target.isReady(resource));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new DaemonSet()));
    }
}
