/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestVisualSeparator
final class LogCollectorBuilderTest {

    @Test
    void testPassingPodAndPodsAsResourcesToLogCollectorBuilder() {
        LogCollectorBuilder logCollectorBuilder = new LogCollectorBuilder()
            .withNamespacedResources("pod", "pods");

        assertEquals(Collections.emptyList(), logCollectorBuilder.getNamespacedResources());
    }

    @Test
    void testRuntimeExceptionIsThrownIfRootFolderPathIsNotSpecified() {
        LogCollectorBuilder logCollectorBuilder = new LogCollectorBuilder();

        assertThrows(RuntimeException.class, logCollectorBuilder::build);
    }
}
