/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.executor;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.clients.KubeClusterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestVisualSeparator
class ExecTest {

    @TempDir
    Path tempDir;

    private Exec exec;

    @BeforeEach
    void setup() {
        exec = new Exec();
    }

    private List<String> getLsCommand() {
        return new ArrayList<>(List.of("ls"));
    }

    private List<String> getCatCommand() {
        return new ArrayList<>(List.of("cat"));
    }

    private List<String> getSleepCommand() {
        return Arrays.asList("sleep", String.valueOf(2));
    }

    @Test
    void testSuccessfulCommandExecution() {
        ExecResult result = Exec.exec(getLsCommand());
        assertNotNull(result);
        assertEquals(0, result.returnCode());
        assertTrue(result.out().contains("ExecTest.java") ||
            result.out().contains("Exec.java") ||
            result.out().contains("pom.xml"));
        assertTrue(result.err().isEmpty());
        assertTrue(result.exitStatus());
    }

    @Test
    void testCommandWithArguments() {
        List<String> command = getLsCommand();
        command.add("-l");

        ExecResult result = Exec.exec(command);
        assertNotNull(result);
        assertEquals(0, result.returnCode());
        assertTrue(result.out().contains("total"));
        assertTrue(result.err().isEmpty());
        assertTrue(result.exitStatus());
    }

    @Test
    void testCommandWithInput() {
        String input = "Hello World\n";
        ExecResult result = Exec.exec(input, getCatCommand());
        assertNotNull(result);
        assertEquals(0, result.returnCode());
        assertEquals(input, result.out());
        assertTrue(result.err().isEmpty());
        assertTrue(result.exitStatus());
    }

    @Test
    void testCommandWithError() {
        ExecResult result = Exec.exec(null, List.of("ls", "----h"), 5, false, false);
        assertNotNull(result);
        assertFalse(result.exitStatus());
    }

    @Test
    void testCommandWithErrorAndThrowErrors() {
        assertThrows(KubeClusterException.class, () -> Exec.exec(null, List.of("ls", "----h"),
            Collections.emptySet(), 0, false, true));
    }

    @Test
    void testCommandTimeout() {
        long startTime = System.currentTimeMillis();
        ExecResult result = Exec.exec(null, getSleepCommand(), 5000, false);
        long endTime = System.currentTimeMillis();

        assertTrue((endTime - startTime) < 3000);
        assertTrue(result.exitStatus());
    }

    @Test
    void testLogToOutput() {
        assertDoesNotThrow(() -> Exec.exec(true, getLsCommand().toArray(new String[0])));
    }

    @Test
    void testStoreOutputsToFile() throws IOException, InterruptedException, ExecutionException {
        Path logDir = tempDir.resolve("exec-logs");
        exec = new Exec(logDir);

        exec.execute(null, getLsCommand(), Collections.emptySet(), 0);

        assertTrue(Files.exists(logDir.resolve("stdOutput.log")));
        assertTrue(Files.exists(logDir.resolve("stdError.log")));

        String stdOutContent = Files.readString(logDir.resolve("stdOutput.log"));
        String stdErrContent = Files.readString(logDir.resolve("stdError.log"));

        assertFalse(stdOutContent.isEmpty());
        assertTrue(stdErrContent.isEmpty());
    }

    @Test
    void testIsExecutableOnPathExisting() {
        assertTrue(Exec.isExecutableOnPath("ls"));
    }

    @Test
    void testIsExecutableOnPathNonExisting() {
        assertFalse(Exec.isExecutableOnPath("non_existent_command_12345_xyz"));
    }

    @Test
    void testCutExecutorLogShort() {
        String shortLog = "This is a short log.";
        assertEquals(shortLog, Exec.cutExecutorLog(shortLog));
    }

    @Test
    void testCutExecutorLogLong() {
        String longLog = "a".repeat(3000);
        String cutLog = Exec.cutExecutorLog(longLog);
        assertEquals(2000, cutLog.length());
        assertTrue(longLog.startsWith(cutLog));
    }

    @Test
    void testExecBuilderBasicCommand() {
        ExecResult result = Exec.builder()
            .withCommand(getLsCommand())
            .exec();
        assertNotNull(result);
        assertEquals(0, result.returnCode());
        assertTrue(result.exitStatus());
    }

    @Test
    void testExecBuilderWithInputAndLogToOutput() {
        String input = "Builder Input Test\n";
        ExecResult result = Exec.builder()
            .withCommand(getCatCommand())
            .withInput(input)
            .logToOutput(true)
            .exec();
        assertNotNull(result);
        assertEquals(0, result.returnCode());
        assertEquals(input, result.out());
        assertTrue(result.exitStatus());
    }

    @Test
    void testExecBuilderWithTimeout() {
        long startTime = System.currentTimeMillis();
        ExecResult result = Exec.builder()
            .withCommand(getSleepCommand())
            .timeout(100)
            .exec();
        long endTime = System.currentTimeMillis();

        assertTrue((endTime - startTime) < 2000);
        assertFalse(result.exitStatus());
    }

    @Test
    void testExecBuilderWithEnvVars() {
        Set<EnvVar> envVars = new HashSet<>();
        envVars.add(new EnvVar("MY_TEST_VAR", "HelloFromEnv", null));

        List<String> command = Arrays.asList("bash", "-c", "echo $MY_TEST_VAR");

        ExecResult result = Exec.builder()
            .withCommand(command)
            .withEnvVars(envVars)
            .exec();

        assertNotNull(result);
        assertEquals(0, result.returnCode());
        assertEquals("HelloFromEnv\n", result.out());
    }

