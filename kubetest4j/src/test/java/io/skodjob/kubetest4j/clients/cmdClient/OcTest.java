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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class OcTest {

    private static final String DEFAULT_NAMESPACE = "oc-test-ns";
    private static final String CUSTOM_CONFIG = "/path/to/oc/kubeconfig";
    private static final String OC_CMD = "oc";


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
        Oc client = new Oc();
        assertNull(client.config, "Default constructor should result in null config");
        assertNull(client.getCurrentNamespace(), "Default constructor should result in null namespace");
    }

    @Test
    void testConstructorWithConfig() {
        Oc client = new Oc(CUSTOM_CONFIG);
        assertEquals(CUSTOM_CONFIG, client.config, "Constructor should set the provided config");
        assertNull(client.getCurrentNamespace(), "Constructor with config only should result in null namespace");
    }

    @Test
    void testCmdReturnsCorrectCommand() {
        Oc client = new Oc();
        assertEquals(OC_CMD, client.cmd(), "cmd() should return 'oc'");
    }

    @Test
    void testInNamespaceReturnsNewInstanceWithNamespaceAndConfig() {
        Oc initialClient = new Oc(CUSTOM_CONFIG);
        assertNull(initialClient.getCurrentNamespace());
        assertEquals(CUSTOM_CONFIG, initialClient.config);

        Oc namespacedClient = initialClient.inNamespace(DEFAULT_NAMESPACE);

        assertNotSame(initialClient, namespacedClient, "inNamespace should return a new instance");
        assertEquals(DEFAULT_NAMESPACE, namespacedClient.getCurrentNamespace(),
            "New instance should have the specified namespace");
        assertEquals(CUSTOM_CONFIG, namespacedClient.config, "New instance should retain the original config");
        assertNull(initialClient.getCurrentNamespace(), "Original instance namespace should remain unchanged");
    }

    @Test
    void testGetCurrentNamespaceReturnsSetNamespace() {
        Oc client = new Oc();
        assertNull(client.getCurrentNamespace(), "Initially namespace should be null");
        Oc namespacedClient = client.inNamespace(DEFAULT_NAMESPACE);
        assertEquals(DEFAULT_NAMESPACE, namespacedClient.getCurrentNamespace());
    }

    @Test
    void testDefaultOlmNamespaceReturnsCorrectValue() {
        Oc client = new Oc();
        assertEquals("openshift-marketplace", client.defaultOlmNamespace());
    }

    @Test
    void testCreateNamespaceUsesNewProject() {
        String namespaceName = "my-new-project";
        ExecResult mockResult = mockSuccessfulExecResult(""); // new-project might not output much on success
        // Exec.exec(cmd(), "new-project", name);
        mockedExec.when(() -> Exec.exec(eq(0), eq(OC_CMD), eq("new-project"), eq(namespaceName)))
            .thenReturn(mockResult);

        Oc client = new Oc(CUSTOM_CONFIG)
            .inNamespace(DEFAULT_NAMESPACE).withTimeout(0); // Namespace is not used by this call
        client.createNamespace(namespaceName);

        // Verify the specific call for oc createNamespace
        mockedExec.verify(() -> Exec.exec(0, OC_CMD, "new-project", namespaceName));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNewAppConstructsCorrectCommand() {
        String templateName = "my-template";
        Map<String, String> params = Map.of("PARAM1", "VALUE1", "PARAM2", "VALUE2");
        ExecResult mockResult = mockSuccessfulExecResult("Application created");
        mockedExec.when(() -> Exec.exec(anyList(), eq(5))).thenReturn(mockResult);

        Oc client = new Oc(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE).withTimeout(5L);
        client.newApp(templateName, params);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(5)));
        List<String> capturedCommand = listCaptor.getValue();

        List<String> expectedBase = new ArrayList<>(Arrays.asList(
            OC_CMD,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "new-app", templateName
        ));

        assertTrue(capturedCommand.containsAll(expectedBase), "Base command parts mismatch");
        assertTrue(capturedCommand.containsAll(Arrays.asList("-p", "PARAM1=VALUE1")) ||
            capturedCommand.containsAll(Arrays.asList("-p", "PARAM1=VALUE1".toLowerCase()))); // if params are sensitive
        assertTrue(capturedCommand.containsAll(Arrays.asList("-p", "PARAM2=VALUE2")) ||
            capturedCommand.containsAll(Arrays.asList("-p", "PARAM2=VALUE2".toLowerCase())));

        for (String paramKey : params.keySet()) {
            boolean found = false;
            for (int i = 0; i < capturedCommand.size() - 1; i++) {
                if (capturedCommand.get(i).equals("-p") &&
                    capturedCommand.get(i + 1).equals(paramKey + "=" + params.get(paramKey))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Parameter " + paramKey + " not found or not formatted correctly.");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNewAppWithNoParams() {
        String templateName = "simple-template";
        Map<String, String> params = Collections.emptyMap();
        ExecResult mockResult = mockSuccessfulExecResult("Application created");
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        Oc client = new Oc(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE);
        client.newApp(templateName, params);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();

        List<String> expectedCommandParts = Arrays.asList(
            OC_CMD,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "new-app", templateName
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetUsernameConstructsOcCommandAndReturnsOutput() {
        String expectedUsername = "testuser";
        ExecResult mockResult = mockSuccessfulExecResult(expectedUsername);
        mockedExec.when(() -> Exec.exec(anyList(), eq(10))).thenReturn(mockResult);

        Oc client = new Oc(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE).withTimeout(10);
        String actualUsername = client.getUsername();

        assertEquals(expectedUsername, actualUsername);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(10)));

        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedCommandParts = Arrays.asList(
            OC_CMD,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "whoami"
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCordonConstructsOcAdmCommand() {
        String nodeName = "oc-node-01";
        ExecResult mockResult = mockSuccessfulExecResult("");
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        Oc client = new Oc(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE);
        client.cordon(nodeName);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedCommandParts = Arrays.asList(
            OC_CMD,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "adm", "cordon", nodeName
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUncordonConstructsOcAdmCommand() {
        String nodeName = "oc-node-02";
        ExecResult mockResult = mockSuccessfulExecResult("");
        mockedExec.when(() -> Exec.exec(anyList(), eq(0))).thenReturn(mockResult);

        Oc client = new Oc(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE);
        client.uncordon(nodeName);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(0)));
        List<String> capturedCommand = listCaptor.getValue();
        List<String> expectedCommandParts = Arrays.asList(
            OC_CMD,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "adm", "uncordon", nodeName
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDrainConstructsOcAdmCommand() {
        String nodeName = "oc-node-to-drain";
        boolean ignoreDaemonSets = true;
        boolean disableEviction = true;
        long timeoutInSeconds = 60;

        ExecResult mockResult = mockSuccessfulExecResult("");
        mockedExec.when(() -> Exec.exec(anyList(), eq(10))).thenReturn(mockResult);

        Oc client = new Oc(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE).withTimeout(10);
        client.drain(nodeName, ignoreDaemonSets, disableEviction, timeoutInSeconds);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(10)));
        List<String> capturedCommand = listCaptor.getValue();

        List<String> expectedCommandParts = Arrays.asList(
            OC_CMD,
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "adm", "drain", nodeName,
            "--ignore-daemonsets", String.valueOf(ignoreDaemonSets),
            "--disable-eviction", String.valueOf(disableEviction),
            "--timeout", timeoutInSeconds + "s"
        );
        assertEquals(expectedCommandParts, capturedCommand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInheritedGetUsesOcCmd() {
        String resourceType = "route";
        String resourceName = "my-test-route";
        String expectedYaml = "apiVersion: route.openshift.io/v1\nkind: Route...";
        ExecResult mockResult = mockSuccessfulExecResult(expectedYaml);

        mockedExec.when(() -> Exec.exec(anyList(), eq(6))).thenReturn(mockResult);

        Oc client = new Oc(CUSTOM_CONFIG).inNamespace(DEFAULT_NAMESPACE).withTimeout(6);
        String actualYaml = client.get(resourceType, resourceName);

        assertEquals(expectedYaml, actualYaml);

        ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);
        mockedExec.verify(() -> Exec.exec(listCaptor.capture(), eq(6)));
        List<String> capturedCommand = listCaptor.getValue();

        assertTrue(capturedCommand.contains(OC_CMD), "Command should use 'oc'");
        List<String> expectedParts = Arrays.asList(
            "--kubeconfig", CUSTOM_CONFIG,
            "--namespace", DEFAULT_NAMESPACE,
            "get", resourceType, resourceName, "-o", "yaml"
        );
        expectedParts.forEach(part -> assertTrue(capturedCommand.contains(part)));
    }
}