/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.exceptions.IncompleteMetricsException;
import io.skodjob.kubetest4j.exceptions.MetricsCollectionException;
import io.skodjob.kubetest4j.exceptions.NoPodsFoundException;
import io.skodjob.kubetest4j.executor.Exec;
import io.skodjob.kubetest4j.metrics.Metric;
import io.skodjob.kubetest4j.metrics.PrometheusTextFormatParser;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.wait.Wait;
import io.skodjob.kubetest4j.wait.WaitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * MetricsCollector is a utility class designed to collect metrics from various Kubernetes components.
 * It operates based on a given Kubernetes component, namespace, and a designated scraper pod for collecting
 * the metrics.
 * This class supports dynamic integration with different Kubernetes resources through the MetricsComponent interface,
 * allowing it to be versatile across different Kubernetes setups.
 *
 * <p>This class is responsible for setting up metric collection from Kubernetes pods,
 * handling the execution of metric retrieval commands, and processing the collected data.
 * It uses a builder pattern for easy configuration of its components.</p>
 *
 * <p>Usage involves configuring the collector with necessary details about the Kubernetes environment,
 * such as namespace and pod names, and then invoking methods to collect metrics either on demand or by scheduling.</p>
 *
 * @see MetricsComponent
 * @see KubeCmdClient
 * @see KubernetesClient
 * @see Exec
 */
