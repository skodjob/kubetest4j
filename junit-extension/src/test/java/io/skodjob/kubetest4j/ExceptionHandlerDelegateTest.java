/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ExceptionHandlerDelegate.
 * These tests verify exception handling logic without requiring a real Kubernetes cluster.
 */
@ExtendWith(MockitoExtension.class)
class ExceptionHandlerDelegateTest {

    @Mock
    private ConfigurationManager configurationManager;

    @Mock
    private ExceptionHandlerDelegate.LogCollectionCallback logCollectionCallback;

    @Mock
    private ExceptionHandlerDelegate.CleanupCallback cleanupCallback;

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private Store store;

    private ExceptionHandlerDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new ExceptionHandlerDelegate(configurationManager, logCollectionCallback, cleanupCallback);
        lenient().when(extensionContext.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        lenient().when(extensionContext.getDisplayName()).thenReturn("TestMethod()");
    }

    @Nested
    @DisplayName("Exception Handler Methods Tests")
    class ExceptionHandlerMethodsTests {

        @Test
        @DisplayName("Should handle test execution exception and re-throw it")
        void shouldHandleTestExecutionExceptionAndReThrow() {
            // Given
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.AUTOMATIC, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When/Then
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Verify log collection was triggered
            verify(logCollectionCallback).collectLogs(eq(extensionContext), eq("failure-test-execution-testmethod"));
            verify(cleanupCallback).handleAutomaticCleanup(extensionContext, testConfig);
        }