    @Test
    void testExecBuilderThrowErrorsOnFailure() {
        assertThrows(KubeClusterException.class, () -> Exec.builder()
            .withCommand("non_existent_command_123456789")
            .throwErrors(true)
            .exec());
    }

    @Test
    void testExecBuilderNoThrowErrorsOnFailure() {
        ExecResult result = Exec.builder()
            .withCommand("ls", "----h")
            .throwErrors(false)
            .exec();
        assertNotNull(result);
        assertFalse(result.exitStatus());
        assertFalse(result.err().isEmpty());
    }

    @Test
    void testCreateAppropriateExceptionNotFound() {
        // Test Java 21 Enhanced Pattern Matching for NotFound errors
        String errorText = "Error from server (NotFound): pods \"missing-pod\" not found";
        Pattern errorPattern = Pattern.compile("Error from server \\(([a-zA-Z0-9]+)\\):");
        Pattern invalidPattern = Pattern.compile("The ([a-zA-Z0-9]+) \"([a-z0-9.-]+)\" is invalid:");

        Matcher errorMatcher = errorPattern.matcher(errorText);
        Matcher invalidMatcher = invalidPattern.matcher(errorText);
        ExecResult execResult = new ExecResult(1, "", errorText);

        KubeClusterException result = Exec.createAppropriateException(
            errorMatcher, invalidMatcher, execResult, "Test message");

        assertInstanceOf(KubeClusterException.NotFound.class, result);
        assertEquals("Test message", result.getMessage());
        assertSame(execResult, result.result);
    }

    @Test
    void testCreateAppropriateExceptionAlreadyExists() {
        // Test Java 21 Enhanced Pattern Matching for AlreadyExists errors
        String errorText = "Error from server (AlreadyExists): pods \"existing-pod\" already exists";
        Pattern errorPattern = Pattern.compile("Error from server \\(([a-zA-Z0-9]+)\\):");
        Pattern invalidPattern = Pattern.compile("The ([a-zA-Z0-9]+) \"([a-z0-9.-]+)\" is invalid:");

        Matcher errorMatcher = errorPattern.matcher(errorText);
        Matcher invalidMatcher = invalidPattern.matcher(errorText);
        ExecResult execResult = new ExecResult(1, "", errorText);

        KubeClusterException result = Exec.createAppropriateException(
            errorMatcher, invalidMatcher, execResult, "Test message");

        assertInstanceOf(KubeClusterException.AlreadyExists.class, result);
        assertEquals("Test message", result.getMessage());
        assertSame(execResult, result.result);
    }

    @Test
    void testCreateAppropriateExceptionInvalidResource() {
        // Test Java 21 Enhanced Pattern Matching for InvalidResource (priority case)
        String errorText = "The Pod \"invalid-pod-name\" is invalid: metadata.name: Invalid value";
        Pattern errorPattern = Pattern.compile("Error from server \\(([a-zA-Z0-9]+)\\):");
        Pattern invalidPattern = Pattern.compile("The ([a-zA-Z0-9]+) \"([a-z0-9.-]+)\" is invalid:");

        Matcher errorMatcher = errorPattern.matcher(errorText);
        Matcher invalidMatcher = invalidPattern.matcher(errorText);
        ExecResult execResult = new ExecResult(1, "", errorText);

        KubeClusterException result = Exec.createAppropriateException(
            errorMatcher, invalidMatcher, execResult, "Test message");

        assertInstanceOf(KubeClusterException.InvalidResource.class, result);
        assertEquals("Test message", result.getMessage());
        assertSame(execResult, result.result);
    }

    @Test
    void testCreateAppropriateExceptionUnknownError() {
        // Test Java 21 Enhanced Pattern Matching default case
        String errorText = "Error from server (UnknownError): something went wrong";
        Pattern errorPattern = Pattern.compile("Error from server \\(([a-zA-Z0-9]+)\\):");
        Pattern invalidPattern = Pattern.compile("The ([a-zA-Z0-9]+) \"([a-z0-9.-]+)\" is invalid:");

        Matcher errorMatcher = errorPattern.matcher(errorText);
        Matcher invalidMatcher = invalidPattern.matcher(errorText);
        ExecResult execResult = new ExecResult(1, "", errorText);

        KubeClusterException result = Exec.createAppropriateException(
            errorMatcher, invalidMatcher, execResult, "Test message");

        // Should fall back to base KubeClusterException for unknown error types
        assertEquals(KubeClusterException.class, result.getClass());
        assertEquals("Test message", result.getMessage());
        assertSame(execResult, result.result);
    }

    @Test
    void testCreateAppropriateExceptionNoPatternMatch() {
        // Test Java 21 Enhanced Pattern Matching fallback case
        String errorText = "Some generic error message";
        Pattern errorPattern = Pattern.compile("Error from server \\(([a-zA-Z0-9]+)\\):");
        Pattern invalidPattern = Pattern.compile("The ([a-zA-Z0-9]+) \"([a-z0-9.-]+)\" is invalid:");

        Matcher errorMatcher = errorPattern.matcher(errorText);
        Matcher invalidMatcher = invalidPattern.matcher(errorText);
        ExecResult execResult = new ExecResult(1, "", errorText);

        KubeClusterException result = Exec.createAppropriateException(
            errorMatcher, invalidMatcher, execResult, "Test message");

        // Should fall back to base KubeClusterException when no patterns match
        assertEquals(KubeClusterException.class, result.getClass());
        assertEquals("Test message", result.getMessage());
        assertSame(execResult, result.result);
    }
}
