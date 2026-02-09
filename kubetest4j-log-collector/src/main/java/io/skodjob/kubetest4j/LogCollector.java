/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Pod;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.clients.cmdClient.Kubectl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * LogCollector class containing all methods used for logs and YAML collection.
 */
public class LogCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogCollector.class);

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    protected final List<String> namespacedResources;
    protected final List<String> clusterWideResources;
    protected final boolean collectPreviousLogs;
    protected String rootFolderPath;
    private KubeCmdClient<?> kubeCmdClient = new Kubectl();
    private KubeClient kubeClient = new KubeClient();

    /**
     * Constructor of the {@link LogCollector}, which uses parameters from {@link LogCollectorBuilder}
     *
     * @param builder {@link LogCollectorBuilder} with configuration for {@link LogCollector}
     */
    public LogCollector(LogCollectorBuilder builder) {
        this.namespacedResources = builder.getNamespacedResources() == null ?
            Collections.emptyList() : builder.getNamespacedResources();
        this.clusterWideResources = builder.getClusterWideResources() == null ?
            Collections.emptyList() : builder.getClusterWideResources();
        this.collectPreviousLogs = builder.shouldCollectPreviousLogs();

        if (builder.getRootFolderPath() == null) {
            throw new RuntimeException("rootFolderPath should be filled, but it's empty");
        }

        if (builder.getKubeClient() != null) {
            this.kubeClient = builder.getKubeClient();
        }

        if (builder.getKubeCmdClient() != null) {
            this.kubeCmdClient = builder.getKubeCmdClient();
        }

        this.rootFolderPath = builder.getRootFolderPath();
    }

    /**
     * Method that collects all logs and YAML files from Namespaces containing specified LabelSelector, collected into
     * {@link #rootFolderPath}.
     *
     * @param labelSelector LabelSelector containing Labels that Namespace should contain
     */
    public void collectFromNamespacesWithLabels(LabelSelector labelSelector) {
        collectFromNamespacesWithLabelsToFolder(labelSelector, null);
    }

    /**
     * Method that collects all logs and YAML files from Namespaces containing specified LabelSelector, collected into
     * {@link #rootFolderPath} with {@param folderPath}.
     *
     * @param labelSelector LabelSelector containing Labels that Namespace should contain
     * @param folderPath    additional folder path for the log collection
     */
    public void collectFromNamespacesWithLabelsToFolder(LabelSelector labelSelector, String folderPath) {
        List<String> namespacesWithLabel = kubeClient.getClient()
            .namespaces()
            .withLabelSelector(labelSelector)
            .list()
            .getItems()
            .stream()
            .map(namespace -> namespace.getMetadata().getName())
            .toList();

        namespacesWithLabel.forEach(namespace -> collectFromNamespaceToFolder(namespace, folderPath));
    }

    /**
     * Method that collects all logs and YAML files from specified set of Namespaces, collected into
     * {@link #rootFolderPath}.
     *
     * @param namespacesNames set of Namespace from which the logs should be collected
     */
    public void collectFromNamespaces(String... namespacesNames) {
        collectFromNamespacesToFolder(Arrays.asList(namespacesNames), null);
    }

    /**
     * Method that collects all logs and YAML files from specified set of Namespaces, collected into
     * {@link #rootFolderPath} with {@param folderPath}.
     *
     * @param namespacesNames set of Namespace from which the logs should be collected
     * @param folderPath      additional folder path for the log collection
     */
    public void collectFromNamespacesToFolder(List<String> namespacesNames, String folderPath) {
        namespacesNames.forEach(namespace -> collectFromNamespaceToFolder(namespace, folderPath));
    }

    /**
     * Method that collects all logs and YAML files from specified Namespace, collected into
     * {@link #rootFolderPath}.
     *
     * @param namespaceName name of Namespace from which the logs should be collected
     */
    public void collectFromNamespace(String namespaceName) {
        collectFromNamespaceToFolder(namespaceName, null);
    }

    /**
     * Method that collects YAML of cluster wide resources
     * {@link #rootFolderPath}.
     */
    public void collectClusterWideResources() {
        collectClusterWideResources(true);
    }

    /**
     * Method that collects YAML of cluster wide resources
     * {@link #rootFolderPath}.
     *
     * @param logPerResource flag enables cluster wide resource per file
     */
    public void collectClusterWideResources(boolean logPerResource) {
        collectClusterWideResourcesToFolder(logPerResource, null);
    }

    /**
     * Method that collects all logs and YAML files from specified Namespace, collected into
     * {@link #rootFolderPath} with {@param folderPath}.
     *
     * @param namespaceName name of Namespace from which the logs should be collected
     * @param folderPath    additional folder path for the log collection
     */
    public void collectFromNamespaceToFolder(String namespaceName, String folderPath) {
        // check if Namespace exists
        if (kubeClient.getClient().namespaces().withName(namespaceName).get() != null) {
            String namespaceFolderPath = createNamespaceDirectory(namespaceName,
                LogCollectorUtils.getFolderPath(rootFolderPath, folderPath));

            List<CompletableFuture<Void>> collectList = new ArrayList<>(
                collectLogsFromPodsInNamespace(namespaceName, namespaceFolderPath)
            );
            collectList.add(
                CompletableFuture.runAsync(
                    () -> collectEventsFromNamespace(namespaceName, namespaceFolderPath),
                    EXECUTOR
                )
            );
            collectList.addAll(
                collectResourcesDescInNamespace(namespaceName, namespaceFolderPath)
            );

            CompletableFuture.allOf(collectList.toArray(new CompletableFuture[0])).join();
        } else {
            LOGGER.warn("Specified Namespace: {} doesn't exist", namespaceName);
        }
    }

    /**
     * Method that collects YAML of cluster wide resources
     * {@link #rootFolderPath} with {@param folderPath}.
     *
     * @param folderPath folder path for the log collection
     */
    public void collectClusterWideResourcesToFolder(String folderPath) {
        collectClusterWideResourcesToFolder(true, folderPath);
    }

    /**
     * Method that collects YAML of cluster wide resources
     * {@link #rootFolderPath} with {@param folderPath}.
     *
     * @param logPerFile flag enables cluster wide resource per file
     * @param folderPath folder path for the log collection
     */
    public void collectClusterWideResourcesToFolder(boolean logPerFile, String folderPath) {
        clusterWideResources.forEach(resourceType -> {
            LOGGER.info("Collecting YAMLs of {}", resourceType);

            String clusterWideFolderPath = createNamespaceDirectory(CollectorConstants.CLUSTER_WIDE_FOLDER,
                LogCollectorUtils.getFolderPath(rootFolderPath, folderPath));
            createLogDirOnPath(clusterWideFolderPath);

            if (logPerFile) {
                collectClusterWideResourcesPerFile(clusterWideFolderPath, resourceType);
            } else {
                String yaml = executeCollectionCall(
                    String.format("collect descriptions of type: %s", resourceType),
                    () -> kubeCmdClient.getResourcesAsYaml(resourceType)
                );
                String resFileName = LogCollectorUtils.getYamlFileNameForResource(resourceType);
                String filePath = LogCollectorUtils
                    .getFullPathForFolderPathAndFileName(clusterWideFolderPath, resFileName);

                writeDataToFile(filePath, yaml);
            }
        });
    }

    /**
     * Method for collecting logs from all Pods (and their containers) in specified Namespace.
     * At the start, it creates a folder in the Namespace dir for the `pod` resource.
     * After that it lists all Pods in the Namespace and collects names of all Containers and InitContainers.
     * Then, for each Pod-(Init)Container it collects logs and then creates a log file, where are the logs stored.
     * Additionally, it stores description of each Pod in the Namespace.
     * For running the collection asynchronously, it returns list of {@link CompletableFuture} containing
     * runnable for collection of each Pod found in the specified Namespace.
     *
     * @param namespaceName       name of Namespace from which the logs (and Pod descriptions) should be collected
     * @param namespaceFolderPath path to Namespace folder where the pods directory will be created and logs will be
     *                            collected into
     *
     * @return  list of {@link CompletableFuture} containing runnable for collection of each Pod
     * found in the specified Namespace.
     */
    public List<CompletableFuture<Void>> collectLogsFromPodsInNamespace(
        String namespaceName,
        String namespaceFolderPath
    ) {
        LOGGER.info("Collecting logs from all Pods in Namespace: {}", namespaceName);
        List<Pod> pods = executeCollectionCall(
            String.format("list Pods in Namespace: %s", namespaceName),
            () -> kubeClient.listPods(namespaceName)
        );
        List<CompletableFuture<Void>> collectList = new ArrayList<>();

        if (pods != null && !pods.isEmpty()) {
            String podsFolderPath = createResourceDirectoryInNamespaceDir(namespaceFolderPath, CollectorConstants.POD);

            pods.forEach(pod -> {
                String podName = pod.getMetadata().getName();

                List<String> containers = pod.getSpec().getContainers().stream().map(Container::getName).toList();
                List<String> initContainers = pod.getSpec().getInitContainers().stream()
                    .map(Container::getName).toList();

                collectList.addAll(
                    List.of(
                        CompletableFuture.runAsync(
                            () -> collectPodDescription(namespaceName, podsFolderPath, podName),
                            EXECUTOR
                        ),
                        CompletableFuture.runAsync(
                            () -> collectLogsFromPodContainers(namespaceName, podsFolderPath, podName, containers),
                            EXECUTOR
                        ),
                        CompletableFuture.runAsync(
                            () -> collectLogsFromPodContainers(namespaceName, podsFolderPath, podName, initContainers),
                            EXECUTOR
                        )
                    )
                );
            });
        }

        return collectList;
    }

    /**
     * Method that for each container collects the log using
     * {@link #collectLogsFromPodContainer(String, String, String, String)}
     *
     * @param namespaceName  name of Namespace where the Pod is present
     * @param podsFolderPath path to the "pod" folder (for example: /tmp/logs/namespace/pods)
     * @param podName        name of Pod from which the log should be collected
     * @param containerNames list of container names from which the log should be collected
     */
    private void collectLogsFromPodContainers(
        String namespaceName,
        String podsFolderPath,
        String podName,
        List<String> containerNames
    ) {
        containerNames.forEach(containerName ->
            collectLogsFromPodContainer(namespaceName, podsFolderPath, podName, containerName));
    }

    /**
     * Method that collects log from specified Pod and Container
     *
     * @param namespaceName  name of Namespace where the Pod is present
     * @param podsFolderPath path to the "pod" folder (for example: /tmp/logs/namespace/pods)
     * @param podName        name of Pod from which the log should be collected
     * @param containerName  name of container from which the log should be collected
     */
    private void collectLogsFromPodContainer(
        String namespaceName,
        String podsFolderPath,
        String podName,
        String containerName
    ) {
        String containerLog = executeCollectionCall(
            String.format("collecting logs from Pod: %s and container: %s", podName, containerName),
            () -> kubeCmdClient.inNamespace(namespaceName).logs(podName, containerName)
        );
        String podConLogFileName = LogCollectorUtils.getLogFileNameForPodContainer(podName, containerName);
        String filePath = LogCollectorUtils.getFullPathForFolderPathAndFileName(podsFolderPath, podConLogFileName);

        writeDataToFile(filePath, containerLog);

        if (collectPreviousLogs) {
            // check if container failed previously
            Pod pod = kubeClient.getClient().pods().inNamespace(namespaceName).withName(podName).get();

            if (pod != null
                && pod.getStatus() != null
                && pod.getStatus().getContainerStatuses() != null
            ) {
                Optional<ContainerStatus> containerStatus = pod.getStatus()
                    .getContainerStatuses()
                    .stream()
                    .filter(cs -> cs.getName().equals(containerName))
                    .findFirst();

                // in case that there is container status
                if (containerStatus.isPresent()) {
                    // check that the container was terminated
                    if (containerStatus.get().getLastState().getTerminated() != null) {
                        // collect previous logs for Pod and container
                        String previousContainerLog = executeCollectionCall(
                            String.format(
                                "collecting previous log from Pod: %s and container: %s",
                                podName, containerName
                            ),
                            () -> kubeCmdClient.inNamespace(namespaceName).previousLogs(podName, containerName)
                        );

                        String file = LogCollectorUtils.getLogFileNameForPreviousPodContainer(podName, containerName);
                        filePath = LogCollectorUtils.getFullPathForFolderPathAndFileName(podsFolderPath, file);

                        writeDataToFile(filePath, previousContainerLog);
                    }
                }
            }
        }
    }

    /**
     * Method that collects description of specified Pod
     *
     * @param namespaceName  name of Namespace where the Pod is present
     * @param podsFolderPath path to the "pod" folder (for example: /tmp/logs/namespace/pods)
     * @param podName        name of Pod from which the description should be collected
     */
    private void collectPodDescription(String namespaceName, String podsFolderPath, String podName) {
        String podDesc = executeCollectionCall(
            String.format("collect description of Pod:%s", podName),
            () -> kubeCmdClient.inNamespace(namespaceName).describe(CollectorConstants.POD, podName)
        );
        String podDescFileName = LogCollectorUtils.getLogFileNameForPodDescription(podName);
        String filePath = LogCollectorUtils.getFullPathForFolderPathAndFileName(podsFolderPath, podDescFileName);

        writeDataToFile(filePath, podDesc);
    }

    /**
     * Collect cluster wide resource per file
     *
     * @param clusterWideFolderPath root path of cluster wide resource
     * @param resourceType          resource kind for collect
     */
    private void collectClusterWideResourcesPerFile(String clusterWideFolderPath, String resourceType) {
        List<String> resources = kubeCmdClient.list(resourceType);
        if (resources != null && !resources.isEmpty()) {
            String fullFolderPath = createResourceDirectoryInNamespaceDir(clusterWideFolderPath, resourceType);

            resources.forEach(resourceName -> {
                String yaml = executeCollectionCall(
                    String.format("collect YAML description for %s:%s", resourceType, resourceName),
                    () -> kubeCmdClient.getResourceAsYaml(resourceType, resourceName)
                );

                String resFileName = LogCollectorUtils.getYamlFileNameForResource(resourceName);
                String fileName = LogCollectorUtils.getFullPathForFolderPathAndFileName(fullFolderPath, resFileName);
                writeDataToFile(fileName, yaml);
            });
        }
    }

    /**
     * Method that collects all Events (kubectl get events) from Namespace
     *
     * @param namespaceName       name of Namespace from which the events should be collected
     * @param namespaceFolderPath path to the Namespace folder (for example: /tmp/logs/namespace)
     */
    public void collectEventsFromNamespace(String namespaceName, String namespaceFolderPath) {
        LOGGER.info("Collecting events from Namespace: {}", namespaceName);
        String events = executeCollectionCall(
            String.format("collect %s from %s", CollectorConstants.EVENTS, namespaceName),
            () -> kubeCmdClient.inNamespace(namespaceName).getEvents()
        );
        String eventsFileName = LogCollectorUtils.getLogFileNameForResource(CollectorConstants.EVENTS);
        String fileName = LogCollectorUtils.getFullPathForFolderPathAndFileName(namespaceFolderPath, eventsFileName);

        writeDataToFile(fileName, events);
    }

    /**
     * Method that collects YAML descriptions of specified resources (resource types)
     * and stores them in particular folders and their YAML files.
     * For running the collection asynchronously, it returns list of {@link CompletableFuture} containing
     * runnable for collection of each resource specified in {@link #namespacedResources}.
     *
     * @param namespaceName       name of Namespace from where the YAMLs should be collected
     * @param namespaceFolderPath path to Namespace folder where the resource directories will be created
     *
     * @return list of {@link CompletableFuture} containing runnable for collection of each
     * resource specified in {@link #namespacedResources}.
     */
    private List<CompletableFuture<Void>> collectResourcesDescInNamespace(
        String namespaceName,
        String namespaceFolderPath
    ) {
        List<CompletableFuture<Void>> collectList = new ArrayList<>();

        namespacedResources.forEach(resource ->
            collectList.add(CompletableFuture.runAsync(
                () -> collectDescriptionOfResourceInNamespace(namespaceName, namespaceFolderPath, resource),
                EXECUTOR
            ))
        );

        return collectList;
    }


    /**
     * Method that collects YAML descriptions for specified resource type (for example secret, configmap, ...).
     * It firstly lists that, in specified Namespace, there are resources with that type.
     * When the resources exist in Namespace, the folder for the resource type is created.
     * Finally, for each resource the YAML description is collected and stored in YAML file inside the type's folder.
     *
     * @param namespaceName       name of Namespace from where the YAMLs should be collected
     * @param namespaceFolderPath path to Namespace folder where the resource directory will be created
     * @param resourceType        name of the resource type (for example secret, configmap, ...)
     */
    private void collectDescriptionOfResourceInNamespace(
        String namespaceName,
        String namespaceFolderPath,
        String resourceType
    ) {
        LOGGER.info("Collecting YAMLs of {} from Namespace: {}", resourceType, namespaceName);
        List<String> resources = executeCollectionCall(
            String.format("list resources of type: %s in Namespace: %s", resourceType, namespaceName),
            () -> kubeCmdClient.inNamespace(namespaceName).list(resourceType)
        );

        if (resources != null && !resources.isEmpty()) {
            String fullFolderPath = createResourceDirectoryInNamespaceDir(namespaceFolderPath, resourceType);

            resources.forEach(resourceName -> {
                String yaml = executeCollectionCall(
                    String.format("collect YAML description for %s:%s", resourceType, resourceName),
                    () -> kubeCmdClient.inNamespace(namespaceName).getResourceAsYaml(resourceType, resourceName)
                );

                String resFileName = LogCollectorUtils.getYamlFileNameForResource(resourceName);
                String fileName = LogCollectorUtils.getFullPathForFolderPathAndFileName(fullFolderPath, resFileName);
                writeDataToFile(fileName, yaml);
            });
        }
    }

    /**
     * Method that creates directory for specified Namespace in the {@param folderPath}
     *
     * @param namespaceName name of Namespace for which the folder will be created
     * @param folderPath    folder path where the Namespace directory should be created
     * @return full path to the Namespace directory
     */
    private String createNamespaceDirectory(String namespaceName, String folderPath) {
        return createLogDirOnPath(LogCollectorUtils.getFullDirPathWithNamespace(folderPath, namespaceName));
    }

    /**
     * Method that creates directory for specified resource type in the Namespace directory
     *
     * @param namespaceFolderPath path to Namespace directory where the directory for the resource type
     *                            will be created
     * @param resourceType        name of resource type for which the directory will be created
     * @return full path to the resource type's directory
     */
    private String createResourceDirectoryInNamespaceDir(String namespaceFolderPath, String resourceType) {
        return createLogDirOnPath(
            LogCollectorUtils.getNamespaceFullDirPathForResourceType(
                namespaceFolderPath,
                resourceType
            )
        );
    }

    /**
     * Method that creates directory in path.
     * It firstly checks if the directory exists or not and if not, it creates all the directories that are missing.
     *
     * @param fullPathToDirectory full path to the desired directory
     * @return path to newly created directory
     */
    private String createLogDirOnPath(String fullPathToDirectory) {
        File logDir = Paths.get(fullPathToDirectory).toFile();

        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                throw new RuntimeException(
                    String.format("Failed to create root log directories on path: %s", logDir.getAbsolutePath())
                );
            }
        }

        return logDir.getAbsolutePath();
    }

    /**
     * Method that writes data to file (on path, specified by {@param fullFilePath}.
     *
     * @param fullFilePath full path to file (for example: /tmp/logs/my-namespace/secret.yaml)
     * @param data         data which should be written to file
     */
    /** test **/ protected void writeDataToFile(String fullFilePath, String data) {
        if (data != null && !data.isEmpty()) {
            try {
                Files.writeString(Paths.get(fullFilePath), data, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(
                    String.format("Failed to write to the %s file due to: %s", fullFilePath, e.getMessage())
                );
            }
        }
    }

    /**
     * Method for executing the collection (or list) call, which handles the exceptions when the resource is not found
     * (or was removed during the process). That way the LogCollector will continue with collection of other resources.
     *
     * @param errorOperationMessage message for the operation that is being executed, for error logging
     * @param executeCall           Supplier with the lambda method for collection of the data
     * @param <T>                   type of the return value -> same as the return type of the executed call
     * @return result of the execution of the method -> YAML descriptions, logs, or lists of resources
     */
    private <T> T executeCollectionCall(String errorOperationMessage, Supplier<T> executeCall) {
        try {
            return executeCall.get();
        } catch (Exception e) {
            LOGGER.warn("Failed to {}, due to: {}", errorOperationMessage, e.getMessage());
            return null;
        }
    }
}
