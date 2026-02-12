/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * JUnit 6 extension for Kubernetes testing.
 * This extension provides automatic setup and teardown of Kubernetes resources,
 * dependency injection of Kubernetes clients, namespace management, and comprehensive
 * log collection.
 * <p>
 * Key features:
 * - Multi-namespace support with automatic labeling for log collection
 * - Comprehensive log collection via exception handlers (catches failures in ANY test phase)
 * - Automatic resource cleanup with configurable strategies
 * - Dependency injection for Kubernetes clients and resources
 * <p>
 * Log collection is triggered by exception handlers that catch failures from:
 * - beforeAll methods
 * - beforeEach methods
 * - test execution
 * - afterEach methods
 * - afterAll methods
 * <p>
 * This ensures no test failures are missed regardless of where they occur in the
 * test lifecycle, which is superior to manual log collection in specific methods.
 */
public class KubernetesTestExtension implements BeforeAllCallback, AfterAllCallback,
    BeforeEachCallback, AfterEachCallback, ParameterResolver,
    TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler,
    NamespaceService.MultiKubeContextProvider, LogCollectionService.MultiKubeContextProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesTestExtension.class);

    // Helper for kubeContext store operations
    private final ContextStoreHelper contextStoreHelper;

    // Configuration management
    private final ConfigurationService configurationService;

    // Dependency injection
    private final DependencyInjector dependencyInjector;

    // Exception handling
    private final ExceptionHandlerDelegate exceptionHandler;

    // Namespace management
    private final NamespaceService namespaceService;

    // Log collection management
    private final LogCollectionService logCollectionService;


    /**
     * Default constructor for production use.
     */
    public KubernetesTestExtension() {
        this(new ContextStoreHelper());
    }

    /**
     * Package-private constructor for testing purposes only.
     * This constructor should not be used in production code.
     */
    KubernetesTestExtension(ContextStoreHelper contextStoreHelper) {
        KubeResourceManager.get();
        this.contextStoreHelper = contextStoreHelper;
        this.configurationService = new ConfigurationService(contextStoreHelper);
        this.dependencyInjector = new DependencyInjector(contextStoreHelper);

        // Create namespace manager with multi-kubeContext provider
        this.namespaceService = new NamespaceService(contextStoreHelper, this);

        // Create log collection manager with kubeContext provider
        this.logCollectionService = new LogCollectionService(contextStoreHelper, configurationService, this);

        // Create exception handler with callbacks for log collection and cleanup
        this.exceptionHandler = new ExceptionHandlerDelegate(
            configurationService,
            logCollectionService::collectLogs,  // Log collection callback
            this::handleAutomaticCleanup  // Cleanup callback
        );
    }

    @Override
    public void beforeAll(@NonNull ExtensionContext context) throws Exception {
        logVisualSeparator(context);
        LOGGER.info("TestClass {} STARTED", context.getRequiredTestClass().getName());
        LOGGER.info("Setting up Kubernetes test environment for class: {}",
            context.getRequiredTestClass().getSimpleName());

        TestConfig testConfig = configurationService.createAndStoreTestConfig(context);

        // Set up KubeResourceManager
        // Get ResourceManager for the default context (primary test context)
        String contextId = KubeTestConstants.DEFAULT_CONTEXT_NAME;
        KubeResourceManager resourceManager = KubeResourceManager.getForContext(contextId);

        resourceManager.setTestContext(context);
        contextStoreHelper.putResourceManager(context, resourceManager);

        // Configure YAML storage if enabled
        if (testConfig.storeYaml()) {
            String yamlPath = testConfig.yamlStorePath().isEmpty() ?
                Paths.get("target", "test-yamls").toString() : testConfig.yamlStorePath();
            resourceManager.setStoreYamlPath(yamlPath);
        }

        // Set up log collection callback BEFORE creating namespaces
        if (testConfig.collectLogs()) {
            logCollectionService.setupLogCollector(context, testConfig, resourceManager);
            namespaceService.setupNamespaceAutoLabeling(resourceManager);
        }

        // Set up namespaces (this will trigger the auto-labeling callback)
        namespaceService.setupNamespaces(context, testConfig, resourceManager);

        // Inject fields for PER_CLASS lifecycle tests that use @BeforeAll methods
        injectTestClassFields(context);

        logVisualSeparator(context);
        LOGGER.info("Kubernetes test environment ready for: {}",
            context.getRequiredTestClass().getSimpleName());
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        LOGGER.info("Cleaning up Kubernetes test environment for class: {}",
            context.getRequiredTestClass().getSimpleName());

        TestConfig testConfig = getTestConfig(context);
        if (testConfig == null) {
            return;
        }

        // Handle cleanup by delegating to ResourceManager logic
        handleAutomaticCleanup(context, testConfig);

        // Clean up created namespaces if any were created during the test
        List<String> createdNamespaces = contextStoreHelper.getCreatedNamespaceNames(context);
        if (createdNamespaces != null && !createdNamespaces.isEmpty()) {
            namespaceService.cleanupNamespaces(context, testConfig);
        }

        // Clean up created namespaces from multi-kubeContext scenarios
        cleanupMultiContextNamespaces(context, testConfig);

        // Close all kubeContext closers (primary + multi-kubeContext)
        cleanupAllContextClosers(context);

        // Clean up ThreadLocal variables to prevent thread reuse issues
        cleanupThreadLocalVariables(context);

        // Reset auto-labeling configuration for subsequent tests
        NamespaceService.resetAutoLabelingConfiguration();

        LOGGER.info("TestClass {} FINISHED", context.getRequiredTestClass().getName());
        logVisualSeparator(context);
    }

    @Override
    public void beforeEach(@NonNull ExtensionContext context) {
        TestConfig testConfig = getTestConfig(context);
        if (testConfig == null) {
            return;
        }

        KubeResourceManager resourceManager = getResourceManager(context);
        if (resourceManager != null) {
            resourceManager.setTestContext(context);
        }

        // Inject fields into the current test instance
        injectTestClassFields(context);

        logVisualSeparator(context);
        LOGGER.info("Test {}.{} STARTED", context.getRequiredTestClass().getName(),
            context.getDisplayName().replace("()", ""));
    }

    @Override
    public void afterEach(@NonNull ExtensionContext context) {
        TestConfig testConfig = getTestConfig(context);
        if (testConfig == null) {
            return;
        }

        String state = "SUCCEEDED";
        if (context.getExecutionException().isPresent()) {
            state = "FAILED";
        }

        // Collect logs if configured for AFTER_EACH (successful completion)
        if ("SUCCEEDED".equals(state) && testConfig.collectLogs() &&
            testConfig.logCollectionStrategy() == LogCollectionStrategy.AFTER_EACH) {
            String testName = context.getDisplayName().replace("()", "");
            logCollectionService.collectLogs(context, "after-each-success-" + testName.toLowerCase());
        }

        // Handle cleanup by delegating to ResourceManager logic
        handleAutomaticCleanup(context, testConfig);

        LOGGER.info("Test {}.{} {}", context.getRequiredTestClass().getName(),
            context.getDisplayName().replace("()", ""), state);
        logVisualSeparator(context);
    }

    // ===============================
    // Exception Handlers for Comprehensive Log Collection
    // ===============================

    @Override
    public void handleTestExecutionException(@NonNull ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        exceptionHandler.handleTestExecutionException(context, throwable);
    }

    @Override
    public void handleBeforeAllMethodExecutionException(@NonNull ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        exceptionHandler.handleBeforeAllMethodExecutionException(context, throwable);
    }

    @Override
    public void handleBeforeEachMethodExecutionException(@NonNull ExtensionContext context,
                                                         @NonNull Throwable throwable)
        throws Throwable {
        exceptionHandler.handleBeforeEachMethodExecutionException(context, throwable);
    }

    @Override
    public void handleAfterEachMethodExecutionException(@NonNull ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        exceptionHandler.handleAfterEachMethodExecutionException(context, throwable);
    }

    @Override
    public void handleAfterAllMethodExecutionException(@NonNull ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        exceptionHandler.handleAfterAllMethodExecutionException(context, throwable);
    }


    @Override
    public boolean supportsParameter(@NonNull ParameterContext parameterContext,
                                     @NonNull ExtensionContext extensionContext) throws ParameterResolutionException {
        return dependencyInjector.supportsParameter(parameterContext);
    }

    @Override
    public Object resolveParameter(@NonNull ParameterContext parameterContext,
                                   @NonNull ExtensionContext extensionContext) throws ParameterResolutionException {
        return dependencyInjector.resolveParameter(parameterContext, extensionContext);
    }


    private void injectTestClassFields(ExtensionContext context) {
        dependencyInjector.injectTestClassFields(context);
    }


    private TestConfig getTestConfig(ExtensionContext context) {
        return configurationService.getTestConfig(context);
    }


    private void logVisualSeparator(ExtensionContext context) {
        TestConfig testConfig = getTestConfig(context);
        if (testConfig != null) {
            LoggerUtils.logSeparator(testConfig.visualSeparatorChar(), testConfig.visualSeparatorLength());
        } else {
            LoggerUtils.logSeparator();
        }
    }


    /**
     * Handle automatic cleanup by delegating to the same logic as @ResourceManager.
     * This mimics what ResourceManagerCleanerExtension does but checks our CleanupStrategy instead
     * of looking for @ResourceManager annotation.
     */
    private void handleAutomaticCleanup(ExtensionContext context, TestConfig testConfig) {
        if (testConfig.cleanup() == CleanupStrategy.AUTOMATIC) {
            KubeResourceManager resourceManager = contextStoreHelper.getResourceManager(context);
            resourceManager.setTestContext(context);
            resourceManager.deleteResources(true);
        }
    }

    /**
     * Cleans up all kubeContext closers to prevent resource leaks.
     * This includes the primary kubeContext closer and all multi-kubeContext closers.
     */
    private void cleanupAllContextClosers(ExtensionContext context) {
        // Context closers are no longer needed with the new per-context singleton approach.
        // KubeResourceManager instances are now context-specific and don't require ThreadLocal switching.
        LOGGER.debug("Context closer cleanup is no longer needed with per-context singleton architecture");
    }

    /**
     * Cleans up ThreadLocal variables in KubeResourceManager to prevent thread reuse issues.
     * This is critical for parallel test execution and thread pool reuse.
     */
    private void cleanupThreadLocalVariables(ExtensionContext context) {
        try {
            // Get the primary ResourceManager and clean its ThreadLocal variables
            KubeResourceManager primaryManager = contextStoreHelper.getResourceManager(context);
            if (primaryManager != null) {
                primaryManager.cleanTestContext();
                primaryManager.cleanClusterContext();
                LOGGER.debug("Cleaned ThreadLocal variables for primary ResourceManager");
            }

            // Clean ThreadLocal variables for all kubeContext-specific managers
            Map<String, KubeResourceManager> contextManagers = contextStoreHelper.getContextManagers(context);
            if (contextManagers != null) {
                for (Map.Entry<String, KubeResourceManager> entry : contextManagers.entrySet()) {
                    try {
                        entry.getValue().cleanTestContext();
                        entry.getValue().cleanClusterContext();
                        LOGGER.debug("Cleaned ThreadLocal variables for kubeContext: {}", entry.getKey());
                    } catch (Exception e) {
                        LOGGER.warn("Failed to clean ThreadLocal variables for kubeContext '{}': {}",
                            entry.getKey(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error during ThreadLocal cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleans up namespaces created in multi-kubeContext scenarios.
     * This ensures that kubeContext-specific namespaces are properly deleted.
     */
    private void cleanupMultiContextNamespaces(ExtensionContext context, TestConfig testConfig) {
        try {
            // Get all kubeContext managers to find contexts that had namespaces created
            Map<String, KubeResourceManager> contextManagers = contextStoreHelper.getContextManagers(context);
            if (contextManagers == null || contextManagers.isEmpty()) {
                LOGGER.debug("No multi-kubeContext managers found, skipping multi-kubeContext namespace cleanup");
                return;
            }

            // Clean up namespaces for each kubeContext that created namespaces
            for (String clusterContext : contextManagers.keySet()) {
                List<String> contextCreatedNamespaces =
                    contextStoreHelper.getOrCreateCreatedNamespacesForContext(context, clusterContext);

                if (contextCreatedNamespaces != null && !contextCreatedNamespaces.isEmpty()) {
                    LOGGER.info("Cleaning up {} namespaces created in kubeContext '{}': {}",
                        contextCreatedNamespaces.size(), clusterContext,
                        String.join(", ", contextCreatedNamespaces));

                    KubeResourceManager contextManager = contextManagers.get(clusterContext);
                    try {
                        for (String namespaceName : contextCreatedNamespaces) {
                            Namespace namespace = new NamespaceBuilder()
                                .withNewMetadata()
                                .withName(namespaceName)
                                .endMetadata()
                                .build();
                            LoggerUtils.logResource("Deleting", Level.DEBUG, namespace);
                            contextManager.deleteResourceWithWait(namespace);
                        }
                        LOGGER.info("Successfully cleaned up {} namespaces from kubeContext '{}'",
                            contextCreatedNamespaces.size(), clusterContext);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to clean up namespaces in kubeContext '{}': {}",
                            clusterContext, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error during multi-kubeContext namespace cleanup: {}", e.getMessage(), e);
        }
    }

    // ===============================
    // Multi-Context Support Methods (MultiKubeContextProvider Implementation)
    // ===============================

    /**
     * Gets or creates a KubeResourceManager for the specified kubeContext.
     */
    @Override
    public KubeResourceManager getResourceManagerForKubeContext(ExtensionContext context, String kubeContext) {
        // Get the cache of kubeContext managers
        Map<String, KubeResourceManager> contextManagers = contextStoreHelper.getOrCreateContextManagers(context);

        // Get or create resource manager for this kubeContext
        return contextManagers.computeIfAbsent(kubeContext, ctx -> {
            LOGGER.info("Creating ResourceManager for kubeContext: {}", ctx);

            try {
                // Get context-specific ResourceManager instance (per-context singleton)
                KubeResourceManager manager = KubeResourceManager.getForContext(ctx);

                // Set test context
                manager.setTestContext(context);

                // Configure YAML storage if enabled (inherit from main test config)
                TestConfig testConfig = contextStoreHelper.getTestConfig(context);
                if (testConfig != null && testConfig.storeYaml()) {
                    String yamlPath = testConfig.yamlStorePath().isEmpty() ?
                        Paths.get("target", "test-yamls").toString() : testConfig.yamlStorePath();
                    manager.setStoreYamlPath(yamlPath);
                    LOGGER.debug("Configured YAML storage for kubeContext '{}': {}", ctx, yamlPath);
                }

                return manager;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create ResourceManager for kubeContext: " + ctx, e);
            }
        });
    }

    @Override
    public void storeNamespaceObjectsForKubeContext(ExtensionContext context, String kubeContext,
                                                    Map<String, Namespace> namespaceObjects) {
        contextStoreHelper.putNamespaceObjectsForContext(context, kubeContext, namespaceObjects);
    }

    @Override
    public List<String> getOrCreateCreatedNamespacesForKubeContext(ExtensionContext context, String kubeContext) {
        return contextStoreHelper.getOrCreateCreatedNamespacesForContext(context, kubeContext);
    }

    @Override
    public Map<String, Namespace> getOrCreateNamespaceObjectsForKubeContext(ExtensionContext context,
                                                                            String kubeContext) {
        return contextStoreHelper.getOrCreateNamespaceObjectsForContext(context, kubeContext);
    }

    // ===============================
    // LogCollectionManager.MultiKubeContextProvider Implementation
    // ===============================

    @Override
    public KubeResourceManager getResourceManager(ExtensionContext context) {
        return contextStoreHelper.getResourceManager(context);
    }

    @Override
    public Map<String, KubeResourceManager> getKubeContextManagers(ExtensionContext context) {
        return contextStoreHelper.getContextManagers(context);
    }
}
