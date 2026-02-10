/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles all exception scenarios across the test lifecycle.
 * This class centralizes exception handling logic and coordinates
 * log collection and cleanup when tests fail.
 */
class ExceptionHandlerDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandlerDelegate.class);

    private final ConfigurationManager configurationManager;
    private final LogCollectionCallback logCollectionCallback;
    private final CleanupCallback cleanupCallback;

    /**
     * Creates a new ExceptionHandlerDelegate with the given dependencies.
     *
     * @param configurationManager  provides access to test configuration
     * @param logCollectionCallback callback for collecting logs on failure
     * @param cleanupCallback       callback for performing cleanup on failure
     */
    ExceptionHandlerDelegate(ConfigurationManager configurationManager,
                             LogCollectionCallback logCollectionCallback,
                             CleanupCallback cleanupCallback) {
        this.configurationManager = configurationManager;
        this.logCollectionCallback = logCollectionCallback;
        this.cleanupCallback = cleanupCallback;
    }

    // ===============================
    // Exception Handler Methods
    // ===============================

    /**
     * Handles test execution exceptions.
     */
    public void handleTestExecutionException(ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        LOGGER.error("Test failed during execution: {}", context.getDisplayName(), throwable);
        handleTestFailure(context, "test-execution");
        throw throwable;
    }

    /**
     * Handles beforeAll method execution exceptions.
     */
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        LOGGER.error("Test failed during beforeAll: {}", context.getDisplayName(), throwable);
        handleTestFailure(context, "before-all");
        throw throwable;
    }

    /**
     * Handles beforeEach method execution exceptions.
     */
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        LOGGER.error("Test failed during beforeEach: {}", context.getDisplayName(), throwable);
        handleTestFailure(context, "before-each");
        throw throwable;
    }

    /**
     * Handles afterEach method execution exceptions.
     */
    public void handleAfterEachMethodExecutionException(ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        LOGGER.error("Test failed during afterEach: {}", context.getDisplayName(), throwable);
        handleTestFailure(context, "after-each");
        throw throwable;
    }

    /**
     * Handles afterAll method execution exceptions.
     */
    public void handleAfterAllMethodExecutionException(ExtensionContext context, @NonNull Throwable throwable)
        throws Throwable {
        LOGGER.error("Test failed during afterAll: {}", context.getDisplayName(), throwable);
        handleTestFailure(context, "after-all");
        throw throwable;
    }

    // ===============================
    // Core Exception Handling Logic
    // ===============================

    /**
     * Centralized test failure handling logic.
     * Coordinates log collection and cleanup based on test configuration.
     */
    private void handleTestFailure(ExtensionContext context, String phase) {
        TestConfig testConfig = configurationManager.getTestConfig(context);
        if (testConfig == null) {
            return;
        }

        // Collect logs based on strategy
        if (testConfig.collectLogs()) {
            LogCollectionStrategy strategy = testConfig.logCollectionStrategy();
            if (strategy == LogCollectionStrategy.ON_FAILURE ||
                strategy == LogCollectionStrategy.AFTER_EACH) {

                String testName = context.getDisplayName().replace("()", "");
                String suffix = String.format("failure-%s-%s", phase, testName.toLowerCase());
                logCollectionCallback.collectLogs(context, suffix);
            }
        }

        // Handle cleanup on failure
        if (testConfig.cleanup() == CleanupStrategy.AUTOMATIC) {
            LOGGER.info("Cleaning up resources due to test failure in phase: {}", phase);
            cleanupCallback.handleAutomaticCleanup(context, testConfig);
        }
    }

    // ===============================
    // Callback Interfaces
    // ===============================

    /**
     * Callback interface for log collection operations.
     * This allows the ExceptionHandlerDelegate to request log collection
     * without directly depending on the LogCollectionManager.
     */
    @FunctionalInterface
    public interface LogCollectionCallback {
        /**
         * Collects logs for the given test kubeContext and suffix.
         *
         * @param context the extension kubeContext
         * @param suffix  the suffix to append to log collection
         */
        void collectLogs(ExtensionContext context, String suffix);
    }

    /**
     * Callback interface for cleanup operations.
     * This allows the ExceptionHandlerDelegate to request cleanup
     * without directly depending on resource management.
     */
    @FunctionalInterface
    public interface CleanupCallback {
        /**
         * Handles automatic cleanup for the given test kubeContext and configuration.
         *
         * @param context    the extension kubeContext
         * @param testConfig the test configuration
         */
        void handleAutomaticCleanup(ExtensionContext context, TestConfig testConfig);
    }
}