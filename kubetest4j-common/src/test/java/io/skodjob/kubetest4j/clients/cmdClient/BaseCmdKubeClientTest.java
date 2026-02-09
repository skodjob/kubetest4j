/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients.cmdClient;

import io.skodjob.kubetest4j.clients.KubeClusterException;
import io.skodjob.kubetest4j.enums.LogLevel;
import io.skodjob.kubetest4j.executor.Exec;
import io.skodjob.kubetest4j.executor.ExecResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BaseCmdKubeClientTest {

    private static final String TEST_CMD = "testkubectl";
    private static final String TEST_NAMESPACE = "test-ns";
    private static final String TEST_CONFIG = "/path/to/kubeconfig";
    private static final String REPLACE = "replace";

    private TestableCmdKubeClient client;
    private MockedStatic<Exec> mockedExec;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        client = new TestableCmdKubeClient(TEST_CMD, TEST_CONFIG);
        client.setNamespace(TEST_NAMESPACE);
        mockedExec = Mockito.mockStatic(Exec.class);
    }

    @AfterEach
    void tearDown() {
        // Close the static mock
        mockedExec.close();
    }

    private ExecResult mockSuccessfulExecResult(String output) {
        ExecResult result = mock(ExecResult.class);
        lenient().when(result.exitStatus()).thenReturn(true);
        lenient().when(result.out()).thenReturn(output != null ? output : "");
        lenient().when(result.err()).thenReturn("");
        lenient().when(result.returnCode()).thenReturn(0);
        return result;
    }

    private ExecResult mockFailedExecResult(String errorOutput, int returnCode) {
        ExecResult result = mock(ExecResult.class);
        lenient().when(result.exitStatus()).thenReturn(false);
        lenient().when(result.out()).thenReturn("");
        lenient().when(result.err()).thenReturn(errorOutput != null ? errorOutput : "Error");
        lenient().when(result.returnCode()).thenReturn(returnCode);
        return result;
    }

    @Test
    void testCommandConstructionWithConfigAndNamespace() {
        List<String> constructedCommand = client.command("get", "pods");
        List<String> expected = Arrays.asList(TEST_CMD, "--kubeconfig", TEST_CONFIG,
            "--namespace", TEST_NAMESPACE, "get", "pods");
        assertEquals(expected, constructedCommand);
    }

    @Test
    void testCommandConstructionWithoutConfig() {
        TestableCmdKubeClient clientNoConfig = new TestableCmdKubeClient(TEST_CMD);
        clientNoConfig.setNamespace(TEST_NAMESPACE);
        List<String> constructedCommand = clientNoConfig.command("get", "pods");
        List<String> expected = Arrays.asList(TEST_CMD, "--namespace", TEST_NAMESPACE, "get", "pods");
        assertEquals(expected, constructedCommand);
    }

    @Test
    void testCommandConstructionWithoutNamespace() {
        TestableCmdKubeClient clientNoNamespace = new TestableCmdKubeClient(TEST_CMD, TEST_CONFIG);
        List<String> constructedCommand = clientNoNamespace.command("get", "pods");
        List<String> expected = Arrays.asList(TEST_CMD, "--kubeconfig", TEST_CONFIG, "get", "pods");
        assertEquals(expected, constructedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteByName() {
        String resourceType = "pod";
        String resourceName = "my-pod";
        ExecResult mockResult = mockSuccessfulExecResult(null);

        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        client.deleteByName(resourceType, resourceName);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedCommand = Arrays.asList(TEST_CMD, "--kubeconfig", TEST_CONFIG,
            "--namespace", TEST_NAMESPACE, "delete", resourceType, resourceName);
        assertEquals(expectedCommand, listCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGet() {
        String resourceType = "service";
        String resourceName = "my-service";
        String expectedYaml = "apiVersion: v1\nkind: Service...";
        ExecResult mockResult = mockSuccessfulExecResult(expectedYaml);

        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.when(() -> Exec.exec(commandCaptor.capture(), eq(0))).thenReturn(mockResult);

        String actualYaml = client.get(resourceType, resourceName);

        assertEquals(expectedYaml, actualYaml);
        List<String> expectedCommand = Arrays.asList(TEST_CMD, "--kubeconfig", TEST_CONFIG,
            "--namespace", TEST_NAMESPACE, "get", resourceType, resourceName, "-o", "yaml");
        assertEquals(expectedCommand, commandCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetEvents() {
        String expectedEvents = "LAST SEEN   TYPE      REASON      OBJECT      MESSAGE";
        ExecResult mockResult = mockSuccessfulExecResult(expectedEvents);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String actualEvents = client.getEvents();
        assertEquals(expectedEvents, actualEvents);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedCommand = Arrays.asList(TEST_CMD, "--kubeconfig", TEST_CONFIG,
            "--namespace", TEST_NAMESPACE, "get", "events");
        assertEquals(expectedCommand, listCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateSingleFileSuccess() throws IOException {
        File testFile = Files.createFile(tempDir.resolve("test.yaml")).toFile();
        ExecResult mockResult = mockSuccessfulExecResult("created");
        mockedExec.when(() -> Exec.exec(eq(null), anyList(), eq(0), eq(false), eq(false)))
            .thenReturn(mockResult);

        client.create(testFile);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(eq(null), listCaptor.capture(), eq(0), eq(false), eq(false)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("create"));
        assertTrue(capturedCommand.contains("-f"));
        assertTrue(capturedCommand.contains(testFile.getAbsolutePath()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateSingleFileFailureLogsWarning() throws IOException {
        File testFile = Files.createFile(tempDir.resolve("test-fail.yaml")).toFile();
        ExecResult mockResult = mockFailedExecResult("error creating", 1);
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), anyInt(), eq(false), eq(false)))
            .thenReturn(mockResult);

        client.create(testFile);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0), eq(false), eq(false)),
            times(1));
        assertTrue(listCaptor.getValue().contains(testFile.getAbsolutePath()));
    }


    @Test
    @SuppressWarnings("unchecked")
    void testCreateDirectoryWithFiles() throws IOException {
        File dir = tempDir.resolve("myresources").toFile();
        assertTrue(dir.mkdirs());
        File file1 = Files.createFile(dir.toPath().resolve("01-configmap.yaml")).toFile();
        File file2 = Files.createFile(dir.toPath().resolve("02-deployment.yaml")).toFile();
        File nonYamlFile = Files.createFile(dir.toPath().resolve("notes.txt")).toFile();

        ExecResult mockResultSuccess = mockSuccessfulExecResult("created");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(false), eq(false)))
            .thenReturn(mockResultSuccess);

        client.create(dir);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0), eq(false), eq(false)),
            times(2));

        List<List<String>> allCommands = listCaptor.getAllValues();
        assertTrue(allCommands.get(0).contains(file2.getAbsolutePath()));
        assertTrue(allCommands.get(1).contains(file1.getAbsolutePath()));
        assertFalse(allCommands.stream().anyMatch(cmdList -> cmdList.contains(nonYamlFile.getAbsolutePath())));
    }

    @Test
    void testCreateFileNotExistsThrowsRuntimeException() {
        File nonExistentFile = new File(tempDir.toFile(), "ghost.yaml");

        Exception exception = assertThrows(RuntimeException.class, () -> client.create(nonExistentFile));
        assertInstanceOf(NoSuchFileException.class, exception.getCause());
        assertEquals(nonExistentFile.getPath(), exception.getCause().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testApplySingleFile() throws IOException {
        File testFile = Files.createFile(tempDir.resolve("apply-test.yaml")).toFile();
        ExecResult mockResult = mockSuccessfulExecResult("applied");
        mockedExec.when(() -> Exec.exec(eq(null), anyList(), eq(0), eq(false), eq(false)))
            .thenReturn(mockResult);

        client.apply(testFile);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(eq(null), listCaptor.capture(), eq(0), eq(false), eq(false)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("apply"));
        assertTrue(capturedCommand.contains("-f"));
        assertTrue(capturedCommand.contains(testFile.getAbsolutePath()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteSingleFile() throws IOException {
        File testFile = Files.createFile(tempDir.resolve("delete-test.yaml")).toFile();
        ExecResult mockResult = mockSuccessfulExecResult("deleted");
        mockedExec.when(() -> Exec.exec(eq(null), anyList(), eq(0), eq(false), eq(false)))
            .thenReturn(mockResult);

        client.delete(testFile);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(eq(null), listCaptor.capture(), eq(0), eq(false), eq(false)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("delete"));
        assertTrue(capturedCommand.contains("-f"));
        assertTrue(capturedCommand.contains(testFile.getAbsolutePath()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testReplaceSingleFile() throws IOException {
        File testFile = Files.createFile(tempDir.resolve("replace-test.yaml")).toFile();
        ExecResult mockResult = mockSuccessfulExecResult("replaced");
        mockedExec.when(() -> Exec.exec(eq(null), anyList(), eq(0), eq(false), eq(false)))
            .thenReturn(mockResult);

        client.replace(testFile);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(eq(null), listCaptor.capture(), eq(0), eq(false), eq(false)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("replace"));
        assertTrue(capturedCommand.contains("-f"));
        assertTrue(capturedCommand.contains(testFile.getAbsolutePath()));
    }


    @Test
    @SuppressWarnings("unchecked")
    void testApplyContent() {
        String yamlContent = "kind: Pod\napiVersion: v1";
        ExecResult mockResult = mockSuccessfulExecResult("applied");
        mockedExec.when(() -> Exec.exec(eq(yamlContent), anyList(), eq(0), eq(true), eq(true)))
            .thenReturn(mockResult);

        client.applyContent(yamlContent);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(eq(yamlContent), listCaptor.capture(), eq(0), eq(true), eq(true)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("apply"));
        assertTrue(capturedCommand.contains("-f"));
        assertTrue(capturedCommand.contains("-"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testReplaceContent() {
        String yamlContent = "kind: Deployment\napiVersion: apps/v1";
        ExecResult mockResult = mockSuccessfulExecResult("replaced");
        mockedExec.when(() -> Exec.exec(eq(yamlContent), anyList(), eq(0), eq(true), eq(true)))
            .thenReturn(mockResult);

        TestableCmdKubeClient returnedClient = client.replaceContent(yamlContent);

        assertSame(client, returnedClient, "Should return the same client instance for fluency");

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(eq(yamlContent), listCaptor.capture(), eq(0), eq(true), eq(true)));
        List<String> capturedCommand = listCaptor.getValue();

        assertTrue(capturedCommand.contains(REPLACE), "Command should contain 'replace'");
        assertTrue(capturedCommand.contains("-f"), "Command should contain '-f'");
        assertTrue(capturedCommand.contains("-"), "Command should contain '-' for stdin");
        assertTrue(capturedCommand.contains(TEST_CMD), "Command should contain the client's command");
        assertTrue(capturedCommand.contains("--kubeconfig"), "Command should contain '--kubeconfig'");
        assertTrue(capturedCommand.contains(TEST_CONFIG), "Command should contain the config path");
        assertTrue(capturedCommand.contains("--namespace"), "Command should contain '--namespace'");
        assertTrue(capturedCommand.contains(TEST_NAMESPACE), "Command should contain the namespace");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteContent() {
        String yamlContent = "kind: Pod\napiVersion: v1";
        ExecResult mockResult = mockSuccessfulExecResult("deleted");
        mockedExec.when(() -> Exec.exec(eq(yamlContent), anyList(), eq(0), eq(true), eq(false)))
            .thenReturn(mockResult);
        client.deleteContent(yamlContent);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(eq(yamlContent), listCaptor.capture(), eq(0), eq(true), eq(false)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("delete"));
        assertTrue(capturedCommand.contains("-f"));
        assertTrue(capturedCommand.contains("-"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateNamespace() {
        String namespaceName = "new-ns";
        ExecResult mockResult = mockSuccessfulExecResult("namespace/new-ns created");
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        client.createNamespace(namespaceName);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedParts = Arrays.asList(TEST_CMD, "--kubeconfig", TEST_CONFIG,
            "--namespace", TEST_NAMESPACE, "create", "namespace", namespaceName);
        assertTrue(capturedCommand.containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteNamespace() {
        String namespaceName = "old-ns";
        ExecResult mockResult = mockSuccessfulExecResult("namespace/old-ns deleted");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(true), eq(false)))
            .thenReturn(mockResult);

        client.deleteNamespace(namespaceName);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0), eq(true), eq(false)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedParts = Arrays.asList(TEST_CMD, "--kubeconfig", TEST_CONFIG,
            "--namespace", TEST_NAMESPACE, "delete", "namespace", namespaceName);
        assertTrue(capturedCommand.containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testScaleByName() {
        String kind = "deployment";
        String name = "my-app";
        int replicas = 3;
        ExecResult mockResult = mockSuccessfulExecResult("scaled");
        mockedExec.when(() -> Exec.exec(isNull(), anyList())).thenReturn(mockResult);

        client.scaleByName(kind, name, replicas);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedParts = Arrays.asList("scale", kind, name, "--replicas", String.valueOf(replicas));
        assertTrue(capturedCommand.containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecInPod() {
        String podName = "my-pod-123";
        String[] cmdToRun = {"ls", "-l"};
        ExecResult mockResult = mockSuccessfulExecResult("total 0");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(LogLevel.INFO),
            eq(false), eq(true))).thenReturn(mockResult);

        ExecResult actualResult = client.execInPod(podName, cmdToRun);

        assertSame(mockResult, actualResult);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0),
            eq(LogLevel.INFO), eq(false), eq(true)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedParts = Arrays.asList("exec", podName, "--", "ls", "-l");
        assertTrue(capturedCommand.containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecInPodContainer() {
        String podName = "my-pod-456";
        String containerName = "my-container";
        String[] cmdToRun = {"ps", "aux"};
        ExecResult mockResult = mockSuccessfulExecResult("USER PID ...");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(LogLevel.INFO),
            eq(true), eq(true))).thenReturn(mockResult);


        ExecResult actualResult = client.execInPodContainer(podName, containerName, cmdToRun);
        assertSame(mockResult, actualResult);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0),
            eq(LogLevel.INFO), eq(true), eq(true)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedParts = Arrays.asList("exec", podName, "-c", containerName, "--", "ps", "aux");
        assertTrue(capturedCommand.containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecInPodContainerWithCustomLogToOutput() {
        String podName = "my-pod-789";
        String containerName = "another-container";
        String[] cmdToRun = {"env"};
        boolean logToOutput = false;
        ExecResult mockResult = mockSuccessfulExecResult("PATH=/usr/bin");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(LogLevel.INFO),
            eq(logToOutput), eq(true))).thenReturn(mockResult);

        ExecResult actualResult = client.execInPodContainer(logToOutput, podName, containerName, cmdToRun);

        assertSame(mockResult, actualResult);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0),
            eq(LogLevel.INFO), eq(logToOutput), eq(true)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedParts = Arrays.asList("exec", podName, "-c", containerName, "--", "env");
        assertTrue(capturedCommand.containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecDefaultParams() {
        String[] cmdToRun = {"version"};
        ExecResult mockResult = mockSuccessfulExecResult("Client Version: ...");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(true), eq(true)))
            .thenReturn(mockResult);

        ExecResult actualResult = client.exec(cmdToRun);

        assertSame(mockResult, actualResult);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0), eq(true), eq(true)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("version"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecWithThrowError() {
        boolean throwError = false;
        String[] cmdToRun = {"get", "foo"};
        ExecResult mockResult = mockFailedExecResult("Error: foo not found", 1);
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(true), eq(throwError)))
            .thenReturn(mockResult);

        ExecResult actualResult = client.exec(throwError, cmdToRun);

        assertSame(mockResult, actualResult);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0), eq(true), eq(throwError)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.containsAll(Arrays.asList("get", "foo")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecWithThrowErrorAndLogToOutput() {
        boolean throwError = true;
        boolean logToOutput = false;
        String[] cmdToRun = {"cluster-info"};
        ExecResult mockResult = mockSuccessfulExecResult("Kubernetes control plane is running at...");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(logToOutput), eq(throwError)))
            .thenReturn(mockResult);

        ExecResult actualResult = client.exec(throwError, logToOutput, cmdToRun);

        assertSame(mockResult, actualResult);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0), eq(logToOutput), eq(throwError)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("cluster-info"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecWithTimeout() {
        boolean throwError = true;
        boolean logToOutput = true;
        int timeout = 5000;
        String[] cmdToRun = {"api-resources"};
        ExecResult mockResult = mockSuccessfulExecResult("NAME SHORTNAMES ...");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(timeout),
                eq(LogLevel.INFO), eq(logToOutput), eq(throwError)))
            .thenReturn(mockResult);

        ExecResult actualResult = client.exec(throwError, logToOutput, timeout, cmdToRun);

        assertSame(mockResult, actualResult);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(timeout),
            eq(LogLevel.INFO), eq(logToOutput), eq(throwError)));
        List<String> capturedCommand = listCaptor.getValue();
        assertTrue(capturedCommand.contains("api-resources"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListResources() {
        String resourceType = "pods";
        String output = "pod-a pod-b  pod-c";
        ExecResult mockResult = mockSuccessfulExecResult(output);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        List<String> resources = client.list(resourceType);

        assertEquals(Arrays.asList("pod-a", "pod-b", "pod-c"), resources);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("get", resourceType,
            "-o", "jsonpath={range .items[*]}{.metadata.name} ");
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    void testListResourcesEmptyOutput() {
        String resourceType = "deployments";
        String output = " ";
        ExecResult mockResult = mockSuccessfulExecResult(output);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        List<String> resources = client.list(resourceType);

        assertTrue(resources.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetResourceAsJson() {
        String type = "configmap";
        String name = "my-cm";
        String jsonOutput = "{\"kind\": \"ConfigMap\"}";
        ExecResult mockResult = mockSuccessfulExecResult(jsonOutput);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String result = client.getResourceAsJson(type, name);
        assertEquals(jsonOutput, result);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("get", type, name, "-o", "json");
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetResourceAsYaml() {
        String type = "secret";
        String name = "my-secret";
        String yamlOutput = "kind: Secret";
        ExecResult mockResult = mockSuccessfulExecResult(yamlOutput);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String result = client.getResourceAsYaml(type, name);
        assertEquals(yamlOutput, result);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("get", type, name, "-o", "yaml");
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetResourcesAsYaml() {
        String type = "ingresses";
        String yamlOutput = "kind: List\nitems:\n- kind: Ingress";
        ExecResult mockResult = mockSuccessfulExecResult(yamlOutput);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String result = client.getResourcesAsYaml(type);
        assertEquals(yamlOutput, result);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("get", type, "-o", "yaml");
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateResourceAndApply() {
        String template = "my-template";
        Map<String, String> params = Map.of("NAME", "app1", "REPLICAS", "2");
        String processedYaml = "kind: Deployment\nmetadata:\n  name: app1";

        ExecResult processResult = mockSuccessfulExecResult(processedYaml);
        ExecResult applyResult = mockSuccessfulExecResult("applied");

        ArgumentCaptor<List<String>> processCmdCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.when(() -> Exec.exec(processCmdCaptor.capture(), eq(0))).thenReturn(processResult);

        ArgumentCaptor<List<String>> applyCmdCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.when(() -> Exec.exec(eq(processedYaml), applyCmdCaptor.capture(), eq(0), eq(true), eq(true)))
            .thenReturn(applyResult);

        client.createResourceAndApply(template, params);

        List<String> processCmd = processCmdCaptor.getValue();
        List<String> expectedProcessParts = Arrays.asList("process", template, "-l", "app=" + template, "-o", "yaml");
        assertTrue(processCmd.containsAll(expectedProcessParts));
        assertTrue(processCmd.contains("-p"));
        assertTrue(processCmd.stream().anyMatch(s -> s.equals("NAME=app1") || s.equals("REPLICAS=2")));
        assertTrue(processCmd.stream().anyMatch(s -> s.equals("REPLICAS=2") || s.equals("NAME=app1")));

        List<String> applyCmd = applyCmdCaptor.getValue();
        List<String> expectedApplyParts = Arrays.asList("apply", "-f", "-");
        assertTrue(applyCmd.containsAll(expectedApplyParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDescribe() {
        String type = "node";
        String name = "node01";
        String description = "Name: node01\nRoles: worker";
        ExecResult mockResult = mockSuccessfulExecResult(description);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String result = client.describe(type, name);
        assertEquals(description, result);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("describe", type, name);
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLogsPodOnly() {
        String podName = "log-pod";
        String logOutput = "Log line 1\nLog line 2";
        ExecResult mockResult = mockSuccessfulExecResult(logOutput);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String logs = client.logs(podName, null);
        assertEquals(logOutput, logs);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        assertTrue(listCaptor.getValue().containsAll(Arrays.asList("logs", podName)));
        assertFalse(listCaptor.getValue().contains("-c"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLogsPodAndContainer() {
        String podName = "log-pod-cont";
        String containerName = "log-container";
        String logOutput = "Container log 1";
        ExecResult mockResult = mockSuccessfulExecResult(logOutput);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String logs = client.logs(podName, containerName);
        assertEquals(logOutput, logs);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("logs", podName, "-c", containerName);
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPreviousLogsPod() {
        String podName = "log-pod-cont";
        String logOutput = "Container log 1";
        ExecResult mockResult = mockSuccessfulExecResult(logOutput);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String logs = client.previousLogs(podName);
        assertEquals(logOutput, logs);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("logs", podName, "--previous=true");
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPreviousLogsPodAndContainer() {
        String podName = "log-pod-cont";
        String containerName = "log-container";
        String logOutput = "Container log 1";
        ExecResult mockResult = mockSuccessfulExecResult(logOutput);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        String logs = client.previousLogs(podName, containerName);
        assertEquals(logOutput, logs);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("logs", podName, "-c", containerName, "--previous=true");
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    void testSearchInLogSuccess() {
        String resourceType = "deployment";
        String resourceName = "my-app";
        long sinceSeconds = 60;
        String[] grepPattern = {"ERROR", "Exception"};
        String logMatch = "Previous line\nERROR: Something went wrong";

        ExecResult mockResult = mockSuccessfulExecResult(logMatch);
        mockedExec.when(() -> Exec.exec(eq(0), eq("bash"), eq("-c"), anyString())).thenReturn(mockResult);

        String result = client.searchInLog(resourceType, resourceName, sinceSeconds, grepPattern);
        assertEquals(logMatch, result);

        ArgumentCaptor<String> bashCmdCaptor = ArgumentCaptor.forClass(String.class);
        mockedExec.verify(() -> Exec.exec(eq(0), eq("bash"), eq("-c"), bashCmdCaptor.capture()));
        String capturedBashCmd = bashCmdCaptor.getValue();

        assertTrue(capturedBashCmd.contains(TEST_CMD));
        assertTrue(capturedBashCmd.contains("logs " + resourceType + "/" + resourceName));
        assertTrue(capturedBashCmd.contains("--since=" + sinceSeconds + "s"));
        assertTrue(capturedBashCmd.contains("| grep  -e ERROR -e Exception -B 1"));
    }

    @Test
    void testSearchInLogGrepNotFoundReturnsEmptyString() {
        String resourceType = "pod";
        String resourceName = "my-pod-log";
        long sinceSeconds = 300;
        String[] grepPattern = {"FATAL"};

        ExecResult failedGrepResult = mock(ExecResult.class);
        lenient().when(failedGrepResult.returnCode()).thenReturn(1);
        lenient().when(failedGrepResult.out()).thenReturn("");

        KubeClusterException kubeException = new KubeClusterException(failedGrepResult, "Grep failed");
        mockedExec.when(() -> Exec.exec(eq(0), eq("bash"), eq("-c"), anyString()))
            .thenThrow(kubeException);

        String result = client.searchInLog(resourceType, resourceName, sinceSeconds, grepPattern);
        assertEquals("", result);
    }

    @Test
    void testSearchInLogOtherKubeExceptionReturnsEmptyString() {
        String resourceType = "pod";
        String resourceName = "my-pod-log-err";
        long sinceSeconds = 120;
        String[] grepPattern = {"PANIC"};

        ExecResult errorResult = mock(ExecResult.class);
        lenient().when(errorResult.returnCode()).thenReturn(127);
        lenient().when(errorResult.err()).thenReturn("Command not found or other error");

        KubeClusterException kubeException = new KubeClusterException(errorResult, "Exec error");
        mockedExec.when(() -> Exec.exec(eq(0), eq("bash"), eq("-c"), anyString()))
            .thenThrow(kubeException);

        String result = client.searchInLog(resourceType, resourceName, sinceSeconds, grepPattern);
        assertEquals("", result);
    }

    @Test
    void testSearchInLogWithContainerSuccess() {
        String resourceType = "statefulset";
        String resourceName = "my-db";
        String resourceContainer = "db-container";
        long sinceSeconds = 10;
        String[] grepPattern = {"Warning"};
        String logMatch = "Context line\nWarning: Low disk space";

        ExecResult mockResult = mockSuccessfulExecResult(logMatch);
        mockedExec.when(() -> Exec.exec(eq(0), eq("bash"), eq("-c"), anyString()))
            .thenReturn(mockResult);

        String result = client.searchInLog(resourceType, resourceName, resourceContainer,
            sinceSeconds, grepPattern);
        assertEquals(logMatch, result);

        ArgumentCaptor<String> bashCmdCaptor = ArgumentCaptor.forClass(String.class);
        mockedExec.verify(() -> Exec.exec(eq(0), eq("bash"), eq("-c"), bashCmdCaptor.capture()));
        String capturedBashCmd = bashCmdCaptor.getValue();

        String expectedLogCmd = "logs " + resourceType + "/" + resourceName + " -c " + resourceContainer;
        assertTrue(capturedBashCmd.contains(expectedLogCmd));
        assertTrue(capturedBashCmd.contains("--since=" + sinceSeconds + "s"));
        assertTrue(capturedBashCmd.contains("| grep  -e Warning -B 1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListResourcesByLabel() {
        String resourceType = "services";
        String label = "app=my-app";
        String output = "service-a service-b";
        ExecResult mockResult = mockSuccessfulExecResult(output);
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        List<String> resources = client.listResourcesByLabel(resourceType, label);
        assertEquals(Arrays.asList("service-a", "service-b"), resources);
        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> expectedParts = Arrays.asList("get", resourceType, "-l", label,
            "-o", "jsonpath={range .items[*]}{.metadata.name} ");
        assertTrue(listCaptor.getValue().containsAll(expectedParts));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcess() {
        Map<String, String> parameters = Map.of("IMAGE", "nginx:latest", "PORT", "8080");
        String file = "my-template.yaml";
        String processedOutput = "kind: Deployment\nimage: nginx:latest";

        Consumer<String> mockConsumer = mock(Consumer.class);
        ExecResult mockResult = mockSuccessfulExecResult(processedOutput);
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(false))).thenReturn(mockResult);

        client.process(parameters, file, mockConsumer);

        verify(mockConsumer).accept(processedOutput);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(isNull(), listCaptor.capture(), eq(0), eq(false)));
        List<String> command = listCaptor.getValue();
        List<String> expectedCmdParts = Arrays.asList("process", "-f", file);
        assertTrue(command.containsAll(expectedCmdParts));
        assertTrue(command.contains("-p IMAGE=nginx:latest") || command.contains("-p PORT=8080"));
        assertTrue(command.contains("-p PORT=8080") || command.contains("-p IMAGE=nginx:latest"));
    }

    @Test
    void testExecInPodWithThrowErrors() {
        String command = "ls";
        String podName = "my-pod";

        ExecResult mockResult = mockFailedExecResult("Error: permission denied", 1);
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(LogLevel.INFO), eq(false), eq(false)))
            .thenReturn(mockResult);
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(LogLevel.INFO), eq(false), eq(true)))
            .thenThrow(new KubeClusterException(new Exception("Failed to execute command")));

        assertDoesNotThrow(() -> client.execInPod(false, podName, command));
        assertThrows(KubeClusterException.class, () -> client.execInPod(true, podName, command));
    }

    @Test
    void testExecInPodContainerWithThrowErrors() {
        String command = "ls";
        String podName = "my-pod";
        String containerName = "container";

        ExecResult mockResult = mockFailedExecResult("Error: permission denied", 1);
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(LogLevel.INFO), eq(false), eq(false)))
            .thenReturn(mockResult);
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(LogLevel.INFO), eq(false), eq(true)))
            .thenThrow(new KubeClusterException(new Exception("Failed to execute command")));

        assertDoesNotThrow(() -> client.execInPodContainer(false, false, podName, containerName, command));
        assertThrows(KubeClusterException.class,
            () -> client.execInPodContainer(true, false, podName, containerName, command));
    }

    @Test
    void testExecWithLogLevel() {
        String command = "ls";
        LogLevel logLevel = LogLevel.ERROR;

        ExecResult mockResult = mockSuccessfulExecResult("This is my files");

        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0),
            eq(logLevel), eq(true), eq(true))).thenReturn(mockResult);

        ExecResult actualResult = client.exec(logLevel, command);

        assertSame(mockResult, actualResult);
        ArgumentCaptor<LogLevel> logLevelCaptor = ArgumentCaptor.forClass(LogLevel.class);
        mockedExec.verify(() -> Exec.exec(isNull(), anyList(), eq(0),
            logLevelCaptor.capture(), eq(true), eq(true)));
        LogLevel capturedLogLevel = logLevelCaptor.getValue();
        assertTrue(capturedLogLevel.equals(LogLevel.ERROR));
    }

    @Test
    void testExecWithLogLevelAndThrowErrors() {
        String command = "ls";
        LogLevel logLevel = LogLevel.ERROR;
        boolean throwErrors = true;

        ExecResult mockResult = mockSuccessfulExecResult("This is my files");

        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0),
            eq(logLevel), eq(true), eq(throwErrors))).thenReturn(mockResult);

        ExecResult actualResult = client.exec(throwErrors, logLevel, command);
        assertSame(mockResult, actualResult);

        ArgumentCaptor<LogLevel> logLevelCaptor = ArgumentCaptor.forClass(LogLevel.class);
        ArgumentCaptor<Boolean> throwErrorCaptor = ArgumentCaptor.forClass(Boolean.class);

        mockedExec.verify(() -> Exec.exec(isNull(), anyList(), eq(0),
            logLevelCaptor.capture(), eq(true), throwErrorCaptor.capture()));
        LogLevel capturedLogLevel = logLevelCaptor.getValue();
        Boolean capturedThrowErrors = throwErrorCaptor.getValue();

        assertTrue(capturedLogLevel.equals(LogLevel.ERROR));
        assertTrue(capturedThrowErrors.equals(throwErrors));
    }

    @Test
    void testExecWithLogLevels() {
        String command = "ls";
        LogLevel logLevel = LogLevel.ERROR;
        boolean throwErrors = true;

        ExecResult mockResult = mockSuccessfulExecResult("This is my files");

        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0),
            eq(logLevel), eq(true), eq(throwErrors))).thenReturn(mockResult);

        ExecResult actualResult = client.exec(throwErrors, logLevel, command);
        assertSame(mockResult, actualResult);

        ArgumentCaptor<LogLevel> logLevelCaptor = ArgumentCaptor.forClass(LogLevel.class);
        ArgumentCaptor<Boolean> throwErrorCaptor = ArgumentCaptor.forClass(Boolean.class);

        mockedExec.verify(() -> Exec.exec(isNull(), anyList(), eq(0),
            logLevelCaptor.capture(), eq(true), throwErrorCaptor.capture()));
        LogLevel capturedLogLevel = logLevelCaptor.getValue();
        Boolean capturedThrowErrors = throwErrorCaptor.getValue();

        assertTrue(capturedLogLevel.equals(LogLevel.ERROR));
        assertTrue(capturedThrowErrors.equals(throwErrors));
    }

    @Test
    void testExecInPodContainerWithLogLevel() {
        String podName = "my-pod-789";
        String containerName = "another-container";
        String[] cmdToRun = {"env"};
        LogLevel logLevel = LogLevel.ERROR;

        ExecResult mockResult = mockSuccessfulExecResult("PATH=/usr/bin");
        mockedExec.when(() -> Exec.exec(isNull(), anyList(), eq(0), eq(logLevel),
            eq(true), eq(true))).thenReturn(mockResult);

        ExecResult actualResult = client.execInPodContainer(logLevel, true, podName, containerName, cmdToRun);
        assertSame(mockResult, actualResult);

        ArgumentCaptor<LogLevel> logLevelCaptor = ArgumentCaptor.forClass(LogLevel.class);

        mockedExec.verify(() -> Exec.exec(isNull(), anyList(), eq(0),
            logLevelCaptor.capture(), eq(true), eq(true)));
        LogLevel capturedLogLevel = logLevelCaptor.getValue();

        assertTrue(capturedLogLevel.equals(LogLevel.ERROR));
    }
}