/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class PersistentVolumeClaimTypeTest {

    private KubernetesClient kubernetesClient;
    private PersistentVolumeClaimType target;

    @BeforeEach
    void setup() {
        KubeResourceManager.get().kubeClient().testReconnect(kubernetesClient.getConfiguration());
        target = new PersistentVolumeClaimType();
    }

    @Test
    void testConstructorsAndMetadata() {
        PersistentVolumeClaimType custom = new PersistentVolumeClaimType(kubernetesClient.persistentVolumeClaims());
        assertEquals("PersistentVolumeClaim", target.getKind());
        assertEquals("PersistentVolumeClaim", custom.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        PersistentVolumeClaim resource = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
                .withName("test-pvc")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        PersistentVolumeClaim created = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc").get();
        assertNotNull(created);

        resource.getMetadata().getLabels().put("k1", "v1");
        target.update(resource);

        PersistentVolumeClaim updated = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc").get();
        assertEquals("v1", updated.getMetadata().getLabels().get("k1"));

        target.replace(resource, pvc -> pvc.getMetadata().getLabels().put("k2", "v2"));

        PersistentVolumeClaim replaced = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc").get();
        assertEquals("v2", replaced.getMetadata().getLabels().get("k2"));

        target.delete(resource);

        PersistentVolumeClaim deleted = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc").get();
        assertNull(deleted);
    }

    @Test
    void testIsReadyBound() {
        PersistentVolumeClaim resource = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
                .withName("test-pvc")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        PersistentVolumeClaim created = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc").get();
        created.setStatus(new PersistentVolumeClaimStatusBuilder().withPhase("Bound").build());
        target.update(created);

        assertTrue(target.isReady(created));
    }

    @Test
    void testIsReadyPending() {
        PersistentVolumeClaim resource = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
                .withName("test-pvc-pending")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        PersistentVolumeClaim created = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc-pending").get();
        created.setStatus(new PersistentVolumeClaimStatusBuilder().withPhase("Pending").build());
        target.update(created);

        assertFalse(target.isReady(created));
    }

    @Test
    void testIsReadyNullAndEmpty() {
        assertFalse(target.isReady(null));
        assertFalse(target.isReady(new PersistentVolumeClaim()));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new PersistentVolumeClaim()));
    }
}
