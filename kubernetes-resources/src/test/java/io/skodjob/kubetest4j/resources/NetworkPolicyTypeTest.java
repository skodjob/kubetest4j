/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class NetworkPolicyTypeTest {

    private KubernetesClient kubernetesClient;
    private NetworkPolicyType target;

    @BeforeEach
    void setup() {
        target = new NetworkPolicyType(kubernetesClient.network().networkPolicies());
    }

    @Test
    void testMetadata() {
        assertEquals("NetworkPolicy", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        NetworkPolicy resource = new NetworkPolicyBuilder()
            .withNewMetadata()
                .withName("test-np")
                .withNamespace("default")
            .endMetadata()
            .build();

        target.create(resource);

        NetworkPolicy created = kubernetesClient.network().networkPolicies()
            .inNamespace("default").withName("test-np").get();
        assertNotNull(created);

        target.replace(resource, np -> np.getMetadata().getLabels().put("k", "v"));

        NetworkPolicy updated = kubernetesClient.network().networkPolicies()
            .inNamespace("default").withName("test-np").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        NetworkPolicy deleted = kubernetesClient.network().networkPolicies()
            .inNamespace("default").withName("test-np").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new NetworkPolicy()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new NetworkPolicy()));
    }
}
