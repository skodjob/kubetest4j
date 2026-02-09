/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.skodjob.kubetest4j.clients.cmdClient;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.skodjob.kubetest4j.clients.KubeClusterException;
import io.skodjob.kubetest4j.enums.LogLevel;
import io.skodjob.kubetest4j.executor.Exec;
import io.skodjob.kubetest4j.executor.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.join;
import static java.util.Arrays.asList;

/**
 * Abstract class representing a base Kubernetes command-line client.
 *
 * @param <K> The subtype of KubeClient, for fluency.
 */
public abstract class BaseCmdKubeClient<K extends BaseCmdKubeClient<K>> implements KubeCmdClient<K> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseCmdKubeClient.class);

    private static final String CREATE = "create";
    private static final String APPLY = "apply";
    private static final String DELETE = "delete";
    private static final String REPLACE = "replace";
    private static final String PROCESS = "process";
    private static final String GET = "get";

    protected String config;
    protected int timeout;
    protected String namespace;

    /**
     * Constructor for BaseCmdKubeClient.
     *
     * @param config The Kubernetes configuration file path.
     * @param timeoutInMs Timeout for exec commands.
     */
    protected BaseCmdKubeClient(String config, int timeoutInMs) {
        this.config = config;
        this.timeout = timeoutInMs;
    }

    protected List<String> command(String... args) {
        return command(Arrays.stream(args).toList());
    }

    protected List<String> command(List<String> args) {
        List<String> result = new ArrayList<>();
        result.add(cmd());

        if (config != null) {
            result.add("--kubeconfig");
            result.add(config);
        }

        if (namespace != null) {
            result.add("--namespace");
            result.add(namespace);
        }

        result.addAll(args);
        return result;
    }

    /**
     * Abstract method to retrieve the command.
     *
     * @return The command string.
     */
    @Override
    public abstract String cmd();

    /**
     * Deletes a resource by its name.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K deleteByName(String resourceType, String resourceName) {
        Exec.exec(command(DELETE, resourceType, resourceName), timeout);
        return (K) this;
    }

    protected static class Context implements AutoCloseable {
        @Override
        public void close() {
        }
    }

    private static final Context NOOP = new Context();

    protected Context defaultContext() {
        return NOOP;
    }

    // Admin context is not implemented now, because it's not needed
    // In case it will be needed in the future, we should change the kubeconfig and apply it for both oc and kubectl
    protected Context adminContext() {
        return defaultContext();
    }

    /**
     * Retrieves the YAML representation of a resource.
     *
     * @param resource     The type of the resource.
     * @param resourceName The name of the resource.
     * @return The YAML representation of the resource.
     */
    @Override
    public String get(String resource, String resourceName) {
        return Exec.exec(command(GET, resource, resourceName, "-o", "yaml"), timeout).out();
    }

    /**
     * Retrieves the events from the cluster.
     *
     * @return The events as a string.
     */
    @Override
    public String getEvents() {
        return Exec.exec(command(GET, "events"), timeout).out();
    }

    /**
     * Creates resources from files.
     *
     * @param files The files representing the resources.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K create(File... files) {
        try (Context context = defaultContext()) {
            Map<File, ExecResult> execResults = execRecursive(CREATE, files, Comparator.comparing(File::getName)
                .reversed());
            for (Map.Entry<File, ExecResult> entry : execResults.entrySet()) {
                if (!entry.getValue().exitStatus()) {
                    LOGGER.warn("Failed to create {}!", entry.getKey().getAbsolutePath());
                    LOGGER.debug(entry.getValue().err());
                }
            }
            return (K) this;
        }
    }

    /**
     * Applies resources from files.
     *
     * @param files The files representing the resources.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K apply(File... files) {
        try (Context context = defaultContext()) {
            Map<File, ExecResult> execResults = execRecursive(APPLY, files, Comparator.comparing(File::getName)
                .reversed());
            for (Map.Entry<File, ExecResult> entry : execResults.entrySet()) {
                if (!entry.getValue().exitStatus()) {
                    LOGGER.warn("Failed to apply {}!", entry.getKey().getAbsolutePath());
                    LOGGER.debug(entry.getValue().err());
                }
            }
            return (K) this;
        }
    }

    /**
     * Deletes resources from files.
     *
     * @param files The files representing the resources.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K delete(File... files) {
        try (Context context = defaultContext()) {
            Map<File, ExecResult> execResults = execRecursive(DELETE, files, Comparator.comparing(File::getName)
                .reversed());
            for (Map.Entry<File, ExecResult> entry : execResults.entrySet()) {
                if (!entry.getValue().exitStatus()) {
                    LOGGER.warn("Failed to delete {}!", entry.getKey().getAbsolutePath());
                    LOGGER.debug(entry.getValue().err());
                }
            }
            return (K) this;
        }
    }

    private Map<File, ExecResult> execRecursive(String subcommand, File[] files, Comparator<File> cmp) {
        Map<File, ExecResult> execResults = new HashMap<>(25);
        for (File f : files) {
            if (f.isFile()) {
                if (f.getName().endsWith(".yaml")) {
                    execResults.put(f, Exec.exec(null, command(subcommand, "-f",
                        f.getAbsolutePath()), timeout, false, false));
                }
            } else if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    Arrays.sort(children, cmp);
                    execResults.putAll(execRecursive(subcommand, children, cmp));
                }
            } else if (!f.exists()) {
                throw new RuntimeException(new NoSuchFileException(f.getPath()));
            }
        }
        return execResults;
    }

    /**
     * Replaces resources from files.
     *
     * @param files The files representing the resources.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K replace(File... files) {
        try (Context context = defaultContext()) {
            Map<File, ExecResult> execResults = execRecursive(REPLACE, files, Comparator.comparing(File::getName));
            for (Map.Entry<File, ExecResult> entry : execResults.entrySet()) {
                if (!entry.getValue().exitStatus()) {
                    LOGGER.warn("Failed to replace {}!", entry.getKey().getAbsolutePath());
                    LOGGER.debug(entry.getValue().err());
                }
            }
            return (K) this;
        }
    }

    /**
     * Applies YAML content.
     *
     * @param yamlContent The YAML content.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K applyContent(String yamlContent) {
        try (Context context = defaultContext()) {
            Exec.exec(yamlContent, command(Arrays.asList(APPLY, "-f", "-")), timeout,
                true, true);
            return (K) this;
        }
    }

    /**
     * Replaces YAML content.
     *
     * @param yamlContent The YAML content.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K replaceContent(String yamlContent) {
        try (Context context = defaultContext()) {
            Exec.exec(yamlContent, command(Arrays.asList(REPLACE, "-f", "-")), timeout,
                true, true);
            return (K) this;
        }
    }

    /**
     * Deletes YAML content.
     *
     * @param yamlContent The YAML content.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K deleteContent(String yamlContent) {
        try (Context context = defaultContext()) {
            Exec.exec(yamlContent, command(Arrays.asList(DELETE, "-f", "-")), timeout,
                true, false);
            return (K) this;
        }
    }

    /**
     * Creates a namespace.
     *
     * @param name The name of the namespace.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K createNamespace(String name) {
        try (Context context = adminContext()) {
            Exec.exec(command(CREATE, "namespace", name), timeout);
        }
        return (K) this;
    }

    /**
     * Deletes a namespace.
     *
     * @param name The name of the namespace.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K deleteNamespace(String name) {
        try (Context context = adminContext()) {
            Exec.exec(null, command(DELETE, "namespace", name), timeout, true, false);
        }
        return (K) this;
    }

    /**
     * Scales resources by name.
     *
     * @param kind     The kind of the resource.
     * @param name     The name of the resource.
     * @param replicas The number of replicas.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K scaleByName(String kind, String name, int replicas) {
        try (Context context = defaultContext()) {
            Exec.exec(command("scale", kind, name, "--replicas", Integer.toString(replicas)), timeout);
            return (K) this;
        }
    }

    /**
     * Executes a command in a pod.
     *
     * @param pod     The name of the pod.
     * @param command The command to execute.
     * @return The execution result.
     */
    @Override
    public ExecResult execInPod(String pod, String... command) {
        return execInPod(true, pod, command);
    }

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
    @Override
    public ExecResult execInPod(LogLevel logLevel, String pod, String... command) {
        return execInPod(true, logLevel, pod, command);
    }

    /**
     * Executes a command in a pod.
     *
     * @param throwError     Whether to throw errors.
     * @param pod            The name of the pod.
     * @param command        The command to execute.
     * @return The execution result.
     */
    @Override
    public ExecResult execInPod(boolean throwError, String pod, String... command) {
        return execInPod(throwError, LogLevel.INFO, pod, command);
    }

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
    @Override
    public ExecResult execInPod(boolean throwError, LogLevel logLevel, String pod, String... command) {
        List<String> cmd = command("exec", pod, "--");
        cmd.addAll(asList(command));
        return Exec.exec(null, cmd, timeout, logLevel, false, throwError);
    }

    /**
     * Executes a command in a pod container.
     *
     * @param pod       The name of the pod.
     * @param container The name of the container.
     * @param command   The command to execute.
     * @return The execution result.
     */
    @Override
    public ExecResult execInPodContainer(String pod, String container, String... command) {
        return execInPodContainer(true, pod, container, command);
    }

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
    @Override
    public ExecResult execInPodContainer(LogLevel logLevel, String pod, String container, String... command) {
        return execInPodContainer(logLevel, true, pod, container, command);
    }

    /**
     * Executes a command in a pod container with the option to log to output.
     *
     * @param logToOutput Whether to log the output.
     * @param pod         The name of the pod.
     * @param container   The name of the container.
     * @param command     The command to execute.
     * @return The execution result.
     */
    @Override
    public ExecResult execInPodContainer(boolean logToOutput, String pod, String container, String... command) {
        return execInPodContainer(true, logToOutput, pod, container, command);
    }

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
    @Override
    public ExecResult execInPodContainer(LogLevel logLevel, boolean logToOutput,
                                         String pod, String container, String... command) {
        return execInPodContainer(true, logLevel, logToOutput, pod, container, command);
    }

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
    @Override
    public ExecResult execInPodContainer(boolean throwError, boolean logToOutput,
                                         String pod, String container, String... command) {
        return execInPodContainer(throwError, LogLevel.INFO, logToOutput, pod, container, command);
    }

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
    @Override
    public ExecResult execInPodContainer(boolean throwError, LogLevel logLevel, boolean logToOutput,
                                         String pod, String container, String... command) {
        List<String> cmd = command("exec", pod, "-c", container, "--");
        cmd.addAll(asList(command));
        return Exec.exec(null, cmd, timeout, logLevel, logToOutput, throwError);
    }

    /**
     * Executes a command.
     *
     * @param command The command to execute.
     * @return The execution result.
     */
    @Override
    public ExecResult exec(String... command) {
        return exec(true, command);
    }

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
    @Override
    public ExecResult exec(LogLevel logLevel, String... command) {
        return exec(true, logLevel, command);
    }

    /**
     * Executes a command with the option to throw errors.
     *
     * @param throwError Whether to throw errors.
     * @param command    The command to execute.
     * @return The execution result.
     */
    @Override
    public ExecResult exec(boolean throwError, String... command) {
        return exec(throwError, true, command);
    }

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
    @Override
    public ExecResult exec(boolean throwError, LogLevel logLevel, String... command) {
        return exec(throwError, logLevel, true, command);
    }

    /**
     * Executes a command with the option to throw errors and log to output.
     *
     * @param throwError  Whether to throw errors.
     * @param logToOutput Whether to log the output.
     * @param command     The command to execute.
     * @return The execution result.
     */
    @Override
    public ExecResult exec(boolean throwError, boolean logToOutput, String... command) {
        List<String> cmd = command(asList(command));
        return Exec.exec(null, cmd, 0, logToOutput, throwError);
    }

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
    @Override
    public ExecResult exec(boolean throwError, LogLevel logLevel, boolean logToOutput, String... command) {
        return exec(throwError, logLevel, logToOutput, 0, command);
    }

    /**
     * Executes a command with the option to throw errors and log to output.
     *
     * @param throwError  Whether to throw errors.
     * @param logToOutput Whether to log the output.
     * @param timeout     timeout in milis
     * @param command     The command to execute.
     * @return The execution result.
     */
    @Override
    public ExecResult exec(boolean throwError, boolean logToOutput, int timeout, String... command) {
        return exec(throwError, LogLevel.INFO, logToOutput, timeout, command);
    }

    /**
     * Execute the given {@code command}. You can specify if potential failure will throw the exception or not.
     *
     * @param throwError  parameter which control thrown exception in case of failure
     * @param command     The command
     * @param timeout     tiemout in ms
     * @param logToOutput determines if we want to print whole output of command
     * @return The process result.
     */
    @Override
    public ExecResult exec(boolean throwError, LogLevel logLevel, boolean logToOutput, int timeout, String... command) {
        List<String> cmd = command(asList(command));
        return Exec.exec(null, cmd, timeout, logLevel, logToOutput, throwError);
    }

    /**
     * Converts the object to a string representation.
     *
     * @return The string representation of the object.
     */
    @Override
    public String toString() {
        return cmd();
    }

    /**
     * Lists resources of a certain type.
     *
     * @param resourceType The type of the resource.
     * @return A list of resource names.
     */
    @Override
    public List<String> list(String resourceType) {
        return Arrays.stream(Exec.exec(command(GET, resourceType,
                    "-o", "jsonpath={range .items[*]}{.metadata.name} "), timeout)
                .out().trim().split(" +"))
            .filter(s -> !s.trim().isEmpty()).collect(Collectors.toList());
    }

    /**
     * Retrieves a resource as JSON.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @return The resource as JSON.
     */
    @Override
    public String getResourceAsJson(String resourceType, String resourceName) {
        return Exec.exec(command(GET, resourceType, resourceName, "-o", "json"), timeout).out();
    }

    /**
     * Retrieves a resource as YAML.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @return The resource as YAML.
     */
    @Override
    public String getResourceAsYaml(String resourceType, String resourceName) {
        return Exec.exec(command(GET, resourceType, resourceName, "-o", "yaml"), timeout).out();
    }

    /**
     * Retrieves resources as YAML.
     *
     * @param resourceType The type of the resource.
     * @return The resources as YAML.
     */
    @Override
    public String getResourcesAsYaml(String resourceType) {
        return Exec.exec(command(GET, resourceType, "-o", "yaml"), timeout).out();
    }

    /**
     * Creates a resource from a template and applies it.
     *
     * @param template The template file.
     * @param params   The parameters for the template.
     */
    @Override
    public void createResourceAndApply(String template, Map<String, String> params) {
        List<String> cmd = command("process", template, "-l", "app=" + template, "-o", "yaml");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            cmd.add("-p");
            cmd.add(entry.getKey() + "=" + entry.getValue());
        }

        String yaml = Exec.exec(cmd, timeout).out();
        this.applyContent(yaml);
    }

    /**
     * Retrieves the description of a resource.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @return The description of the resource.
     */
    @Override
    public String describe(String resourceType, String resourceName) {
        return Exec.exec(command("describe", resourceType, resourceName), timeout).out();
    }

    /**
     * Retrieves logs of a pod/container.
     *
     * @param pod       The name of the pod.
     * @param container The name of the container.
     * @return The logs as a string.
     */
    @Override
    public String logs(String pod, String container) {
        String[] args;
        if (container != null) {
            args = new String[]{"logs", pod, "-c", container};
        } else {
            args = new String[]{"logs", pod};
        }
        return Exec.exec(command(args), timeout).out();
    }

    /**
     * Retrieves logs for previous instance of the Pod and container.
     *
     * @param pod           The name of the pod.
     * @param container     The name of the container.
     *
     * @return  logs for previous instance of the Pod and container.
     */
    @Override
    public String previousLogs(String pod, String container) {
        String[] args;
        if (container != null) {
            args = new String[]{"logs", pod, "-c", container, "--previous=true"};
        } else {
            args = new String[]{"logs", pod, "--previous=true"};
        }
        return Exec.exec(command(args), timeout).out();
    }

    /**
     * Searches for patterns in logs.
     *
     * @param resourceType The type of the resource.
     * @param resourceName The name of the resource.
     * @param sinceSeconds The duration since the logs should be searched.
     * @param grepPattern  The patterns to search for.
     * @return The matching lines from logs.
     */
    @Override
    public String searchInLog(String resourceType, String resourceName, long sinceSeconds, String... grepPattern) {
        try {
            return Exec.exec(timeout, "bash", "-c", join(" ",
                    command("logs", resourceType + "/" + resourceName,
                        "--since=" + sinceSeconds + "s",
                        "|", "grep", " -e " + join(" -e ", grepPattern), "-B", "1")))
                .out();
        } catch (KubeClusterException e) {
            if (e.result != null && e.result.returnCode() == 1) {
                LOGGER.info("{} not found", Arrays.stream(grepPattern).toList());
            } else {
                LOGGER.error("Caught exception while searching {} in logs", Arrays.stream(grepPattern).toList());
            }
        }
        return "";
    }

    /**
     * Searches for patterns in logs of a specific container.
     *
     * @param resourceType      The type of the resource.
     * @param resourceName      The name of the resource.
     * @param resourceContainer The name of the container.
     * @param sinceSeconds      The duration since the logs should be searched.
     * @param grepPattern       The patterns to search for.
     * @return The matching lines from logs.
     */
    @Override
    public String searchInLog(String resourceType, String resourceName, String resourceContainer,
                              long sinceSeconds, String... grepPattern) {
        try {
            return Exec.exec(timeout, "bash", "-c", join(" ", command("logs",
                resourceType + "/" + resourceName, "-c " + resourceContainer, "--since=" + sinceSeconds + "s",
                "|", "grep", " -e " + join(" -e ", grepPattern), "-B", "1"))).out();
        } catch (KubeClusterException e) {
            if (e.result != null && e.result.exitStatus()) {
                LOGGER.info("{} not found", Arrays.stream(grepPattern).toList());
            } else {
                LOGGER.error("Caught exception while searching {} in logs", Arrays.stream(grepPattern).toList());
            }
        }
        return "";
    }

    /**
     * Lists resources by label.
     *
     * @param resourceType The type of the resource.
     * @param label        The label.
     * @return A list of resource names.
     */
    @Override
    public List<String> listResourcesByLabel(String resourceType, String label) {
        return asList(Exec.exec(command(GET, resourceType,
                "-l", label, "-o", "jsonpath={range .items[*]}{.metadata.name} "), timeout)
            .out().split("\\s+"));
    }

    /**
     * Processes a template file with parameters.
     *
     * @param parameters The parameters for the template.
     * @param file       The template file.
     * @param c          The consumer to process the output.
     * @return The instance of the client.
     */
    @Override
    @SuppressWarnings("unchecked")
    public K process(Map<String, String> parameters, String file, Consumer<String> c) {
        List<String> command = command(asList(PROCESS, "-f", file));
        command.addAll(parameters.entrySet().stream()
            .map(e -> "-p " + e.getKey() + "=" + e.getValue())
            .toList());

        c.accept(Exec.exec(null, command, timeout, false).out());
        return (K) this;
    }
}
