/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class ConfigMapTypeTest {

    private KubernetesClient kubernetesClient;
    private ConfigMapType target;

    @BeforeEach
    void setup() {
        target = new ConfigMapType(kubernetesClient.configMaps());
    }

    @Test
    void testMetadata() {
        assertEquals("ConfigMap", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        ConfigMap resource = new ConfigMapBuilder()
            .withNewMetadata()
                .withName("test-cm")
                .withNamespace("default")
            .endMetadata()
            .addToData("k1", "v1")
            .build();

        target.create(resource);

        ConfigMap created = kubernetesClient.configMaps().inNamespace("default").withName("test-cm").get();
        assertNotNull(created);

        target.replace(resource, cm -> cm.getData().put("k2", "v2"));

        ConfigMap updated = kubernetesClient.configMaps().inNamespace("default").withName("test-cm").get();
        assertEquals("v2", updated.getData().get("k2"));

        target.delete(resource);

        ConfigMap deleted = kubernetesClient.configMaps().inNamespace("default").withName("test-cm").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new ConfigMap()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new ConfigMap()));
    }
}
