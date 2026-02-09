/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients.cmdClient;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.skodjob.kubetest4j.enums.LogLevel;
import io.skodjob.kubetest4j.executor.ExecResult;

/**
 * Abstraction for a Kubernetes client.
 *
 * @param <K> The subtype of KubeClient, for fluency.
 */
public interface KubeCmdClient<K extends KubeCmdClient<K>> {

    /**
     * Retrieves the default OLM (Operator Lifecycle Manager) namespace for the Kubernetes client.
     *
     * @return The default OLM namespace.
     */
    String defaultOlmNamespace();

    /**
     * Deletes resources by resource name.
     *
     * @param resourceType The type of the resource to delete.
     * @param resourceName The name of the resource to delete.
     * @return This kube client.
     */
    K deleteByName(String resourceType, String resourceName);

    /**
     * Sets the namespace for subsequent operations.
     *
     * @param namespace The namespace to set.
     * @return This kube client.
     */
    KubeCmdClient<K> inNamespace(String namespace);


    /**
     * Sets the timeout for later operations.
     *
     * @param timeout timeout in ms for execution of command.
     * @return This kube client.
     */
    KubeCmdClient<K> withTimeout(int timeout);

    /**
     * Sets the timeout for later operations.
     *
     * @param timeout timeout in ms for execution of command.
     * @return This kube client.
     */
    KubeCmdClient<K> withTimeout(long timeout);

    /**
     * Retrieves the currently set namespace for the Kubernetes client.
     *
     * @return The currently set namespace.
     */
    String getCurrentNamespace();

    /**
     * Creates resources from the provided files.
     *
     * @param files The files containing resource definitions.
     * @return This kube client.
     */
    K create(File... files);

    /**
     * Applies resource changes from the provided files.
     *
     * @param files The files containing resource changes.
     * @return This kube client.
     */
    K apply(File... files);

    /**
     * Deletes resources specified in the provided files.
     *
     * @param files The files containing resources to delete.
     * @return This kube client.
     */
    K delete(File... files);

    /**
     * Creates resources from YAML files specified by file paths.
     *
     * @param files The paths to YAML files containing resource definitions.
     * @return This kube client.
     */
    default K create(String... files) {
        return create(Arrays.stream(files).map(File::new).toArray(File[]::new));
    }

    /**
     * Applies resource changes from YAML files specified by file paths.
     *
     * @param files The paths to YAML files containing resource changes.
     * @return This kube client.
     */
    default K apply(String... files) {
        return apply(Arrays.stream(files).map(File::new).toArray(File[]::new));
    }

    /**
     * Deletes resources specified in YAML files specified by file paths.
     *
     * @param files The paths to YAML files containing resources to delete.
     * @return This kube client.
     */
    default K delete(String... files) {
        return delete(Arrays.stream(files).map(File::new).toArray(File[]::new));
    }

    /**
     * Replaces resources with the contents of the provided files.
     *
     * @param files The files containing resources to replace.
     * @return This kube client.
     */
    K replace(File... files);

    /**
     * Applies resource content.
     *
     * @param yamlContent The YAML content representing the resources.
     * @return This kube client.
     */
    K applyContent(String yamlContent);

    /**
     * Replaces resource content
     * @param yamlContent The YAML content representing the resources.
     * @return This kube client.
     */
    K replaceContent(String yamlContent);

    /**
     * Deletes resource content.
     *
     * @param yamlContent The YAML content representing the resources to delete.
     * @return This kube client.
     */
    K deleteContent(String yamlContent);

    /**
     * Creates a namespace with the given name.
     *
     * @param name The name of the namespace to create.
     * @return This kube client.
     */
    K createNamespace(String name);

    /**
     * Deletes the namespace with the given name.
     *
     * @param name The name of the namespace to delete.
     * @return This kube client.
     */
    K deleteNamespace(String name);

    /**
     * Scales a resource by its kind and name.
     *
     * @param kind     The kind of the resource.
     * @param name     The name of the resource.
     * @param replicas The number of replicas.
     * @return This kube client.
     */
    K scaleByName(String kind, String name, int replicas);

    /**
     * Executes a command within a pod.
     *
     * @param pod     The name of the pod.
     * @param command The command to execute.
     * @return The execution result.
     */
    ExecResult execInPod(String pod, String... command);

    /**
     * Executes a command within a pod.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param logLevel  Desired log level where the log
     *                  from the command execution should be printed.
     * @param pod       The name of the pod.
     * @param command   The command to execute.
     *
     * @return The execution result.
     */
    ExecResult execInPod(LogLevel logLevel, String pod, String... command);

    /**
     * Executes a command within a pod.
     *
     * @param throwError     Whether to throw errors.
     * @param pod            The name of the pod.
     * @param command        The command to execute.
     * @return The execution result.
     */
    ExecResult execInPod(boolean throwError, String pod, String... command);

