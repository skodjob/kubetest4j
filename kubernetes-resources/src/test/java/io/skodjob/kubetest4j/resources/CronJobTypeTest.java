/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class CronJobTypeTest {

    private KubernetesClient kubernetesClient;
    private CronJobType target;

    @BeforeEach
    void setup() {
        target = new CronJobType(kubernetesClient.batch().v1().cronjobs());
    }

    @Test
    void testMetadata() {
        assertEquals("CronJob", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        CronJob resource = new CronJobBuilder()
            .withNewMetadata()
                .withName("test-cronjob")
                .withNamespace("default")
            .endMetadata()
            .withNewSpec()
                .withSchedule("*/5 * * * *")
            .endSpec()
            .build();

        target.create(resource);

        CronJob created = kubernetesClient.batch().v1().cronjobs().inNamespace("default")
            .withName("test-cronjob").get();
        assertNotNull(created);

        target.replace(resource, cj -> cj.getSpec().setSchedule("*/10 * * * *"));

        CronJob updated = kubernetesClient.batch().v1().cronjobs().inNamespace("default")
            .withName("test-cronjob").get();
        assertEquals("*/10 * * * *", updated.getSpec().getSchedule());

        target.delete(resource);

        CronJob deleted = kubernetesClient.batch().v1().cronjobs().inNamespace("default")
            .withName("test-cronjob").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new CronJob()));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new CronJob()));
    }
}
