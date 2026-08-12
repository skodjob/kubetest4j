/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class LeaseTypeTest {

    private KubernetesClient kubernetesClient;
    private LeaseType target;

    @BeforeEach
    void setup() {
        target = new LeaseType(kubernetesClient.leases());
    }

    @Test
    void testMetadata() {
        assertEquals("Lease", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        Lease resource = new LeaseBuilder()
            .withNewMetadata()
                .withName("test-lease")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        Lease created = kubernetesClient.leases().inNamespace("default").withName("test-lease").get();
        assertNotNull(created);

        target.replace(resource, lease -> lease.getMetadata().getLabels().put("k", "v"));

        Lease updated = kubernetesClient.leases().inNamespace("default").withName("test-lease").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        Lease deleted = kubernetesClient.leases().inNamespace("default").withName("test-lease").get();
        assertNull(deleted);
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new Lease()));
    }
}
