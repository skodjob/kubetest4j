/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.executor;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestVisualSeparator
class ExecResultTest {

    @Test
    void testExitStatusSuccess() {
        ExecResult result = new ExecResult(0, "stdout content", "stderr content");
        assertTrue(result.exitStatus());
    }

    @Test
    void testExitStatusFailure() {
        ExecResult result = new ExecResult(1, "stdout content", "stderr content");
        assertFalse(result.exitStatus());
    }

    @Test
    void testToString() {
        ExecResult result = new ExecResult(0, "some output", "some error");
        String expected = "ExecResult{returnCode=0, stdOut='some output', stdErr='some error'}";
        assertEquals(expected, result.toString());
    }

    @Test
    void testEmptyOutputs() {
        ExecResult result = new ExecResult(0, "", "");
        assertTrue(result.exitStatus());
        assertEquals("", result.out());
        assertEquals("", result.err());
    }
}
