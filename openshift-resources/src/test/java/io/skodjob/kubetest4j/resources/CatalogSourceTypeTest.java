/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSourceBuilder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogSourceTypeTest {

    CatalogSourceType target;

    @BeforeEach
    void setup() {
        // no real client needed for readiness tests
        target = new CatalogSourceType(null);
    }

    @Test
    void testReadyTrue() {
        boolean ready = target.isReady(new CatalogSourceBuilder()
                .withNewStatus()
                    .withNewConnectionState()
                        .withLastObservedState("READY")
                    .endConnectionState()
                .endStatus()
                .build());

        assertTrue(ready, () -> "CatalogSource was not ready");
    }

    @Test
    void testReadyFalseTransientFailure() {
        boolean ready = target.isReady(new CatalogSourceBuilder()
                .withNewStatus()
                    .withNewConnectionState()
                        .withLastObservedState("TRANSIENT_FAILURE")
                    .endConnectionState()
                .endStatus()
                .build());

        assertFalse(ready, () -> "CatalogSource was ready, but should have not been ready");
    }

    @Test
    void testReadyFalseNoStatus() {
        boolean ready = target.isReady(new CatalogSource());
        assertFalse(ready, () -> "CatalogSource was ready, but should have not been ready");
    }

    @Test
    void testReadyFalseNoResource() {
        boolean ready = target.isReady(null);
        assertFalse(ready, () -> "CatalogSource was ready, but should have not been ready");
    }
}
