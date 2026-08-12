/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class ValidatingWebhookConfigurationTypeTest {

    private KubernetesClient kubernetesClient;
    private ValidatingWebhookConfigurationType target;

    @BeforeEach
    void setup() {
        target = new ValidatingWebhookConfigurationType(kubernetesClient.admissionRegistration()
            .v1().validatingWebhookConfigurations());
    }

    @Test
    void testMetadata() {
        assertEquals("ValidatingWebhookConfiguration", target.getKind());
        assertNotNull(target.getTimeoutForResourceReadiness());
        assertNotNull(target.getClient());
    }

    @Test
    void testCrudOperations() {
        ValidatingWebhookConfiguration resource = new ValidatingWebhookConfigurationBuilder()
            .withNewMetadata()
                .withName("test-vwc")
            .endMetadata()
            .build();

        target.create(resource);

        ValidatingWebhookConfiguration created = kubernetesClient.admissionRegistration()
            .v1().validatingWebhookConfigurations().withName("test-vwc").get();
        assertNotNull(created);

        target.replace(resource, vwc -> vwc.getMetadata().getLabels().put("k", "v"));

        ValidatingWebhookConfiguration updated = kubernetesClient.admissionRegistration()
            .v1().validatingWebhookConfigurations().withName("test-vwc").get();
        assertEquals("v", updated.getMetadata().getLabels().get("k"));

        target.delete(resource);

        ValidatingWebhookConfiguration deleted = kubernetesClient.admissionRegistration()
            .v1().validatingWebhookConfigurations().withName("test-vwc").get();
        assertNull(deleted);
    }

    @Test
    void testIsReady() {
        assertTrue(target.isReady(new ValidatingWebhookConfiguration()));
        assertFalse(target.isReady(null));
    }

    @Test
    void testIsDeleted() {
        assertTrue(target.isDeleted(null));
        assertFalse(target.isDeleted(new ValidatingWebhookConfiguration()));
    }
}
