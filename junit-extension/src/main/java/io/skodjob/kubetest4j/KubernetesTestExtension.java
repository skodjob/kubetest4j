/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import io.skodjob.kubetest4j.interfaces.ResourceType;
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

import java.nio.file.Paths;
import java.util.Map;

/**
 * JUnit 6 extension for Kubernetes testing.
 * This extension provides automatic setup and teardown of Kubernetes resources,
 * dependency injection of Kubernetes clients, namespace management, and comprehensive
 * log collection.
 * <p>
 * Key features:
 * - Namespace management via {@code @ClassNamespace} (class-level) and {@code @MethodNamespace} (per-test)
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

    // Namespace auto-labeling for log collection
    private final NamespaceService namespaceService;

    // Class namespace management (class-level, beforeAll/afterAll)
    private final ClassNamespaceService classNamespaceService;

    // Method namespace management (per-test-method, beforeEach/afterEach)
    private final MethodNamespaceService methodNamespaceService;

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

        // Create namespace service (for auto-labeling only)
        this.namespaceService = new NamespaceService();

        // Create class namespace manager for class-level namespaces
        this.classNamespaceService = new ClassNamespaceService(contextStoreHelper, this);

        // Create method namespace manager for per-test-method namespaces
        this.methodNamespaceService = new MethodNamespaceService(contextStoreHelper, this);

        // Create dependency injector with namespace support
        this.dependencyInjector = new DependencyInjector(contextStoreHelper, methodNamespaceService);

        // Create log collection manager with kubeContext provider
        this.logCollectionService = new LogCollectionService(contextStoreHelper, configurationService, this);

        // Create exception handler with callbacks for log collection and cleanup
        this.exceptionHandler = new ExceptionHandlerDelegate(
            configurationService,
            logCollectionService::collectLogs,  // Log collection callback
            this::handleAutomaticCleanup  // Cleanup callback (safety net for afterEach/afterAll failures)
        );
    }

    @Override
    public void beforeAll(@NonNull ExtensionContext context) throws Exception {
        logVisualSeparator(context);
        LOGGER.info("TestClass {} STARTED", context.getRequiredTestClass().getName());

        TestConfig testConfig = configurationService.createAndStoreTestConfig(context);

        // Set up KubeResourceManager
        String contextId = KubeTestConstants.DEFAULT_CONTEXT_NAME;
        KubeResourceManager resourceManager = KubeResourceManager.getForContext(contextId);

        resourceManager.setTestContext(context);
        contextStoreHelper.putResourceManager(context, resourceManager);

        // Register resource types from @KubernetesTest annotation
        registerResourceTypes(context, resourceManager);

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

        // Create class namespaces declared via @ClassNamespace on static fields
        classNamespaceService.createClassNamespaces(context);

        // Inject fields for PER_CLASS lifecycle tests that use @BeforeAll methods
        injectTestClassFields(context);

        logVisualSeparator(context);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        TestConfig testConfig = getTestConfig(context);
        if (testConfig == null) {
            return;
        }

        // Handle cleanup by delegating to ResourceManager logic
        handleAutomaticCleanup(context, testConfig);

        // Clean up class namespaces (only those created by the test)
        classNamespaceService.cleanupClassNamespaces(context);

        // Restore previous resource types to prevent leaking to next class
        ResourceType<?>[] previousTypes = contextStoreHelper.getPreviousResourceTypes(context);
        if (previousTypes != null) {
            KubeResourceManager resourceManager = getResourceManager(context);
            if (resourceManager != null) {
                resourceManager.setResourceTypes(previousTypes);
                LOGGER.debug("Restored previous resource types ({} type(s))", previousTypes.length);
            }
        }

        // Clean up ThreadLocal variables to prevent thread reuse issues
        cleanupThreadLocalVariables(context);

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

        // Create method namespaces before field injection so they are available for @MethodNamespace
        methodNamespaceService.createMethodNamespaces(context);

        // Inject fields into the current test instance (includes @MethodNamespace fields)
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
        // KRM's LIFO stack ensures correct deletion order:
        // resources inside namespaces are deleted before the namespaces themselves
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
    public void handleBeforeAllMethodExecutionException(@NonNull ExtensionContext context,
                                                         @NonNull Throwable throwable)
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
    public void handleAfterEachMethodExecutionException(@NonNull ExtensionContext context,
                                                         @NonNull Throwable throwable)
        throws Throwable {
        exceptionHandler.handleAfterEachMethodExecutionException(context, throwable);
    }

    @Override
    public void handleAfterAllMethodExecutionException(@NonNull ExtensionContext context,
                                                        @NonNull Throwable throwable)
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
     * Registers resource types declared via {@code @KubernetesTest(resourceTypes = {...})}.
     * Each class is instantiated via its no-arg constructor and registered globally via
     * {@link KubeResourceManager#setResourceTypes(ResourceType[])}.
     * <p>
     * The previous global types are saved and restored in {@link #afterAll} to prevent
     * leaking state between test classes.
     *
     * @param context         the extension context
     * @param resourceManager the resource manager to register types with
     */
    private void registerResourceTypes(ExtensionContext context, KubeResourceManager resourceManager) {
        Class<? extends ResourceType<?>>[] typeClasses = resolveResourceTypes(context);
        if (typeClasses.length == 0) {
            return;
        }

        ResourceType<?>[] types = new ResourceType<?>[typeClasses.length];

        for (int i = 0; i < typeClasses.length; i++) {
            try {
                types[i] = typeClasses[i].getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Failed to instantiate ResourceType: " + typeClasses[i].getName()
                        + ". Ensure it has a public no-argument constructor.", e);
            }
        }

        // Save current types so they can be restored in afterAll
        contextStoreHelper.putPreviousResourceTypes(context, resourceManager.getResourceTypes());

        resourceManager.setResourceTypes(types);
        LOGGER.debug("Registered {} resource type(s) from @KubernetesTest annotation", types.length);
    }

    /**
     * Resolves resourceTypes by walking the class hierarchy.
     * If the current class's annotation has resourceTypes, use them.
     * Otherwise, walk up parent classes to find the nearest ancestor with resourceTypes declared.
     * This allows child classes to override other annotation parameters (cleanup, collectLogs, etc.)
     * without losing the parent's resourceTypes.
     *
     * @param context the extension context
     * @return the resolved resource type classes, or empty array if none found
     */
    @SuppressWarnings("unchecked")
    private Class<? extends ResourceType<?>>[] resolveResourceTypes(ExtensionContext context) {
        // Check the current class's annotation first
        KubernetesTest annotation = configurationService.getKubernetesTestAnnotation(context);
        if (annotation != null && annotation.resourceTypes().length > 0) {
            return annotation.resourceTypes();
        }

        // Walk up the class hierarchy looking for resourceTypes in parent annotations.
        // Uses getDeclaredAnnotation() to check each class's own annotation only,
        // avoiding @Inherited which would return a grandparent's annotation at the parent level.
        Class<?> current = context.getRequiredTestClass().getSuperclass();
        while (current != null && current != Object.class) {
            KubernetesTest parentAnnotation = current.getDeclaredAnnotation(KubernetesTest.class);
            if (parentAnnotation != null && parentAnnotation.resourceTypes().length > 0) {
                LOGGER.debug("Inherited resourceTypes from parent class {}", current.getName());
                return parentAnnotation.resourceTypes();
            }
            current = current.getSuperclass();
        }

        return new Class[0];
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
                LOGGER.debug("Cleaned up ThreadLocal variables for primary ResourceManager");
            }

            // Clean ThreadLocal variables for all kubeContext-specific managers
            Map<String, KubeResourceManager> contextManagers = contextStoreHelper.getContextManagers(context);
            if (contextManagers != null) {
                for (Map.Entry<String, KubeResourceManager> entry : contextManagers.entrySet()) {
                    try {
                        entry.getValue().cleanTestContext();
                        entry.getValue().cleanClusterContext();
                        LOGGER.debug("Cleaned up ThreadLocal variables for kubeContext {}", entry.getKey());
                    } catch (Exception e) {
                        LOGGER.warn("Failed to clean up ThreadLocal variables for kubeContext {}: {}",
                            entry.getKey(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to clean up ThreadLocal variables: {}", e.getMessage(), e);
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
            LOGGER.debug("Creating ResourceManager for kubeContext {}", ctx);

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
                    LOGGER.debug("Configured YAML storage for kubeContext {}: {}", ctx, yamlPath);
                }

                return manager;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create ResourceManager for kubeContext: " + ctx, e);
            }
        });
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