    /**
     * Executes a command within a pod.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param throwError     Whether to throw errors.
     * @param logLevel       Desired log level where the log
     *                       from the command execution should be printed.
     * @param pod            The name of the pod.
     * @param command        The command to execute.
     * @return The execution result.
     */
    ExecResult execInPod(boolean throwError, LogLevel logLevel, String pod, String... command);

    /**
     * Executes a command within a pod container.
     *
     * @param pod       The name of the pod.
     * @param container The name of the container.
     * @param command   The command to execute.
     * @return The execution result.
     */
    ExecResult execInPodContainer(String pod, String container, String... command);

    /**
     * Executes a command within a pod container.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param logLevel       Desired log level where the log
     *                       from the command execution should be printed.
     * @param pod            The name of the pod.
     * @param container      The name of the container.
     * @param command        The command to execute.
     * @return The execution result.
     */
    ExecResult execInPodContainer(LogLevel logLevel, String pod, String container, String... command);

    /**
     * Executes a command within a pod container with logging to output control.
     *
     * @param logToOutput Determines if the output should be logged.
     * @param pod         The name of the pod.
     * @param container   The name of the container.
     * @param command     The command to execute.
     * @return The execution result.
     */
    ExecResult execInPodContainer(boolean logToOutput, String pod, String container, String... command);

    /**
     * Executes a command within a pod container with logging to output control.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param logLevel       Desired log level where the log
     *                       from the command execution should be printed.
     * @param logToOutput    Determines if the output should be logged.
     * @param pod            The name of the pod.
     * @param container      The name of the container.
     * @param command        The command to execute.
     *
     * @return The execution result.
     */
    ExecResult execInPodContainer(LogLevel logLevel, boolean logToOutput,
                                  String pod, String container, String... command);

    /**
     * Executes a command within a pod container with logging to output control.
     *
     * @param throwError     Whether to throw errors.
     * @param logToOutput    Determines if the output should be logged.
     * @param pod            The name of the pod.
     * @param container      The name of the container.
     * @param command        The command to execute.
     * @return The execution result.
     */
    ExecResult execInPodContainer(boolean throwError, boolean logToOutput,
                                  String pod, String container, String... command);
    /**
     * Executes a command within a pod container with logging to output control.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param throwError     Whether to throw errors.
     * @param logLevel       Desired log level where the log
     *                       from the command execution should be printed.
     * @param logToOutput    Determines if the output should be logged.
     * @param pod            The name of the pod.
     * @param container      The name of the container.
     * @param command        The command to execute.
     * @return The execution result.
     */
    ExecResult execInPodContainer(boolean throwError, LogLevel logLevel, boolean logToOutput,
                                  String pod, String container, String... command);

    /**
     * Executes a command.
     *
     * @param command The command to execute.
     * @return The execution result.
     */
    ExecResult exec(String... command);

    /**
     * Executes a command - printing output to desired log level.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param command  The command to execute.
     * @param logLevel Desired log level where the log
     *                 from the command execution should be printed.
     *
     * @return The execution result.
     */
    ExecResult exec(LogLevel logLevel, String... command);

    /**
     * Executes a command with control over throwing exceptions on failure.
     *
     * @param throwError Determines if an exception should be thrown on failure.
     * @param command    The command to execute.
     * @return The execution result.
     */
    ExecResult exec(boolean throwError, String... command);

    /**
     * Executes a command with control over throwing exceptions on failure.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param throwError    Determines if an exception should be thrown on failure.
     * @param logLevel      Desired log level where the log
     *                      from the command execution should be printed.
     * @param command       The command to execute.
     * @return The execution result.
     */
    ExecResult exec(boolean throwError, LogLevel logLevel, String... command);

    /**
     * Executes a command with control over throwing exceptions on failure and logging to output control.
     *
     * @param throwError  Determines if an exception should be thrown on failure.
     * @param logToOutput Determines if the output should be logged.
     * @param command     The command to execute.
     * @return The execution result.
     */
    ExecResult exec(boolean throwError, boolean logToOutput, String... command);

    /**
     * Executes a command with control over throwing exceptions on failure and logging to output control.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param throwError    Determines if an exception should be thrown on failure.
     * @param logLevel      Desired log level where the log
     *                      from the command execution should be printed.
     * @param logToOutput   Determines if the output should be logged.
     * @param command       The command to execute.
     * @return The execution result.
     */
    ExecResult exec(boolean throwError, LogLevel logLevel, boolean logToOutput, String... command);

    /**
     * Execute the given {@code command}. You can specify if potential failure will throw the exception or not.
     *
     * @param throwError  Parameter which control thrown exception in case of failure
     * @param command     The command
     * @param timeout     Timeout in ms
     * @param logToOutput Determines if we want to print whole output of command
     *
     * @return The process result.
     */
    ExecResult exec(boolean throwError, boolean logToOutput, int timeout, String... command);

