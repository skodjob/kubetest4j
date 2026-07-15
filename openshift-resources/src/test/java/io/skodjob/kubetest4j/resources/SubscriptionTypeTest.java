/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionBuilder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionTypeTest {

    SubscriptionType target;

    @BeforeEach
    void setup() {
        // no real client needed for readiness tests
        target = new SubscriptionType(null);
    }

    @Test
    void testReadyTrue() {
        boolean ready = target.isReady(new SubscriptionBuilder()
                .withNewStatus()
                    .addNewCondition()
                        .withType("CatalogSourcesUnhealthy")
                        .withStatus("False")
                    .endCondition()
                .endStatus()
                .build());

        assertTrue(ready, () -> "Subscription was not ready");
    }

    @Test
    void testReadyFalseUnhealthy() {
        boolean ready = target.isReady(new SubscriptionBuilder()
                .withNewStatus()
                    .addNewCondition()
                        .withType("CatalogSourcesUnhealthy")
                        .withStatus("True")
                    .endCondition()
                .endStatus()
                .build());

        assertFalse(ready, () -> "Subscription was ready, but should have not been ready");
    }

    @Test
    void testReadyFalseNoConditions() {
        boolean ready = target.isReady(new SubscriptionBuilder()
                .withNewStatus()
                .endStatus()
                .build());

        assertFalse(ready, () -> "Subscription was ready, but should have not been ready");
    }

    @Test
    void testReadyFalseNoStatus() {
        boolean ready = target.isReady(new Subscription());
        assertFalse(ready, () -> "Subscription was ready, but should have not been ready");
    }

    @Test
    void testReadyFalseNoResource() {
        boolean ready = target.isReady(null);
        assertFalse(ready, () -> "Subscription was ready, but should have not been ready");
    }
}
