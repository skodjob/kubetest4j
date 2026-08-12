/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class HorizontalPodAutoscalerTypeTest {

    private KubernetesClient kubernetesClient;
    private HorizontalPodAutoscalerType target;

    @BeforeEach
    void setup() {
        target = new HorizontalPodAutoscalerType(kubernetesClient.autoscaling().v2().horizontalPodAutoscalers());
    }

    @Test
    void testMetadata() {
        assertEquals("HorizontalPodAutoscaler", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        HorizontalPodAutoscaler resource = new HorizontalPodAutoscalerBuilder()
            .withNewMetadata()
                .withName("test-hpa")
                .withNamespace("default")
            .endMetadata()
            .withNewSpec()
                .withMaxReplicas(5)
            .endSpec()
            .build();

        target.create(resource);

        HorizontalPodAutoscaler created = kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
            .inNamespace("default").withName("test-hpa").get();
        assertNotNull(created);

        target.replace(resource, hpa -> hpa.getSpec().setMaxReplicas(10));

        HorizontalPodAutoscaler updated = kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
            .inNamespace("default").withName("test-hpa").get();
        assertEquals(10, updated.getSpec().getMaxReplicas());

        target.delete(resource);

        HorizontalPodAutoscaler deleted = kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
            .inNamespace("default").withName("test-hpa").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new HorizontalPodAutoscaler()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new HorizontalPodAutoscaler()));
    }
}
