/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.MustGather;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.helpers.MustGatherTestSupplier;
import io.skodjob.kubetest4j.helpers.TestListener;
import io.skodjob.kubetest4j.helpers.ValueHolder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestVisualSeparator
@ExtendWith(TestListener.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@MustGather(config = MustGatherTestSupplier.class)
final class MustGatherHandlerTest {

    @Test
    @Order(1)
    void testFailAnCallMustGather() {
        fail();
    }

    @Test
    @Order(2)
    void testMustGatherSupplierCalled() {
        assertTrue(ValueHolder.get().callbackCalled.get());
    }
}
