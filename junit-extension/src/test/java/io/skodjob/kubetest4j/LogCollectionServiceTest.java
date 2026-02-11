/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LogCollectionManager.
 * These tests verify log collection logic without requiring a real Kubernetes cluster.
 */
@ExtendWith(MockitoExtension.class)
class LogCollectionServiceTest {

    @Mock
    private ContextStoreHelper contextStoreHelper;

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private LogCollectionService.MultiKubeContextProvider contextProvider;

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private Store store;

    @Mock
    private KubeResourceManager resourceManager;

    @Mock
    private KubeClient kubeClient;

    @Mock
    private KubeCmdClient kubeCmdClient;

    @Mock
    private KubernetesClient k8sClient;

    @Mock
    private LogCollector logCollector;

    @Mock
    private NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> namespaceOperation;

    @Mock
    private FilterWatchListDeletable<Namespace, NamespaceList, Resource<Namespace>> labelSelector;

    private LogCollectionService manager;

    @BeforeEach
    void setUp() {
        manager = new LogCollectionService(contextStoreHelper, configurationService, contextProvider);
        lenient().when(extensionContext.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        lenient().when(resourceManager.kubeClient()).thenReturn(kubeClient);
        lenient().when(resourceManager.kubeCmdClient()).thenReturn(kubeCmdClient);
        lenient().when(kubeClient.getClient()).thenReturn(k8sClient);
        lenient().when(k8sClient.namespaces()).thenReturn(namespaceOperation);
        lenient().when(namespaceOperation.withLabelSelector(any(LabelSelector.class))).thenReturn(labelSelector);
    }

    @Nested
    @DisplayName("Log Collector Setup Tests")
    class LogCollectorSetupTests {

        @Test
        @DisplayName("Should setup log collector with default path when path is empty")
        void shouldSetupLogCollectorWithDefaultPathWhenPathIsEmpty() {
            // Given
            TestConfig testConfig = createTestConfig("", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);

            // When (using empty path will use default behavior)
            manager.setupLogCollector(extensionContext, testConfig, resourceManager);

            // Then
            verify(contextStoreHelper).putLogCollector(eq(extensionContext), any(LogCollector.class));
        }

        @Test
        @DisplayName("Should setup log collector with custom path when path is provided")
        void shouldSetupLogCollectorWithCustomPathWhenPathIsProvided() {
            // Given
            TestConfig testConfig = createTestConfig("/custom/log/path", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);

            // When
            manager.setupLogCollector(extensionContext, testConfig, resourceManager);

            // Then
            verify(contextStoreHelper).putLogCollector(eq(extensionContext), any(LogCollector.class));
        }

        @Test
        @DisplayName("Should setup log collector with cluster-wide resources when configured")
        void shouldSetupLogCollectorWithClusterWideResourcesWhenConfigured() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods", "services"), List.of("nodes", "persistentvolumes"), false);

            // When
            manager.setupLogCollector(extensionContext, testConfig, resourceManager);

            // Then
            verify(contextStoreHelper).putLogCollector(eq(extensionContext), any(LogCollector.class));
        }

        @Test
        @DisplayName("Should setup log collector with previous logs collection when configured")
        void shouldSetupLogCollectorWithPreviousLogsCollectionWhenConfigured() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), true);

            // When
            manager.setupLogCollector(extensionContext, testConfig, resourceManager);

            // Then
            verify(contextStoreHelper).putLogCollector(eq(extensionContext), any(LogCollector.class));
        }
    }

    @Nested
    @DisplayName("Log Collection Execution Tests")
    class LogCollectionExecutionTests {

        @Test
        @DisplayName("Should skip log collection when log collector is null")
        void shouldSkipLogCollectionWhenLogCollectorIsNull() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextStoreHelper.getLogCollector(extensionContext)).thenReturn(null);

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            // Should not crash and should log a warning
            // Verify no interactions with kubeContext provider since we return early
            verifyNoInteractions(contextProvider);
        }

        @Test
        @DisplayName("Should collect logs from primary kubeContext successfully")
        void shouldCollectLogsFromPrimaryContextSuccessfully() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextStoreHelper.getLogCollector(extensionContext)).thenReturn(logCollector);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            // Mock namespace query
            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            verify(logCollector).collectFromNamespaces(any(String[].class));
        }

        @Test
        @DisplayName("Should collect logs from multiple contexts")
        void shouldCollectLogsFromMultipleContexts() {
            // Given
            TestConfig.KubeContextMappingConfig contextMapping = new TestConfig.KubeContextMappingConfig(
                "staging", List.of("stg-ns"), CleanupStrategy.AUTOMATIC, List.of(), List.of()
            );
            TestConfig testConfig = createTestConfigWithContexts("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false, List.of(contextMapping));

            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextStoreHelper.getLogCollector(extensionContext)).thenReturn(logCollector);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);

            // Mock additional kubeContext
            KubeResourceManager stagingManager = mock(KubeResourceManager.class);
            KubeClient stagingKubeClient = mock(KubeClient.class);
            KubeCmdClient stagingKubeCmdClient = mock(KubeCmdClient.class);
            KubernetesClient stagingK8sClient = mock(KubernetesClient.class);
            NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> stagingNamespaceOp =
                mock(NonNamespaceOperation.class);
            FilterWatchListDeletable<Namespace, NamespaceList, Resource<Namespace>> stagingLabelSelector =
                mock(FilterWatchListDeletable.class);

            when(stagingManager.kubeClient()).thenReturn(stagingKubeClient);
            when(stagingManager.kubeCmdClient()).thenReturn(stagingKubeCmdClient);
            when(stagingKubeClient.getClient()).thenReturn(stagingK8sClient);
            when(stagingK8sClient.namespaces()).thenReturn(stagingNamespaceOp);
            when(stagingNamespaceOp.withLabelSelector(any(LabelSelector.class))).thenReturn(stagingLabelSelector);

            Map<String, KubeResourceManager> contextManagers = Map.of("staging", stagingManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(contextManagers);

            // Mock namespace queries
            NamespaceList primaryNamespaceList = mock(NamespaceList.class);
            NamespaceList stagingNamespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(primaryNamespaceList);
            when(stagingLabelSelector.list()).thenReturn(stagingNamespaceList);
            when(primaryNamespaceList.getItems()).thenReturn(List.of());
            when(stagingNamespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            // Verify primary kubeContext log collection
            verify(logCollector).collectFromNamespaces(any(String[].class));
            // Additional kubeContext creates its own LogCollector, so we can't easily verify it
            // but we can verify that the kubeContext managers were queried
            verify(contextProvider).getKubeContextManagers(extensionContext);
        }

        @Test
        @DisplayName("Should handle exception during log collection gracefully")
        void shouldHandleExceptionDuringLogCollectionGracefully() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextStoreHelper.getLogCollector(extensionContext)).thenReturn(logCollector);
            when(contextProvider.getResourceManager(extensionContext))
                .thenThrow(new RuntimeException("Test exception"));

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            // Should not crash and should handle the exception gracefully
            verify(contextProvider).getResourceManager(extensionContext);
        }

        @Test
        @DisplayName("Should collect labeled namespaces from kubeContext")
        void shouldCollectLabeledNamespacesFromContext() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextStoreHelper.getLogCollector(extensionContext)).thenReturn(logCollector);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            // Mock labeled namespace
            Namespace labeledNamespace = new NamespaceBuilder()
                .withNewMetadata()
                .withName("labeled-namespace")
                .addToLabels("test-frame.io/log-collection", "enabled")
                .endMetadata()
                .build();

            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of(labeledNamespace));

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            // Verify that log collection was attempted with both test namespaces and labeled namespaces
            verify(logCollector).collectFromNamespaces(any(String[].class));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle exception when querying labeled namespaces")
        void shouldHandleExceptionWhenQueryingLabeledNamespaces() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextStoreHelper.getLogCollector(extensionContext)).thenReturn(logCollector);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            // Mock exception when querying namespaces
            when(labelSelector.list()).thenThrow(new RuntimeException("Kubernetes API error"));

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            // Should not crash and should continue with test namespaces
            verify(logCollector).collectFromNamespaces(any(String[].class));
        }

        @Test
        @DisplayName("Should handle empty namespace list gracefully")
        void shouldHandleEmptyNamespaceListGracefully() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextStoreHelper.getLogCollector(extensionContext)).thenReturn(logCollector);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            // Mock empty namespace list
            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            // Should still attempt log collection with test namespaces
            verify(logCollector).collectFromNamespaces(any(String[].class));
        }
    }

    // Helper methods to create TestConfig instances for testing
    private TestConfig createTestConfig(String logPath, LogCollectionStrategy strategy,
                                        List<String> namespacedResources, List<String> clusterResources,
                                        boolean collectPreviousLogs) {
        return new TestConfig(
            List.of("test-namespace"),
            CleanupStrategy.AUTOMATIC,
            "",
            false,
            "",
            List.of(),
            List.of(),
            "#",
            76,
            true,
            strategy,
            logPath,
            collectPreviousLogs,
            namespacedResources,
            clusterResources,
            List.of()
        );
    }

    private TestConfig createTestConfigWithContexts(String logPath, LogCollectionStrategy strategy,
                                                    List<String> namespacedResources, List<String> clusterResources,
                                                    boolean collectPreviousLogs,
                                                    List<TestConfig.KubeContextMappingConfig> contextMappings) {
        return new TestConfig(
            List.of("test-namespace"),
            CleanupStrategy.AUTOMATIC,
            "",
            false,
            "",
            List.of(),
            List.of(),
            "#",
            76,
            true,
            strategy,
            logPath,
            collectPreviousLogs,
            namespacedResources,
            clusterResources,
            contextMappings
        );
    }
}