        @Test
        @DisplayName("Should handle beforeAll method execution exception and re-throw it")
        void shouldHandleBeforeAllMethodExecutionExceptionAndReThrow() {
            // Given
            RuntimeException testException = new RuntimeException("BeforeAll failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.AUTOMATIC, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When/Then
            assertThrows(RuntimeException.class, () ->
                delegate.handleBeforeAllMethodExecutionException(extensionContext, testException));

            // Verify log collection was triggered with correct phase
            verify(logCollectionCallback).collectLogs(eq(extensionContext), eq("failure-before-all-testmethod"));
            verify(cleanupCallback).handleAutomaticCleanup(extensionContext, testConfig);
        }

        @Test
        @DisplayName("Should handle beforeEach method execution exception and re-throw it")
        void shouldHandleBeforeEachMethodExecutionExceptionAndReThrow() {
            // Given
            RuntimeException testException = new RuntimeException("BeforeEach failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.AUTOMATIC, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When/Then
            assertThrows(RuntimeException.class, () ->
                delegate.handleBeforeEachMethodExecutionException(extensionContext, testException));

            // Verify log collection was triggered with correct phase
            verify(logCollectionCallback).collectLogs(eq(extensionContext), eq("failure-before-each-testmethod"));
            verify(cleanupCallback).handleAutomaticCleanup(extensionContext, testConfig);
        }

        @Test
        @DisplayName("Should handle afterEach method execution exception and re-throw it")
        void shouldHandleAfterEachMethodExecutionExceptionAndReThrow() {
            // Given
            RuntimeException testException = new RuntimeException("AfterEach failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.AUTOMATIC, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When/Then
            assertThrows(RuntimeException.class, () ->
                delegate.handleAfterEachMethodExecutionException(extensionContext, testException));

            // Verify log collection was triggered with correct phase
            verify(logCollectionCallback).collectLogs(eq(extensionContext), eq("failure-after-each-testmethod"));
            verify(cleanupCallback).handleAutomaticCleanup(extensionContext, testConfig);
        }

        @Test
        @DisplayName("Should handle afterAll method execution exception and re-throw it")
        void shouldHandleAfterAllMethodExecutionExceptionAndReThrow() {
            // Given
            RuntimeException testException = new RuntimeException("AfterAll failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.AUTOMATIC, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When/Then
            assertThrows(RuntimeException.class, () ->
                delegate.handleAfterAllMethodExecutionException(extensionContext, testException));

            // Verify log collection was triggered with correct phase
            verify(logCollectionCallback).collectLogs(eq(extensionContext), eq("failure-after-all-testmethod"));
            verify(cleanupCallback).handleAutomaticCleanup(extensionContext, testConfig);
        }
    }

    @Nested
    @DisplayName("Log Collection Strategy Tests")
    class LogCollectionStrategyTests {

        @Test
        @DisplayName("Should collect logs when strategy is ON_FAILURE")
        void shouldCollectLogsWhenStrategyIsOnFailure() {
            // Given
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.MANUAL, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then
            verify(logCollectionCallback).collectLogs(eq(extensionContext), eq("failure-test-execution-testmethod"));
        }

        @Test
        @DisplayName("Should collect logs when strategy is AFTER_EACH")
        void shouldCollectLogsWhenStrategyIsAfterEach() {
            // Given
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.AFTER_EACH, CleanupStrategy.MANUAL, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then
            verify(logCollectionCallback).collectLogs(eq(extensionContext), eq("failure-test-execution-testmethod"));
        }

        @Test
        @DisplayName("Should not collect logs when strategy is NEVER")
        void shouldNotCollectLogsWhenStrategyIsNever() {
            // Given
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.NEVER, CleanupStrategy.AUTOMATIC, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then
            verifyNoInteractions(logCollectionCallback);
            verify(cleanupCallback).handleAutomaticCleanup(extensionContext, testConfig);
        }

        @Test
        @DisplayName("Should not collect logs when collectLogs is disabled")
        void shouldNotCollectLogsWhenCollectLogsIsDisabled() {
            // Given
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE,
                CleanupStrategy.AUTOMATIC, false);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then
            verifyNoInteractions(logCollectionCallback);
            verify(cleanupCallback).handleAutomaticCleanup(extensionContext, testConfig);
        }
    }

    @Nested
    @DisplayName("Cleanup Strategy Tests")
    class CleanupStrategyTests {

        @Test
        @DisplayName("Should trigger cleanup when strategy is AUTOMATIC")
        void shouldTriggerCleanupWhenStrategyIsAutomatic() {
            // Given
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.NEVER, CleanupStrategy.AUTOMATIC, false);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then
            verify(cleanupCallback).handleAutomaticCleanup(extensionContext, testConfig);
        }

        @Test
        @DisplayName("Should not trigger cleanup when strategy is MANUAL")
        void shouldNotTriggerCleanupWhenStrategyIsManual() {
            // Given
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.MANUAL, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then
            verifyNoInteractions(cleanupCallback);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null TestConfig gracefully")
        void shouldHandleNullTestConfigGracefully() {
            // Given
            RuntimeException testException = new RuntimeException("Test failed");
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(null);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then - should not crash and should not call any callbacks
            verifyNoInteractions(logCollectionCallback);
            verifyNoInteractions(cleanupCallback);
        }

        @Test
        @DisplayName("Should handle display name without parentheses")
        void shouldHandleDisplayNameWithoutParentheses() {
            // Given
            when(extensionContext.getDisplayName()).thenReturn("SimpleTestName");
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.MANUAL, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then
            verify(logCollectionCallback).collectLogs(eq(extensionContext),
                eq("failure-test-execution-simpletestname"));
        }

        @Test
        @DisplayName("Should handle complex display name properly")
        void shouldHandleComplexDisplayNameProperly() {
            // Given
            when(extensionContext.getDisplayName()).thenReturn("Complex Test Name (With Params)");
            RuntimeException testException = new RuntimeException("Test failed");
            TestConfig testConfig = createTestConfig(LogCollectionStrategy.ON_FAILURE, CleanupStrategy.MANUAL, true);
            when(configurationManager.getTestConfig(extensionContext)).thenReturn(testConfig);

            // When
            assertThrows(RuntimeException.class, () ->
                delegate.handleTestExecutionException(extensionContext, testException));

            // Then
            verify(logCollectionCallback).collectLogs(eq(extensionContext),
                eq("failure-test-execution-complex test name (with params)"));
        }
    }

    // Helper method to create TestConfig for testing
    private TestConfig createTestConfig(LogCollectionStrategy logStrategy, CleanupStrategy cleanupStrategy,
                                       boolean collectLogs) {
        return new TestConfig(
            List.of("test-namespace"),
            cleanupStrategy,
            "",
            false,
            "",
            List.of(),
            List.of(),
            "#",
            76,
            collectLogs,
            logStrategy,
            "",
            false,
            List.of("pods"),
            List.of(),
            List.of()
        );
    }
}