public class MetricsCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsCollector.class);
    private static final long EXEC_TIMEOUT_MS_DEFAULT = Duration.ofSeconds(20).toMillis();

    protected String namespaceName;
    protected String scraperPodName;
    protected String scraperPodImage;
    protected boolean deployScraperPod;
    protected MetricsComponent component;
    protected Map<String, List<Metric>> collectedData;

    /* test */ private Exec exec;
    /* test */ private KubernetesClient kubeClient;
    /* test */ private KubeCmdClient<?> kubeCmdClient;

    /**
     * Builder for creating instances of {@link MetricsCollector}.
     * This builder allows for configuring the namespace, scraper pod, and metrics component.
     */
    public static class Builder {
        private String namespaceName;
        private String scraperPodName;
        private String scraperPodImage = "quay.io/curl/curl:latest";
        private boolean deployScraperPod;
        private MetricsComponent component;
        private Map<String, List<Metric>> collectedData;
        private Exec exec;

        /**
         * Constructor
         */
        public Builder() {
            // constructor
        }

        /**
         * Sets the namespace name for the metrics collector.
         * The namespace name specifies where the metrics collector will search for pods
         * from which to collect metrics.
         *
         * @param namespaceName the name of the Kubernetes namespace
         * @return this builder instance to allow for method chaining
         */
        public Builder withNamespaceName(String namespaceName) {
            this.namespaceName = namespaceName;
            return this;
        }

        /**
         * Sets the name of the scraper pod that will be used for collecting metrics.
         * The scraper pod name is used to identify which pod within the specified namespace
         * should be used to execute metrics collection commands.
         *
         * @param scraperPodName the name of the pod designated for scraping metrics
         * @return this builder instance to allow for method chaining
         */
        public Builder withScraperPodName(String scraperPodName) {
            this.scraperPodName = scraperPodName;
            return this;
        }

        /**
         * Sets image of scraper pod which is deployed by MetricsCollector
         *
         * @param image image full path
         * @return this builder instance to allow for method chaining
         */
        public Builder withScraperPodImage(String image) {
            this.scraperPodImage = image;
            return this;
        }

        /**
         * Deploy own scraper pod instead of using already created with name
         *
         * @return this builder instance to allow for method chaining
         */
        public Builder withDeployScraperPod() {
            this.deployScraperPod = true;
            return this;
        }

        /**
         * Sets the metrics component configuration for the metrics collector.
         * This component defines specific details such as the port and path used for
         * collecting metrics and the label selector to identify relevant pods.
         *
         * @param component the metrics component that provides configuration details
         * @return this builder instance to allow for method chaining
         */
        public Builder withComponent(MetricsComponent component) {
            this.component = component;
            return this;
        }


        /**
         * Sets the map that will hold the collected metrics data. This method is typically
         * used for testing purposes to inject predefined data into the metrics collector.
         *
         * @param collectedData the map to store collected metrics data
         * @return this builder instance to allow for method chaining
         */
        /* test */
        protected Builder withCollectedData(Map<String, List<Metric>> collectedData) {
            this.collectedData = collectedData;
            return this;
        }

        /**
         * Configures the {@link Exec} instance used for executing shell commands during metric collection.
         * This method is primarily intended for setting up the execution environment that interacts with
         * the Kubernetes command-line tools, such as kubectl, to facilitate metrics collection from pods.
         *
         * <p>This method is marked for testing purposes and should be used to inject mock or alternative
         * {@link Exec} implementations during unit testing. It allows for the separation of command execution
         * logic from the metrics collection logic, thereby enhancing testability and modularity.</p>
         *
         * @param exec The {@link Exec} instance to be used for command execution.
         * @return This builder instance to allow for method chaining, enabling a fluent builder setup.
         */
        /* test */
        protected Builder withExec(Exec exec) {
            this.exec = exec;
            return this;
        }

        /**
         * Constructs a new {@link MetricsCollector} instance based on the current configuration of this builder.
         * This method uses the configuration properties set on this builder instance to initialize a new
         * {@link MetricsCollector} object, encapsulating all setup necessary for collecting metrics.
         *
         * @return a new instance of {@link MetricsCollector} configured according to this builder's properties
         */
        public MetricsCollector build() {
            return new MetricsCollector(this);
        }
    }

    // Additional setter methods for dependency injection
    /* test */
    protected synchronized void setKubeClient(KubernetesClient client) {
        this.kubeClient = client;
    }

    /* test */
    protected synchronized void setKubeCmdClient(KubeCmdClient<?> client) {
        this.kubeCmdClient = client;
    }

    /* test */
    protected void setExec(Exec exec) {
        this.exec = exec;
    }

    synchronized KubernetesClient getKubeClient() {
        if (kubeClient == null) {
            kubeClient = KubeResourceManager.get().kubeClient().getClient();
            if (kubeClient == null) {
                throw new IllegalStateException("KubeClient is not available");
            }
        }
        return kubeClient;
    }

    @SuppressWarnings("unchecked")
    synchronized <K extends KubeCmdClient<K>> K getKubeCmdClient() {
        if (kubeCmdClient == null) {
            kubeCmdClient = KubeResourceManager.get().kubeCmdClient();
            if (kubeCmdClient == null) {
                throw new IllegalStateException("KubeCmdClient is not available");
            }
        }
        return (K) kubeCmdClient;
    }

    /**
     * Retrieves the namespace name.
     *
     * @return the name of the namespace
     */
    public String getNamespaceName() {
        return namespaceName;
    }

    /**
     * Retrieves the name of the scraper pod.
     *
     * @return the name of the scraper pod
     */
    public String getScraperPodName() {
        return scraperPodName;
    }

    /**
     * Retrieves the image of the scraper pod.
     *
     * @return the image of the scraper pod
     */
    public String getScraperPodImage() {
        return scraperPodImage;
    }

    /**
     * Retrieves the metrics component associated with this object.
     *
     * @return the metrics component
     */
    public MetricsComponent getComponent() {
        return component;
    }

    /**
     * Retrieves the switch if deploy own scraper pod is enabled
     *
     * @return boolean indicates deploy own pod
     */
    public boolean getDeployScraperPod() {
        return deployScraperPod;
    }

    /**
     * Retrieves the collected data as a map.
     *
     * @return a map containing the collected data
     */
    public Map<String, List<Metric>> getCollectedData() {
        return collectedData;
    }

    /* test */ protected Exec getExec() {
        return exec;
    }

    /* test */
    protected void setCollectedData(Map<String, List<Metric>> collectedData) {
        this.collectedData = collectedData;
    }

    protected MetricsCollector.Builder newBuilder() {
        return new MetricsCollector.Builder();
    }

    protected MetricsCollector.Builder updateBuilder(MetricsCollector.Builder builder) {
        MetricsCollector.Builder b =  builder
            .withNamespaceName(getNamespaceName())
            .withComponent(getComponent())
            .withScraperPodImage(getScraperPodImage())
            .withScraperPodName(getScraperPodName())
            .withCollectedData(getCollectedData())
            .withExec(getExec());

        if (getDeployScraperPod()) {
            b.withDeployScraperPod();
        }

        return b;
    }

    /**
     * Creates a new {@link Builder} instance that is initialized with the current configuration of the existing
     * {@link MetricsCollector}. This method allows for modifying an existing {@link MetricsCollector} configuration
     * by first replicating its setup and then applying new configuration changes.
     *
     * @return a new {@link Builder} instance pre-populated with the current metrics collector's configuration
     */
    public MetricsCollector.Builder toBuilder() {
        return updateBuilder(newBuilder());
    }

    protected MetricsCollector(Builder builder) {
        if (builder.namespaceName == null || builder.namespaceName.isEmpty()) {
            throw new InvalidParameterException("Namespace name is not set");
        }
        if (builder.scraperPodName == null || builder.scraperPodName.isEmpty()) {
            throw new InvalidParameterException("Scraper Pod name is not set");
        }
        if (builder.component == null) {
            throw new InvalidParameterException("Component is not set");
        }

        // set default values
        if (builder.collectedData == null) {
            builder.collectedData = Collections.emptyMap();
        }

        if (builder.exec == null) {
            builder.exec = new Exec();
        }

        namespaceName = builder.namespaceName;
        scraperPodName = builder.scraperPodName;
        scraperPodImage = builder.scraperPodImage;
        deployScraperPod = builder.deployScraperPod;
        component = builder.component;
        collectedData = builder.collectedData;
        exec = builder.exec;
    }

    /**
     * Executes a command to collect metrics from a specific pod.
     * The method constructs and executes a shell command to scrape metrics using curl,
     * handling the execution within a specified timeout.
     *
     * @param metricsPodIp The IP address of the metrics pod.
     * @param podName      The name of the pod from which metrics are being collected.
     * @return String               The output from the executed command, typically metrics data.
     * @throws InterruptedException If the thread is interrupted during command execution.
     * @throws ExecutionException   If an error occurs during the command execution.
     * @throws IOException          If an I/O error occurs during command handling.
     */
    protected String collectMetrics(String metricsPodIp, String podName)
        throws InterruptedException, ExecutionException, IOException {

        if (this.deployScraperPod) {
            deployScraperPod();
        }

        List<String> executableCommand = Arrays.asList(getKubeCmdClient().inNamespace(namespaceName).toString(), "exec",
            scraperPodName,
            "-n", namespaceName,
            "--", "curl", metricsPodIp + ":" +
                component.getDefaultMetricsPort() + component.getDefaultMetricsPath());

        LOGGER.debug("Executing command:{} for scrape the metrics", executableCommand);

        // 20 seconds should be enough for collect data from the pod
        int ret = this.exec.execute(null, executableCommand, null, EXEC_TIMEOUT_MS_DEFAULT);

        LOGGER.debug("Metrics collection for Pod: {}/{}({}) from Pod: {}/{} finished with return code: {}",
            namespaceName, podName, metricsPodIp, namespaceName, scraperPodName, ret);

        if (this.deployScraperPod) {
            deleteScraperPod();
        }

        return this.exec.out();
    }

    /**
     * Deploy own scraper pod
     */
    private void deployScraperPod() {
        Pod scraperPod = new PodBuilder()
            .withNewMetadata()
                .withName(this.scraperPodName)
                .withNamespace(this.namespaceName)
                .addToLabels(Map.of("io.skodjob.scraper-pod", "true"))
            .endMetadata()
            .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                    .withName("scraper-container")
                    .withImage(this.scraperPodImage)
                    .withCommand("/bin/sh")
                    .withArgs("-c", "while true; do sleep 3600; done")
                .endContainer()
            .endSpec()
            .build();
        KubeResourceManager.get().createResourceWithWait(scraperPod);
    }

    /**
     * Delete own scraper pod
     */
    private void deleteScraperPod() {
        KubeResourceManager.get().deleteResourceWithWait(
            new PodBuilder()
                .withNewMetadata()
                    .withName(this.scraperPodName)
                    .withNamespace(this.namespaceName)
                .endMetadata()
                .build());
    }

    /**
     * Return list of metrics containing specific label name for specific pod
     *
     * @param podName name of the pod which provided metrics
     * @param label label
     * @return list of metrics with label
     */
    public final List<Metric> collectMetricWithLabels(String podName, String label) {
        return this.collectedData.getOrDefault(podName, Collections.emptyList()).stream()
            .filter(metric -> metric.getLabels().containsKey(label)).toList();
    }

    /**
     * Collects metrics from all relevant pods within the configured namespace using specified label selectors,
     * within a given timeout duration. This method orchestrates the entire metrics collection process, handling
     * retries and managing exceptions, and aggregates the metrics data into a map.
     *
     * <p>The method employs a retry mechanism that attempts to collect metrics until the data meets expected
     * conditions or the timeout is reached. The readiness of metrics data is determined based on non-emptiness
     * and completeness criteria. If metrics collection encounters any issues (e.g., no data, incomplete data, or
     * runtime exceptions), it categorizes the issue using a {@link MetricsCollectionStatus} object.</p>
     *
     * <p>If the collection process times out, the method evaluates the type and message of the error encountered
     * during the collection attempts and throws a corresponding specialized exception based on the last known error
     * state.</p>
     *
     * @param timeoutDuration the maximum time in milliseconds to wait for metrics collection.
     *                        This duration dictates how long the system should attempt to collect metrics
     *                        before timing out.
     * @throws MetricsCollectionException if the collection process fails due to any error such as no data,
     *                                    incomplete data, or an exception during the collection process.
     *                                    This exception encapsulates detailed error information, including
     *                                    the original cause of the failure when applicable.
     */
    public final void collectMetricsFromPods(long timeoutDuration) {
        final MetricsCollectionStatus status = new MetricsCollectionStatus();

        try {
            Wait.until("metrics won't be empty", TestFrameConstants.GLOBAL_POLL_INTERVAL_1_SEC, timeoutDuration,
                () -> {
                    try {
                        Map<String, List<Metric>> metricsData = collectMetricsFromPodsWithoutWait();
                        if (metricsData.isEmpty()) {
                            status.setMessage("No pods found or no metrics available from pods.");
                            status.setType(MetricsCollectionStatus.Type.NO_DATA);
                            LOGGER.warn("Metrics collection failed: {}", status.getMessage());
                            return false;
                        }
                        if (metricsData.values().stream().anyMatch(List::isEmpty)) {
                            status.setMessage("Incomplete metrics data collected.");
                            status.setType(MetricsCollectionStatus.Type.INCOMPLETE_DATA);
                            LOGGER.warn("Metrics collection incomplete: Some pods returned empty metrics.");
                            return false;
                        }
                        this.collectedData = metricsData;
                        return true;
                    } catch (Exception e) {
                        status.setMessage(e.getMessage());
                        status.setType(MetricsCollectionStatus.Type.ERROR);
                        status.setException(e);
                        LOGGER.warn("Error during metrics collection: {}", status.getMessage(), e);
                        return false;
                    }
                },
                () -> LOGGER.error("Failed to collect metrics within the allowed time: {}", status.getMessage())
            );
        } catch (WaitException we) {
            LOGGER.error("Metrics collection terminated due to timeout: {}", we.getMessage());
            throw determineExceptionFromStatus(status);
        }
    }

    /* test */ MetricsCollectionException determineExceptionFromStatus(MetricsCollectionStatus status) {
        return switch (status.getType()) {
            case null -> new MetricsCollectionException("Unknown error occurred during metrics collection");
            case NO_DATA -> new NoPodsFoundException(status.getMessage());
            case INCOMPLETE_DATA -> new IncompleteMetricsException(status.getMessage());
            case ERROR -> new MetricsCollectionException(status.getMessage(), status.getException());
        };
    }

    static class MetricsCollectionStatus {
        private String message;
        private Exception exception;
        private Type type;

        enum Type {
            NO_DATA, INCOMPLETE_DATA, ERROR
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }
    }

    /**
     * Collects metrics from all relevant pods within the configured namespace using the default timeout.
     * This method is a convenience wrapper around {@link #collectMetricsFromPods(long)} with the timeout
     * set to {@link TestFrameConstants#GLOBAL_TIMEOUT_MEDIUM}.
     *
     * <p>This simplifies calls to the metrics collection process when the default timeout is appropriate.
     * The method orchestrates the metric collection process, handling retries, exceptions, and aggregates
     * metrics data into a map. It uses the pre-configured timeout defined in {@code TestFrameConstants}.
     *
     * @throws MetricsCollectionException if the collection fails due to no data or errors during execution.
     *                                    This includes scenarios where no pods are found, data is incomplete,
     *                                    or other errors occur as defined by the implementation of the metric
     *                                    collection logic.
     */
    public final void collectMetricsFromPods() throws MetricsCollectionException {
        collectMetricsFromPods(TestFrameConstants.GLOBAL_TIMEOUT_MEDIUM);
    }

    /**
     * Collects metrics from each pod that matches the component's label selector immediately without waiting
     * for data availability checks. This method is useful for scenarios where immediate data retrieval is necessary
     * and the readiness of data is either ensured or non-critical.
     *
     * @return A map containing the collected metrics, associated by pod name.
     * @throws MetricsCollectionException if errors occur during the collection process.
     */
    public final Map<String, List<Metric>> collectMetricsFromPodsWithoutWait() {
        final Map<String, List<Metric>> map = new HashMap<>();
        final Map<String, String> errorMap = new HashMap<>(); // Store errors separately

        final List<Pod> pods = getKubeClient()
            .pods()
            .inNamespace(namespaceName)
            .withLabelSelector(component.getLabelSelector())
            .list()
            .getItems();
        if (pods.isEmpty()) {
            LOGGER.info("No pods found with the specified label selector.");
            return map;
        }

        for (final Pod p : pods) {
            final String podName = p.getMetadata().getName();
            String podIP = p.getStatus().getPodIP();
            if (TestFrameEnv.IP_FAMILY.equals(TestFrameEnv.IP_FAMILY_VERSION_6)) {
                podIP = "[" + podIP + "]";
            }

            try {
                final String metrics = collectMetrics(podIP, podName);
                final List<Metric> parsedMetrics = PrometheusTextFormatParser.parse(metrics);

                map.put(podName, parsedMetrics);
                LOGGER.info("Finished metrics collection from {}", podName);
                LOGGER.debug("Collected metrics from {}: {}", podName, metrics);
                LOGGER.debug("Parsed metrics from {}:\n{}", podName, parsedMetrics);
            } catch (InterruptedException | ExecutionException | IOException e) {
                LOGGER.error("Failed to collect metrics from {}: {}", podName, e.getMessage());
                errorMap.put(podName, e.getMessage()); // Store the error message
            }
        }

        if (!errorMap.isEmpty()) {
            throw new MetricsCollectionException("Errors occurred while collecting metrics: " + errorMap);
        }

        return map;
    }
}
