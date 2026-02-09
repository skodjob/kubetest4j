/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.NamespaceListBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.clients.cmdClient.Kubectl;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestVisualSeparator
@SuppressWarnings("unchecked")
final class LogCollectorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogCollectorTest.class);

    private KubeClient mockClient;
    private KubeCmdClient<Kubectl> mockCmdClient;
    private NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> mockNamespaceOperation;
    private MixedOperation<Pod, PodList, PodResource> mockPodOperation;
    private final KubernetesClient mockKubernetesClient = mock(KubernetesClient.class);

    private static final String SECRET = "secret";
    private static final String CONFIG_MAP = "configmap";
    private static final String DEPLOYMENT = "deployment";

    private final String pathToRoot = "/tmp/log-collector-tests";
    private int testCounter = 0;
    private LogCollector logCollector;

    @BeforeAll
    void setup() {
        mockClient = mock(KubeClient.class);
        mockCmdClient = mock(KubeCmdClient.class);
        mockNamespaceOperation = mock(NonNamespaceOperation.class);

        logCollector = new LogCollectorBuilder()
            .withNamespacedResources(SECRET, DEPLOYMENT, CONFIG_MAP)
            .withRootFolderPath(pathToRoot)
            .withKubeCmdClient(mockCmdClient)
            .withKubeClient(mockClient)
            .build();
    }

    @BeforeEach
    void setRootPathForCollector() {
        mockPodOperation = mock(MixedOperation.class);

        when(mockKubernetesClient.namespaces()).thenReturn(mockNamespaceOperation);
        when(mockCmdClient.inNamespace(anyString())).thenReturn(mockCmdClient);
        when(mockClient.getClient()).thenReturn(mockKubernetesClient);
        when(mockKubernetesClient.pods()).thenReturn(mockPodOperation);

        logCollector = new LogCollectorBuilder(logCollector)
            .withRootFolderPath(getFolderPathForTest())
            .withKubeCmdClient(mockCmdClient)
            .withKubeClient(mockClient)
            .build();
    }

    /**
     * Checks "happy path" where we have single Namespace with resources, Pods, and events.
     */
    @Test
    void testCollectFromNamespaceWhichAreFilledWithResources() {
        String namespaceName = "my-happy-namespace";
        String[] secretNames = new String[]{"secret1", "secret2"};
        String[] deploymentNames = new String[]{"deployment1", "deployment2"};
        String[] configMapNames = new String[]{"conf1", "conf2"};
        String[] podNames = new String[]{"pod1", "pod2"};

        mockNamespaces(namespaceName);
        mockEvents();
        mockSecrets(namespaceName, secretNames);
        mockDeployments(namespaceName, deploymentNames);
        mockConfigMaps(namespaceName, configMapNames);
        mockPods(namespaceName, true, true, podNames);

        logCollector.collectFromNamespace(namespaceName);

        File rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertFolderForResourceTypeExistsAndContainsFiles(namespaceFolder, SECRET, secretNames);
        assertFolderForResourceTypeExistsAndContainsFiles(namespaceFolder, DEPLOYMENT, deploymentNames);
        assertFolderForResourceTypeExistsAndContainsFiles(namespaceFolder, CONFIG_MAP, configMapNames);
        assertPodFolderContainsEverything(namespaceFolder, true, true, podNames);
        assertNamespaceFolderContainsEventsLog(namespaceFolder);
    }

    @Test
    void testCollectFromNamespaceWhichIsEmptyWithAdditionalFolder() {
        doTestCollectFromNamespaceWhichIsEmpty("additional");
    }

    @Test
    void testCollectFromNamespaceWhichIsEmptyWithoutAdditionalFolder() {
        doTestCollectFromNamespaceWhichIsEmpty(null);
    }

    void doTestCollectFromNamespaceWhichIsEmpty(String folderName) {
        String namespaceName = "my-namespace-jeje";

        mockNamespaces(namespaceName);
        mockEvents();

        logCollector.collectFromNamespace(namespaceName);

        if (folderName == null) {
            logCollector.collectFromNamespace(namespaceName);
        } else {
            logCollector.collectFromNamespaceToFolder(namespaceName, folderName);
        }

        File rootFolder = Paths.get(LogCollectorUtils.getFolderPath(getFolderPathForTest(), folderName)).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertFolderExistsAndIsNotEmpty(namespaceFolder);
        assertNamespaceFolderContainsEventsLog(namespaceFolder);
    }

    @Test
    void testCollectFromNamespacesWhichAreEmptyWithAdditionalFolder() {
        doTestCollectFromNamespacesWhichAreEmpty("additional");
    }

    @Test
    void testCollectFromNamespacesWhichAreEmptyWithoutAdditionalFolder() {
        doTestCollectFromNamespacesWhichAreEmpty(null);
    }

    void doTestCollectFromNamespacesWhichAreEmpty(String folderName) {
        String namespaceName1 = "my-namespace-tudu";
        String namespaceName2 = "second-namespace";

        mockNamespaces(namespaceName1, namespaceName2);
        mockEvents();

        if (folderName == null) {
            logCollector.collectFromNamespaces(namespaceName1, namespaceName2);
        } else {
            logCollector.collectFromNamespacesToFolder(List.of(namespaceName1, namespaceName2), folderName);
        }

        File rootFolder = Paths.get(LogCollectorUtils.getFolderPath(getFolderPathForTest(), folderName)).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 2);
        assertFolderContainsFolders(rootFolder, namespaceName1, namespaceName2);

        Arrays.asList(rootFolder.listFiles()).forEach(namespaceFolder -> {
            assertFolderExistsAndIsNotEmpty(namespaceFolder);
            assertNamespaceFolderContainsEventsLog(namespaceFolder);
        });
    }

    @Test
    void testCollectFromNamespacesWithLabelsWithAdditionalFolder() {
        doTestCollectFromNamespaceWithLabels("additional");
    }

    @Test
    void testCollectFromNamespacesWithLabelsWithoutAdditionalFolder() {
        doTestCollectFromNamespaceWithLabels(null);
    }

    void doTestCollectFromNamespaceWithLabels(String folderName) {
        String namespaceName1 = "my-namespace-blah";
        String namespaceName2 = "second-namespace";
        Map<String, String> labels = Map.of("first", "label");

        mockNamespaces(namespaceName1);
        mockEvents();
        mockNamespacesWithLabels(labels, namespaceName2);

        LabelSelector labelSelector = new LabelSelectorBuilder()
            .withMatchLabels(labels)
            .build();

        if (folderName == null) {
            logCollector.collectFromNamespacesWithLabels(labelSelector);
        } else {
            logCollector.collectFromNamespacesWithLabelsToFolder(labelSelector, folderName);
        }

        File rootFolder = Paths.get(LogCollectorUtils.getFolderPath(getFolderPathForTest(), folderName)).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName2);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertFolderExistsAndIsNotEmpty(namespaceFolder);
        assertNamespaceFolderContainsEventsLog(namespaceFolder);
    }

    @Test
    void testCollectFromNonExistentNamespace() {
        String nonExistentNamespace = "not-the-right-namespace";
        String namespaceName = "my-namespace";

        mockNamespaces(namespaceName);

        logCollector.collectFromNamespace(nonExistentNamespace);

        File rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFalse(rootFolder.exists());
    }

    @Test
    void testChangingCustomResources() throws IOException {
        LogCollector localLogCollector = new LogCollectorBuilder()
            .withRootFolderPath(getFolderPathForTest())
            .withNamespacedResources(SECRET)
            .withKubeClient(mockClient)
            .withKubeCmdClient(mockCmdClient)
            .build();

        String namespaceName = "my-namespace";
        String[] secretNames = new String[]{"secret1", "secret2"};
        String[] deploymentNames = new String[]{"deployment1", "deployment2"};
        String[] configMapNames = new String[]{"conf1", "conf2"};

        mockNamespaces(namespaceName);
        mockEvents();
        mockSecrets(namespaceName, secretNames);
        mockDeployments(namespaceName, deploymentNames);
        mockConfigMaps(namespaceName, configMapNames);

        localLogCollector.collectFromNamespace(namespaceName);

        File rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertFolderExistsAndContainsCorrectNumberOfFiles(namespaceFolder, 2);
        assertFolderForResourceTypeExistsAndContainsFiles(namespaceFolder, SECRET, secretNames);
        assertNamespaceFolderContainsEventsLog(namespaceFolder);

        cleanUp();

        localLogCollector = new LogCollectorBuilder(localLogCollector)
            .withRootFolderPath(getFolderPathForTest())
            .withNamespacedResources(CONFIG_MAP, DEPLOYMENT)
            .withKubeClient(mockClient)
            .withKubeCmdClient(mockCmdClient)
            .build();

        localLogCollector.collectFromNamespace(namespaceName);

        rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        namespaceFolder = rootFolder.listFiles()[0];

        assertFolderExistsAndContainsCorrectNumberOfFiles(namespaceFolder, 3);
        assertFolderForResourceTypeExistsAndContainsFiles(namespaceFolder, DEPLOYMENT, deploymentNames);
        assertFolderForResourceTypeExistsAndContainsFiles(namespaceFolder, CONFIG_MAP, configMapNames);
        assertNamespaceFolderContainsEventsLog(namespaceFolder);
    }

    @Test
    void testCollectingLogsFromContainerWithoutInitContainers() {
        String namespaceName = "my-namespace";
        String[] podNames = new String[]{"pod1", "pod2"};

        mockNamespaces(namespaceName);
        mockEvents();
        mockPods(namespaceName, false, false, podNames);

        logCollector.collectFromNamespace(namespaceName);

        File rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertPodFolderContainsEverything(namespaceFolder, false, false, podNames);
        assertNamespaceFolderContainsEventsLog(namespaceFolder);
    }

    @Test
    void testCollectingWithEmptyListOfCustomResources() {
        String namespaceName = "my-namespace-1";
        String[] secretNames = new String[]{"secret1", "secret2"};
        String[] deploymentNames = new String[]{"deployment1", "deployment2"};

        LogCollector localLogCollector = new LogCollectorBuilder()
            .withRootFolderPath(getFolderPathForTest())
            .withKubeClient(mockClient)
            .withKubeCmdClient(mockCmdClient)
            .build();

        mockNamespaces(namespaceName);
        mockEvents();
        mockSecrets(namespaceName, secretNames);
        mockDeployments(namespaceName, deploymentNames);

        localLogCollector.collectFromNamespace(namespaceName);

        File rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertFolderExistsAndContainsCorrectNumberOfFiles(namespaceFolder, 1);
        assertNamespaceFolderContainsEventsLog(namespaceFolder);
    }

    @Test
    void testCannotWriteToFiles() {
        String namespaceName = "my-namespace-2";
        mockNamespaces(namespaceName);
        mockEvents();

        LogCollector spyCollector = spy(logCollector);

        doThrow(new RuntimeException()).when(spyCollector).writeDataToFile(any(), anyString());

        assertThrows(Exception.class, () -> spyCollector.collectFromNamespace(namespaceName));
    }

    @Test
    void testExceptionDuringResourceCollection() {
        String namespaceName = "my-namespace-3";
        mockNamespaces(namespaceName);
        mockSecrets(namespaceName, "my-secret");

        when(mockClient.listPods(any())).thenThrow(new KubernetesClientException("Failed to obtain the resource"));
        when(mockCmdClient.getResourceAsYaml(any(), any()))
            .thenThrow(new KubernetesClientException("Failed to get description of resource"));

        assertDoesNotThrow(() -> logCollector.collectFromNamespace(namespaceName));
    }

    @Test
    void testCollectFromFailedPodsWithCollectPreviousPodsEnabled() {
        LogCollector localLogCollector = new LogCollectorBuilder(logCollector)
            .withKubeClient(mockClient)
            .withKubeCmdClient(mockCmdClient)
            .withCollectPreviousLogs()
            .build();

        String namespaceName = "failed-pods-namespace";
        String failedPod = "bad";

        mockNamespaces(namespaceName);
        mockPods(namespaceName, true, true, true, failedPod);

        localLogCollector.collectFromNamespace(namespaceName);

        File rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertPodFolderContainsEverything(namespaceFolder, true, true, failedPod);
        assertPodFolderContainsPreviousLogs(namespaceFolder, failedPod, "failed", true);
    }

    @Test
    void testCollectFromSuccessfulPodsWithCollectPreviousPodsEnabled() {
        LogCollector localLogCollector = new LogCollectorBuilder(logCollector)
            .withKubeClient(mockClient)
            .withKubeCmdClient(mockCmdClient)
            .withCollectPreviousLogs()
            .build();

        String namespaceName = "successful-pods-namespace";
        String failedPod = "my-pod";

        mockNamespaces(namespaceName);
        mockPods(namespaceName, true, true, false, failedPod);

        localLogCollector.collectFromNamespace(namespaceName);

        File rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertPodFolderContainsEverything(namespaceFolder, true, true, failedPod);
        assertPodFolderContainsPreviousLogs(namespaceFolder, namespaceName, null, true);
    }

    @Test
    void testCollectFromFailedPodsWithCollectPreviousPodsDisabled() {
        LogCollector localLogCollector = new LogCollectorBuilder(logCollector)
            .withKubeClient(mockClient)
            .withKubeCmdClient(mockCmdClient)
            .withCollectPreviousLogs(false)
            .build();

        String namespaceName = "failed-pods-namespace";
        String failedPod = "bad";

        mockNamespaces(namespaceName);
        mockPods(namespaceName, true, true, true, failedPod);

        localLogCollector.collectFromNamespace(namespaceName);

        File rootFolder = Paths.get(getFolderPathForTest()).toFile();

        assertFolderExistsAndContainsCorrectNumberOfFiles(rootFolder, 1);
        assertFolderContainsFolders(rootFolder, namespaceName);

        File namespaceFolder = rootFolder.listFiles()[0];

        assertPodFolderContainsEverything(namespaceFolder, true, true, failedPod);
        assertPodFolderContainsPreviousLogs(namespaceFolder, failedPod, "failed", false);
    }

    @AfterEach
    void cleanAndUpdate() {
        reset(mockCmdClient, mockClient);
        testCounter++;
    }

    @AfterAll
    void cleanUp() throws IOException {
        // remove the directories created by the tests to clean-up everything
        FileUtils.deleteDirectory(Paths.get(pathToRoot).toFile());
    }

    private String getFolderPathForTest() {
        return String.join("/", pathToRoot, "test", String.valueOf(testCounter));
    }

    private void mockNamespaces(String... namespaces) {
        when(mockNamespaceOperation.withName(anyString())).thenAnswer((Answer<Resource<Namespace>>) invocation -> {
            String namespaceName = invocation.getArgument(0);
            Resource<Namespace> mockNamespace = mock(Resource.class);

            if (Arrays.asList(namespaces).contains(namespaceName)) {
                Namespace namespace = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(namespaceName)
                    .endMetadata()
                    .build();

                when(mockNamespace.get()).thenReturn(namespace);
            }

            return mockNamespace;
        });
    }

    private void mockNamespacesWithLabels(Map<String, String> labels, String... namespaces) {
        List<Namespace> listOfNamespaces = new ArrayList<>();

        Arrays.asList(namespaces).forEach(namespace ->
            listOfNamespaces.add(new NamespaceBuilder()
                .withNewMetadata()
                .withName(namespace)
                .withLabels(labels)
                .endMetadata()
                .build())
        );

        NamespaceList namespaceList = new NamespaceListBuilder()
            .withItems(listOfNamespaces)
            .build();

        when(mockNamespaceOperation.withLabelSelector(any(LabelSelector.class))).thenAnswer(
            (Answer<FilterWatchListDeletable<Namespace, NamespaceList, Resource<Namespace>>>) invocation -> {
                LabelSelector labelSelector = invocation.getArgument(0);
                FilterWatchListDeletable<Namespace, NamespaceList, Resource<Namespace>> mockFilterWatchList =
                    mock(FilterWatchListDeletable.class);

                if (labelSelector.getMatchLabels().equals(labels)) {
                    when(mockFilterWatchList.list()).thenReturn(namespaceList);
                }

                return mockFilterWatchList;
            });

        when(mockNamespaceOperation.withName(anyString())).thenAnswer((Answer<Resource<Namespace>>) invocation -> {
            String namespaceName = invocation.getArgument(0);
            Resource<Namespace> mockNamespace = mock(Resource.class);

            if (Arrays.asList(namespaces).contains(namespaceName)) {
                when(mockNamespace.get()).thenReturn(listOfNamespaces.stream()
                    .filter(nspc -> nspc.equals(namespaceName)).findFirst().orElse(new Namespace()));
            }

            return mockNamespace;
        });
    }

    private void mockPods(
        String namespaceName,
        boolean withInitContainers,
        boolean withMultipleContainers,
        String... podNames
    ) {
        mockPods(namespaceName, withInitContainers, withMultipleContainers, false, podNames);
    }

    private void mockPods(
        String namespaceName,
        boolean withInitContainers,
        boolean withMultipleContainers,
        boolean withFailedContainer,
        String... podNames
    ) {
        List<Pod> pods = new ArrayList<>();

        for (String podName : podNames) {
            Container exampleContainer = new ContainerBuilder()
                .withName(podName)
                .withImage("random")
                .build();

            PodBuilder mockPodBuilder = new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .withNamespace(namespaceName)
                .endMetadata()
                .editOrNewSpec()
                .withContainers(exampleContainer)
                .endSpec();

            if (withInitContainers) {
                Container initContainer = new ContainerBuilder(exampleContainer)
                    .withName("init-" + podName)
                    .build();

                mockPodBuilder
                    .editOrNewSpec()
                    .withInitContainers(initContainer)
                    .endSpec();
            }

            if (withMultipleContainers) {
                Container anotherContainer = new ContainerBuilder(exampleContainer)
                    .withName("second-" + podName)
                    .build();

                mockPodBuilder
                    .editOrNewSpec()
                    .addToContainers(anotherContainer)
                    .endSpec();
            }

            if (withFailedContainer) {
                Container failedContainer = new ContainerBuilder(exampleContainer)
                    .withName("failed")
                    .build();

                mockPodBuilder
                    .editOrNewSpec()
                        .addToContainers(failedContainer)
                    .endSpec()
                    .editOrNewStatus()
                        .addToContainerStatuses(
                            new ContainerStatusBuilder()
                                .withName(failedContainer.getName())
                                .withNewLastState()
                                    .withNewTerminated()
                                        .withExitCode(1)
                                        .withReason("Wrong image")
                                    .endTerminated()
                                .endLastState()
                                .build()
                        )
                    .endStatus();
            }

            Pod mockPod = mockPodBuilder.build();
            pods.add(mockPod);

            NonNamespaceOperation<Pod, PodList, PodResource> nonNamespaceOp = mock(NonNamespaceOperation.class);
            PodResource mockPodResource = mock(PodResource.class);

            when(mockPodOperation.inNamespace(namespaceName)).thenReturn(nonNamespaceOp);
            when(nonNamespaceOp.withName(podName)).thenReturn(mockPodResource);
            when(mockPodResource.get()).thenReturn(mockPod);
            when(mockCmdClient.inNamespace(namespaceName).describe(CollectorConstants.POD, podName))
                .thenReturn("this is description of " + podName);
            when(mockCmdClient.inNamespace(namespaceName).logs(any(), any()))
                .thenAnswer((Answer<String>) invocation -> {
                    String pod = invocation.getArgument(0);
                    String container = invocation.getArgument(1);

                    return "this is log for pod: " + pod + " and container: " + container;
                });
            when(mockCmdClient.inNamespace(namespaceName).previousLogs(any(), any()))
                .thenAnswer((Answer<String>) invocation -> {
                    String pod = invocation.getArgument(0);
                    String container = invocation.getArgument(1);

                    return "this is previous log for pod: " + pod + " and container: " + container;
                });
        }

        when(mockClient.listPods(namespaceName)).thenReturn(pods);
    }

    private void mockSecrets(String namespaceName, String... secretNames) {
        List<String> secretNamesList = Arrays.asList(secretNames);

        for (String secretName : secretNames) {
            when(mockCmdClient.inNamespace(namespaceName).getResourceAsYaml(SECRET, secretName))
                .thenReturn("this is description of Secret: " + secretName);
        }

        when(mockCmdClient.inNamespace(namespaceName).list(SECRET)).thenReturn(secretNamesList);
    }

    private void mockConfigMaps(String namespaceName, String... configMapNames) {
        List<String> configMapNamesList = Arrays.asList(configMapNames);

        for (String configName : configMapNames) {
            when(mockCmdClient.inNamespace(namespaceName).getResourceAsYaml(CONFIG_MAP, configName))
                .thenReturn("this is description of ConfigMap: " + configName);
        }

        when(mockCmdClient.inNamespace(namespaceName).list(CONFIG_MAP)).thenReturn(configMapNamesList);
    }

    private void mockDeployments(String namespaceName, String... deploymentNames) {
        List<String> deploymentNamesList = Arrays.asList(deploymentNames);

        for (String deploymentName : deploymentNames) {
            when(mockCmdClient.inNamespace(namespaceName).getResourceAsYaml(anyString(), anyString()))
                .thenReturn("this is description of Deployment: " + deploymentName);
        }

        when(mockCmdClient.inNamespace(namespaceName).list(DEPLOYMENT)).thenReturn(deploymentNamesList);
    }

    private void mockEvents() {
        when(mockCmdClient.inNamespace(anyString()).getEvents()).thenReturn("these are events from this namespace");
    }

    private void assertNamespaceFolderContainsEventsLog(File namespaceFolder) {
        assertTrue(Arrays.asList(namespaceFolder.list()).contains("events.log"));
    }

    private void assertFolderForResourceTypeExistsAndContainsFiles(File namespaceFolder, String resourceType,
                                                                   String... resourceNames) {
        File resourceFolder = Arrays.stream(namespaceFolder.listFiles())
            .filter(file -> file.getName().equals(resourceType)).findFirst().get();

        assertFolderExistsAndIsNotEmpty(resourceFolder);
        assertFolderContainsCorrectResourceFiles(resourceFolder, resourceNames);
    }

    private void assertFolderExistsAndIsNotEmpty(File folder) {
        assertNotNull(folder);
        assertTrue(Objects.requireNonNull(folder.list()).length != 0);
    }

    private void assertFolderExistsAndContainsCorrectNumberOfFiles(File folder, int expectedNumOfFiles) {
        assertNotNull(folder);
        assertEquals(expectedNumOfFiles, Objects.requireNonNull(folder.list()).length);
    }

    private void assertFolderContainsFolders(File folder, String... folders) {
        List<String> expectedFolderNames = Arrays.asList(folders);
        List<String> namesOfFolders = Arrays.stream(folder.listFiles())
            .filter(File::isDirectory)
            .map(File::getName)
            .toList();

        assertTrue(namesOfFolders.containsAll(expectedFolderNames));
    }

    private void assertFolderContainsCorrectResourceFiles(File folder, String... resourceNames) {
        List<String> folderFiles = Arrays.asList(Objects.requireNonNull(folder.list()));
        List<String> expectedFiles = Arrays.stream(resourceNames).map(it -> it + ".yaml").toList();

        assertTrue(folderFiles.containsAll(expectedFiles));
    }

    private void assertPodFolderContainsEverything(
        File namespaceFolder,
        boolean withInitContainers,
        boolean withMultipleContainers,
        String... podNames
    ) {
        List<String> podFiles = Arrays.asList(Arrays.stream(namespaceFolder.listFiles())
            .filter(file -> file.getName().equals(CollectorConstants.POD))
            .findFirst()
            .get()
            .list());

        for (String podName : podNames) {
            assertTrue(podFiles.contains(LogCollectorUtils.getLogFileNameForPodDescription(podName)));
            assertTrue(podFiles.contains(LogCollectorUtils.getLogFileNameForPodContainer(podName, podName)));

            if (withInitContainers) {
                assertTrue(podFiles.contains(
                    LogCollectorUtils.getLogFileNameForPodContainer(podName, "init-" + podName)));
            }

            if (withMultipleContainers) {
                assertTrue(podFiles.contains(
                    LogCollectorUtils.getLogFileNameForPodContainer(podName, "second-" + podName)));
            }
        }
    }

    private void assertPodFolderContainsPreviousLogs(
        File namespaceFolder,
        String podName,
        String failedContainerName,
        boolean collectPreviousPodsEnabled
    ) {
        List<String> podFiles = Arrays.asList(Arrays.stream(namespaceFolder.listFiles())
            .filter(file -> file.getName().equals(CollectorConstants.POD))
            .findFirst()
            .get()
            .list());

        LOGGER.info("Pod log files in directory: {}",
            podFiles.toString().replaceAll(",", "\n").replaceAll("\\[|\\]", ""));

        List<String> previousLogFiles = podFiles
            .stream()
            .filter(file -> file.contains(CollectorConstants.PREVIOUS))
            .toList();

        String prevFileName = LogCollectorUtils.getLogFileNameForPreviousPodContainer(podName, failedContainerName);
        if (failedContainerName != null && collectPreviousPodsEnabled) {
            assertEquals(previousLogFiles.size(), 1);
            assertTrue(podFiles.contains(prevFileName));
        } else {
            assertEquals(previousLogFiles.size(), 0);
            assertFalse(podFiles.contains(prevFileName));
        }
    }
}
