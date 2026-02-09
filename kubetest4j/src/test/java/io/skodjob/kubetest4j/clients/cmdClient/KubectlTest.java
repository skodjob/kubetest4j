/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients.cmdClient;

import io.skodjob.kubetest4j.executor.Exec;
import io.skodjob.kubetest4j.executor.ExecResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class KubectlTest {

    private static final String DEFAULT_NAMESPACE = "default-test-ns";
    private static final String CUSTOM_CONFIG = "/path/to/custom/kubeconfig";

    private MockedStatic<Exec> mockedExec;

    @BeforeEach
    void setUp() {
        mockedExec = Mockito.mockStatic(Exec.class);
    }

    @AfterEach
    void tearDown() {
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

    @Test
    void testDefaultConstructor() {
        Kubectl client = new Kubectl();
        assertNull(client.config, "Default constructor should result in null config");
        assertNull(client.getCurrentNamespace(), "Default constructor should result in null namespace");
    }

    @Test
    void testConstructorWithConfig() {
        Kubectl client = new Kubectl(CUSTOM_CONFIG);
        assertEquals(CUSTOM_CONFIG, client.config, "Constructor should set the provided config");
        assertNull(client.getCurrentNamespace(), "Constructor with config only should result in null namespace");
    }

    @Test
    void testCmdReturnsCorrectCommand() {
        Kubectl client = new Kubectl();
        assertEquals(Kubectl.KUBECTL, client.cmd(), "cmd() should return 'kubectl'");
    }

    @Test
    void testInNamespaceReturnsNewInstanceWithNamespaceAndConfig() {
        Kubectl initialClient = new Kubectl(CUSTOM_CONFIG);
        assertNull(initialClient.getCurrentNamespace());
        assertEquals(CUSTOM_CONFIG, initialClient.config);

        Kubectl namespacedClient = initialClient.inNamespace(DEFAULT_NAMESPACE);

        assertNotSame(initialClient, namespacedClient, "inNamespace should return a new instance");
        assertEquals(DEFAULT_NAMESPACE, namespacedClient.getCurrentNamespace(),
            "New instance should have the specified namespace");
        assertEquals(CUSTOM_CONFIG, namespacedClient.config, "New instance should retain the original config");
        assertNull(initialClient.getCurrentNamespace(), "Original instance namespace should remain unchanged");
    }

    @Test
    void testGetCurrentNamespaceReturnsSetNamespace() {
        Kubectl client = new Kubectl();
        assertNull(client.getCurrentNamespace(), "Initially namespace should be null");
        Kubectl namespacedClient = client.inNamespace(DEFAULT_NAMESPACE);
        assertEquals(DEFAULT_NAMESPACE, namespacedClient.getCurrentNamespace());
    }

    @Test
    void testDefaultOlmNamespaceReturnsCorrectValue() {
        Kubectl client = new Kubectl();
        assertEquals("operators", client.defaultOlmNamespace());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetUsernameConstructsCommandAndReturnsOutput() {
        String expectedUsername = "system:serviceaccount:kube-system:default";
        ExecResult mockResult = mockSuccessfulExecResult(expectedUsername);
        mockedExec.when(() -> Exec.exec(anyList(), eq(10))).thenReturn(mockResult);

        Kubectl client = new Kubectl(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE).withTimeout(10L);
        String actualUsername = client.getUsername();

        assertEquals(expectedUsername, actualUsername);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(10)));

        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedCommandParts = Arrays.asList(
            Kubectl.KUBECTL,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "auth", "whoami", "-o", "jsonpath='{.status.userInfo.username}'"
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCordonConstructsCorrectCommand() {
        String nodeName = "node-01";
        ExecResult mockResult = mockSuccessfulExecResult("");
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        Kubectl client = new Kubectl(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE);
        client.cordon(nodeName);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedCommandParts = Arrays.asList(
            Kubectl.KUBECTL,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "cordon", nodeName
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUncordonConstructsCorrectCommand() {
        String nodeName = "node-02";
        ExecResult mockResult = mockSuccessfulExecResult("");
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        Kubectl client = new Kubectl(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE);
        client.uncordon(nodeName);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedCommandParts = Arrays.asList(
            Kubectl.KUBECTL,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "uncordon", nodeName
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDrainConstructsCorrectCommand() {
        String nodeName = "node-to-drain";
        boolean ignoreDaemonSets = true;
        boolean disableEviction = false;
        long timeoutInSeconds = 300;

        ExecResult mockResult = mockSuccessfulExecResult("");
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        Kubectl client = new Kubectl(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE);
        client.drain(nodeName, ignoreDaemonSets, disableEviction, timeoutInSeconds);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();

        List<String> expectedCommandParts = Arrays.asList(
            Kubectl.KUBECTL,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "drain", nodeName,
            "--ignore-daemonsets", String.valueOf(ignoreDaemonSets),
            "--disable-eviction", String.valueOf(disableEviction),
            "--timeout", timeoutInSeconds + "s"
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInheritedGetUsesKubectlCmd() {
        String resourceType = "pod";
        String resourceName = "my-test-pod";
        String expectedYaml = "apiVersion: v1\nkind: Pod...";
        ExecResult mockResult = mockSuccessfulExecResult(expectedYaml);

        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        Kubectl client = new Kubectl(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE);
        String actualYaml = client.get(resourceType, resourceName);

        assertEquals(expectedYaml, actualYaml);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();

        assertTrue(capturedCommand.contains(Kubectl.KUBECTL), "Command should use 'kubectl'");
        List<String> expectedParts = Arrays.asList(
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "get", resourceType, resourceName, "-o", "yaml"
        );
        expectedParts.forEach(part -> assertTrue(capturedCommand.contains(part)));
    }
}