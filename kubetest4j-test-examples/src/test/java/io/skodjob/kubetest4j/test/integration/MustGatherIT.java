/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;


import io.skodjob.kubetest4j.annotations.MustGather;
import io.skodjob.kubetest4j.test.integration.helpers.MustGatherSupplierImpl;
import io.skodjob.kubetest4j.test.integration.helpers.TestListener;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(TestListener.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@MustGather(config = MustGatherSupplierImpl.class)
final class MustGatherIT extends AbstractIT {

    @Test
    @Order(1)
    void testFailAndCollectLogs() {
        fail();
    }

    @Test
    @Order(2)
    void testCheckLogsCollected() {
        assertTrue(Files.exists(LOG_DIR.resolve("failedTest")
            .resolve("testFailAndCollectLogs").resolve("cluster-wide-resources")));
        assertTrue(Files.exists(LOG_DIR.resolve("failedTest")
            .resolve("testFailAndCollectLogs").resolve("default")));
    }
}