    /**
     * Execute the given {@code command}. You can specify if potential failure will throw the exception or not.
     * Specifying {@param logLevel} you can configure a log level where
     * all the logs from the command execution will be printed into.
     *
     * @param throwError    Parameter which control thrown exception in case of failure
     * @param logLevel      Desired log level where the log
     *                      from the command execution should be printed.
     * @param command       The command
     * @param timeout       Timeout in ms
     * @param logToOutput   Determines if we want to print whole output of command
     * @return The process result.
     */
    ExecResult exec(boolean throwError, LogLevel logLevel, boolean logToOutput, int timeout, String... command);

    /**
     * Retrieves the YAML content of a resource.
     *
     * @param resource     The type of the resource.
     * @param resourceName The name of the resource.
     * @return The YAML content of the resource.
     */
    String get(String resource, String resourceName);

    /**
     * Retrieves a list of events within the current namespace.
     *
     * @return The list of events.
     */
    String getEvents();

    /**
     * Retrieves a list of resources by type.
     *
     * @param resourceType The type of the resources.
     * @return The list of resources.
     */
    List<String> list(String resourceType);

    /**
     * Retrieves the YAML content of a resource by type and name.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @return The YAML content of the resource.
     */
    String getResourceAsYaml(String resourceType, String resourceName);

    /**
     * Retrieves the YAML content of resources by type.
     *
     * @param resourceType The type of the resources.
     * @return The YAML content of the resources.
     */
    String getResourcesAsYaml(String resourceType);

    /**
     * Creates a resource from a template and applies it.
     *
     * @param template The template for the resource.
     * @param params   The parameters for the template.
     */
    void createResourceAndApply(String template, Map<String, String> params);

    /**
     * Retrieves a description of a resource.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @return The description of the resource.
     */
    String describe(String resourceType, String resourceName);

    /**
     * Retrieves logs for a pod.
     *
     * @param pod The name of the pod.
     * @return The logs for the pod.
     */
    default String logs(String pod) {
        return logs(pod, null);
    }

    /**
     * Retrieves logs for a pod container.
     *
     * @param pod       The name of the pod.
     * @param container The name of the container.
     * @return The logs for the pod container.
     */
    String logs(String pod, String container);

    /**
     * Retrieves logs for previous instance of the Pod.
     *
     * @param pod   The name of the pod.
     *
     * @return  The logs for previous instance of the Pod.
     */
    default String previousLogs(String pod) {
        return previousLogs(pod, null);
    }

    /**
     * Retrieves logs for previous instance of the Pod and container.
     *
     * @param pod           The name of the pod.
     * @param container     The name of the container.
     *
     * @return  logs for previous instance of the Pod and container.
     */
    String previousLogs(String pod, String container);

    /**
     * Searches for patterns in the logs of a resource.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @param sinceSeconds The duration for the logs (e.g., "5s" for 5 seconds).
     * @param grepPattern  The patterns to search for.
     * @return The search result.
     */
    String searchInLog(String resourceType, String resourceName, long sinceSeconds, String... grepPattern);

    /**
     * Searches for patterns in the logs of a resource container.
     *
     * @param resourceType      The type of the resource.
     * @param resourceName      The name of the resource.
     * @param resourceContainer The name of the resource container.
     * @param sinceSeconds      The duration for the logs (e.g., "5s" for 5 seconds).
     * @param grepPattern       The patterns to search for.
     * @return The search result.
     */
    String searchInLog(String resourceType, String resourceName, String resourceContainer,
                       long sinceSeconds, String... grepPattern);

    /**
     * Retrieves the JSON content of a resource.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @return The JSON content of the resource.
     */
    String getResourceAsJson(String resourceType, String resourceName);

    /**
     * Retrieves a list of resources by label.
     *
     * @param resourceType The type of the resources.
     * @param label        The label to filter by.
     * @return The list of resources.
     */
    List<String> listResourcesByLabel(String resourceType, String label);

    /**
     * Retrieves the command associated with the Kubernetes client.
     *
     * @return The command string.
     */
    String cmd();

    /**
     * Processes a file with specific domain logic.
     *
     * @param domain The domain specific parameters.
     * @param file   The file to process.
     * @param c      The consumer to handle processing.
     * @return This kube client.
     */
    K process(Map<String, String> domain, String file, Consumer<String> c);

    /**
     * Retrieves the username associated with the Kubernetes client.
     *
     * @return The username.
     */
    String getUsername();

    /**
     * Set node unschedule
     *
     * @param nodeName name of node
     */
    void cordon(String nodeName);


    /**
     * Set node schedule
     *
     * @param nodeName name of node
     */
    void uncordon(String nodeName);

    /**
     * Drain node
     *
     * @param nodeName         name of the node
     * @param ignoreDaemonSets ignore DaemonSet-managed pods
     * @param disableEviction  force drain to use delete, even if eviction is supported.
     *                         This will bypass checking PodDisruptionBudgets, use with caution.
     * @param timeoutInSeconds the length of time to wait before giving up, zero means infinite
     */
    void drain(String nodeName, boolean ignoreDaemonSets, boolean disableEviction, long timeoutInSeconds);
}
