/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
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
        target = new PersistentVolumeClaimType(kubernetesClient.persistentVolumeClaims());
    }

    @Test
    void testMetadata() {
        assertEquals("PersistentVolumeClaim", target.getKind());
        assertNotNull(target.getClient());
    }

    @Test
    void testIsReadyBound() {
        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
                .withName("test-pvc")
                .withNamespace("default")
            .endMetadata()
            .withNewStatus()
                .withPhase("Bound")
            .endStatus()
            .build();

        assertTrue(target.isReady(pvc));
    }

    @Test
    void testIsReadyPending() {
        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
                .withName("test-pvc")
                .withNamespace("default")
            .endMetadata()
            .withNewStatus()
                .withPhase("Pending")
            .endStatus()
            .build();

        assertFalse(target.isReady(pvc));
    }

    @Test
    void testIsReadyNull() {
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new PersistentVolumeClaim()));
    }

    @Test
    void testCrudOperations() {
        PersistentVolumeClaim resource = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
                .withName("test-pvc")
                .withNamespace("default")
            .endMetadata()
            .withNewSpec()
                .withNewResources()
                    .addToRequests("storage", new io.fabric8.kubernetes.api.model.Quantity("1Gi"))
                .endResources()
            .endSpec()
            .build();

        target.create(resource);

        PersistentVolumeClaim created = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc").get();
        assertNotNull(created);

        target.replace(resource, pvc -> pvc.getSpec().getResources()
            .setRequests(java.util.Map.of("storage",
                new io.fabric8.kubernetes.api.model.Quantity("2Gi"))));

        PersistentVolumeClaim updated = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc").get();
        assertEquals("2Gi", updated.getSpec().getResources().getRequests().get("storage").toString());

        target.delete(resource);

        PersistentVolumeClaim deleted = kubernetesClient.persistentVolumeClaims()
            .inNamespace("default").withName("test-pvc").get();
        assertNull(deleted);
    }
}
