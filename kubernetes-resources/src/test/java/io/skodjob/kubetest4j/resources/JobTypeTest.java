/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class JobTypeTest {

    private KubernetesClient kubernetesClient;
    private JobType target;

    @BeforeEach
    void setup() {
        target = new JobType(kubernetesClient.batch().v1().jobs());
    }

    @Test
    void testMetadata() {
        assertEquals("Job", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        Job resource = new JobBuilder()
            .withNewMetadata()
                .withName("test-job")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        Job created = kubernetesClient.batch().v1().jobs().inNamespace("default").withName("test-job").get();
        assertNotNull(created);

        target.replace(resource, job -> job.getMetadata().getLabels().put("k", "v"));

        Job updated = kubernetesClient.batch().v1().jobs().inNamespace("default").withName("test-job").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        Job deleted = kubernetesClient.batch().v1().jobs().inNamespace("default").withName("test-job").get();
        assertNull(deleted);
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new Job()));
    }
}
