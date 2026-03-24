/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.utils.TestUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Manages log collection operations for Kubernetes tests.
 * This class handles log collection setup, multi-kubeContext log gathering,
 * and coordination with LogCollector instances across different cluster contexts.
 * <p>
 * Namespace discovery is purely label-based: all namespaces created via
 * {@code @ClassNamespace} or {@code @MethodNamespace} are automatically labeled
 * by the namespace auto-labeling callback in {@link NamespaceService}.
 */
class LogCollectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogCollectionService.class);

    // Log collection labels
    private static final String LOG_COLLECTION_LABEL_KEY = "kubetest4j.skodjob.io/log-collection";
    private static final String LOG_COLLECTION_LABEL_VALUE = "enabled";

    private final ContextStoreHelper contextStoreHelper;
    private final ConfigurationService configurationService;
    private final MultiKubeContextProvider contextProvider;

    /**
     * Creates a new LogCollectionManager with the given dependencies.
     *
     * @param contextStoreHelper   provides access to extension kubeContext storage
     * @param configurationService provides access to test configuration
     * @param contextProvider      provides multi-kubeContext operations
     */
    LogCollectionService(ContextStoreHelper contextStoreHelper,
                         ConfigurationService configurationService,
                         MultiKubeContextProvider contextProvider) {
        this.contextStoreHelper = contextStoreHelper;
        this.configurationService = configurationService;
        this.contextProvider = contextProvider;
    }

    // ===============================
    // Log Collection Setup
    // ===============================

    /**
     * Sets up the primary LogCollector for the test.
     */
    public void setupLogCollector(ExtensionContext context, TestConfig testConfig,
                                  KubeResourceManager resourceManager) {
        String logPath = getLogPath(context, testConfig, KubeTestConstants.DEFAULT_CONTEXT_NAME);

        LogCollectorBuilder builder = createLogBuilder(testConfig, resourceManager, logPath);

        LogCollector logCollector = builder.build();
        contextStoreHelper.putLogCollector(context, logCollector);

        LOGGER.debug("Setting up log collector with strategy '{}', path: {}",
            testConfig.logCollectionStrategy(), logPath);
    }

    private LogCollectorBuilder createLogBuilder(TestConfig testConfig,
                                                 KubeResourceManager resourceManager, String logPath) {
        LogCollectorBuilder builder = new LogCollectorBuilder()
            .withRootFolderPath(logPath)
            .withKubeClient(resourceManager.kubeClient())
            .withKubeCmdClient(resourceManager.kubeCmdClient())
            .withNamespacedResources(testConfig.collectNamespacedResources().toArray(new String[0]));

        if (!testConfig.collectClusterWideResources().isEmpty()) {
            builder.withClusterWideResources(testConfig.collectClusterWideResources().toArray(new String[0]));
        }

        if (testConfig.collectPreviousLogs()) {
            builder.withCollectPreviousLogs();
        }

        return builder;
    }

    // ===============================
    // Log Collection Execution
    // ===============================

    /**
     * Collects logs from all contexts with the specified suffix.
     */
    public void collectLogs(ExtensionContext context, String suffix) {
        TestConfig testConfig = configurationService.getTestConfig(context);
        LogCollector logCollector = getLogCollector(context);

        if (logCollector == null) {
            LOGGER.warn("Skipping log collection - log collector not available");
            return;
        }

        try {
            LOGGER.info("Collecting logs: {}", suffix);

            // Create label selector to find namespaces with log collection enabled
            LabelSelector logCollectionSelector = new LabelSelectorBuilder()
                .addToMatchLabels(LOG_COLLECTION_LABEL_KEY, LOG_COLLECTION_LABEL_VALUE)
                .build();

            LOGGER.debug("Collecting from namespaces with label: {}={}", LOG_COLLECTION_LABEL_KEY,
                LOG_COLLECTION_LABEL_VALUE);

            // Collect logs from all contexts (primary + additional kubeContexts)
            collectLogsFromAllContexts(context, testConfig, logCollectionSelector, logCollector);

            LOGGER.debug("Log collection completed");
        } catch (Exception e) {
            LOGGER.error("Failed to collect logs", e);
        }
    }

    /**
     * Collects logs from primary kubeContext and all additional contexts.
     * Namespace discovery is purely label-based — all namespaces created via
     * {@code @ClassNamespace} or {@code @MethodNamespace} are auto-labeled.
     */
    private void collectLogsFromAllContexts(ExtensionContext context, TestConfig testConfig,
                                            LabelSelector logCollectionSelector, LogCollector primaryLogCollector) {
        // Collect from primary kubeContext using label-based namespace discovery
        KubeResourceManager primaryResourceManager = contextProvider.getResourceManager(context);
        List<String> primaryNamespaces = collectNamespacesWithLabel(
            primaryResourceManager, logCollectionSelector, KubeTestConstants.DEFAULT_CONTEXT_NAME);

        if (!primaryNamespaces.isEmpty()) {
            LOGGER.debug("Collecting logs from primary kubeContext, namespaces: {}", primaryNamespaces);
            primaryLogCollector.collectFromNamespaces(primaryNamespaces.toArray(new String[0]));
            primaryLogCollector.collectClusterWideResources();
        }

        // Collect from each additional kubeContext using kubeContext-specific LogCollectors
        Map<String, KubeResourceManager> contextManagers = contextProvider.getKubeContextManagers(context);
        for (Map.Entry<String, KubeResourceManager> entry : contextManagers.entrySet()) {
            String contextName = entry.getKey();
            KubeResourceManager contextManager = entry.getValue();

            // Find labeled namespaces in this kubeContext
            List<String> contextNamespaces = collectNamespacesWithLabel(
                contextManager, logCollectionSelector, contextName);

            if (!contextNamespaces.isEmpty()) {
                LOGGER.debug("Collecting logs from kubeContext {}, namespaces: {}",
                    contextName, contextNamespaces);

                // Create kubeContext-specific LogCollector with proper KubeClient
                LogCollector contextLogCollector =
                    createLogCollectorForContext(testConfig, context, contextManager, contextName);
                contextLogCollector.collectFromNamespaces(contextNamespaces.toArray(new String[0]));
                contextLogCollector.collectClusterWideResources();
            }
        }

        LOGGER.debug("Multi-kubeContext log collection completed");
    }

    /**
     * Creates a kubeContext-specific LogCollector configured with the appropriate KubeClient
     * for the given kubeContext. This ensures log collection uses the correct kubeconfig.
     */
    private LogCollector createLogCollectorForContext(TestConfig testConfig,
                                                      ExtensionContext context,
                                                      KubeResourceManager contextManager,
                                                      String contextName) {
        String logPath = getLogPath(context, testConfig, contextName);

        LOGGER.debug("Creating log collector for kubeContext {}, path: {}", contextName, logPath);

        return createLogBuilder(testConfig, contextManager, logPath).build();
    }

    /**
     * Collects namespaces that have the log collection label in the specified kubeContext.
     */
    private List<String> collectNamespacesWithLabel(KubeResourceManager resourceManager,
                                                    LabelSelector logCollectionSelector, String contextName) {
        try {
            List<String> labeledNamespaces = resourceManager.kubeClient().getClient()
                .namespaces()
                .withLabelSelector(logCollectionSelector)
                .list()
                .getItems()
                .stream()
                .map(ns -> ns.getMetadata().getName())
                .toList();

            LOGGER.debug("Found {} labeled namespaces in kubeContext {}: {}",
                labeledNamespaces.size(), contextName, labeledNamespaces);

            return labeledNamespaces;
        } catch (Exception e) {
            LOGGER.warn("Failed to query labeled namespaces in kubeContext {}: {}",
                contextName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Gets the stored LogCollector from the extension kubeContext.
     */
    private LogCollector getLogCollector(ExtensionContext context) {
        return contextStoreHelper.getLogCollector(context);
    }

    private String getLogPath(ExtensionContext extContext, TestConfig testConfig, String contextName) {
        return testConfig.logCollectionPath().isEmpty() ?
            TestUtils.getLogPath(Paths.get
                    (System.getProperty("user.dir"), "target", "test-logs").toString(),
                extContext, contextName).toString() :
            TestUtils.getLogPath(testConfig.logCollectionPath(), extContext, contextName).toString();
    }

    // ===============================
    // Context Provider Interface
    // ===============================

    /**
     * Interface to abstract kubeContext operations for dependency injection.
     * This allows LogCollectionManager to work with multi-kubeContext functionality
     * without directly depending on specific implementation details.
     */
    public interface MultiKubeContextProvider {
        /**
         * Gets the primary resource manager for the extension kubeContext.
         *
         * @param context the extension kubeContext
         * @return the primary resource manager
         */
        KubeResourceManager getResourceManager(ExtensionContext context);

        /**
         * Gets all Kubernetes kubeContext managers for multi-kubeContext operations.
         *
         * @param context the extension kubeContext
         * @return map of Kubernetes kubeContext names to resource managers
         */
        Map<String, KubeResourceManager> getKubeContextManagers(ExtensionContext context);
    }
}
