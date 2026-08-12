/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class CustomResourceDefinitionTypeTest {

    private KubernetesClient kubernetesClient;
    private CustomResourceDefinitionType target;

    @BeforeEach
    void setup() {
        target = new CustomResourceDefinitionType(kubernetesClient.apiextensions().v1().customResourceDefinitions());
    }

    @Test
    void testMetadata() {
        assertEquals("CustomResourceDefinition", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        CustomResourceDefinition resource = new CustomResourceDefinitionBuilder()
            .withNewMetadata()
                .withName("tests.example.com")
            .endMetadata()
            .withNewSpec()
                .withGroup("example.com")
                .withScope("Namespaced")
                .withNewNames()
                    .withPlural("tests")
                    .withSingular("test")
                    .withKind("Test")
                .endNames()
                .addNewVersion()
                    .withName("v1")
                    .withServed(true)
                    .withStorage(true)
                .endVersion()
            .endSpec()
            .build();

        target.create(resource);

        CustomResourceDefinition created = kubernetesClient.apiextensions().v1().customResourceDefinitions()
            .withName("tests.example.com").get();
        assertNotNull(created);

        target.replace(resource, crd -> crd.getMetadata().getLabels().put("k", "v"));

        CustomResourceDefinition updated = kubernetesClient.apiextensions().v1().customResourceDefinitions()
            .withName("tests.example.com").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        CustomResourceDefinition deleted = kubernetesClient.apiextensions().v1().customResourceDefinitions()
            .withName("tests.example.com").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new CustomResourceDefinition()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new CustomResourceDefinition()));
    }
}
