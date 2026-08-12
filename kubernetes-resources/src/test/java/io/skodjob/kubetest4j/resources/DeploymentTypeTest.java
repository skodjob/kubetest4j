/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class DeploymentTypeTest {

    private KubernetesClient kubernetesClient;
    private DeploymentType target;

    @BeforeEach
    void setup() {
        target = new DeploymentType(kubernetesClient.apps().deployments());
    }

    @Test
    void testMetadata() {
        assertEquals("Deployment", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        Deployment resource = new DeploymentBuilder()
            .withNewMetadata()
                .withName("test-dep")
                .withNamespace("default")
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
            .endSpec()
            .build();

        target.create(resource);

        Deployment created = kubernetesClient.apps().deployments().inNamespace("default").withName("test-dep").get();
        assertNotNull(created);

        target.replace(resource, dep -> dep.getSpec().setReplicas(2));

        Deployment updated = kubernetesClient.apps().deployments().inNamespace("default").withName("test-dep").get();
        assertEquals(2, updated.getSpec().getReplicas());

        target.delete(resource);

        Deployment deleted = kubernetesClient.apps().deployments().inNamespace("default").withName("test-dep").get();
        assertNull(deleted);
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new Deployment()));
    }
}
