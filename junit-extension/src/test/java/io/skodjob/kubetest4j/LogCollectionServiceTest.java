/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Namespace;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    private NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> namespaceOperation;

    @Mock
    private FilterWatchListDeletable<Namespace, NamespaceList, Resource<Namespace>> labelSelector;

    private LogCollectionService manager;

    @BeforeEach
    void setUp() {
        manager = new LogCollectionService(contextStoreHelper, configurationService, contextProvider);
        lenient().when(extensionContext.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        lenient().when(extensionContext.getDisplayName()).thenReturn("testMethodA()");
        lenient().when(extensionContext.getTestClass()).thenReturn(Optional.of(LogCollectionServiceTest.class));
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
        @DisplayName("Should build fresh log collector and collect from primary context")
        void shouldCollectLogsFromPrimaryContextSuccessfully() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then - fresh LogCollector built from resource manager
            verify(contextProvider, atLeastOnce()).getResourceManager(extensionContext);
            verify(contextProvider).getKubeContextManagers(extensionContext);
        }

        @Test
        @DisplayName("Should collect logs from multiple contexts")
        void shouldCollectLogsFromMultipleContexts() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);

            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);

            // Mock additional kubeContext
            KubeResourceManager stagingManager = mock(KubeResourceManager.class);
            KubeClient stagingKubeClient = mock(KubeClient.class);
            KubernetesClient stagingK8sClient = mock(KubernetesClient.class);
            NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> stagingNamespaceOp =
                mock(NonNamespaceOperation.class);
            FilterWatchListDeletable<Namespace, NamespaceList, Resource<Namespace>> stagingLabelSelector =
                mock(FilterWatchListDeletable.class);

            lenient().when(stagingManager.kubeClient()).thenReturn(stagingKubeClient);
            lenient().when(stagingKubeClient.getClient()).thenReturn(stagingK8sClient);
            lenient().when(stagingK8sClient.namespaces()).thenReturn(stagingNamespaceOp);
            lenient().when(stagingNamespaceOp.withLabelSelector(any(LabelSelector.class)))
                .thenReturn(stagingLabelSelector);

            Map<String, KubeResourceManager> contextManagers = Map.of("staging", stagingManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(contextManagers);

            NamespaceList primaryNamespaceList = mock(NamespaceList.class);
            NamespaceList stagingNamespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(primaryNamespaceList);
            lenient().when(stagingLabelSelector.list()).thenReturn(stagingNamespaceList);
            when(primaryNamespaceList.getItems()).thenReturn(List.of());
            lenient().when(stagingNamespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            verify(contextProvider).getKubeContextManagers(extensionContext);
        }

        @Test
        @DisplayName("Should handle exception during log collection gracefully")
        void shouldHandleExceptionDuringLogCollectionGracefully() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextProvider.getResourceManager(extensionContext))
                .thenThrow(new RuntimeException("Test exception"));

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            verify(contextProvider, atLeastOnce()).getResourceManager(extensionContext);
        }

        @Test
        @DisplayName("Should query labeled namespaces from kubeContext")
        void shouldCollectLabeledNamespacesFromContext() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            verify(contextProvider, atLeastOnce()).getResourceManager(extensionContext);
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
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            // Mock exception when querying namespaces
            when(labelSelector.list()).thenThrow(new RuntimeException("Kubernetes API error"));

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            verify(contextProvider, atLeastOnce()).getResourceManager(extensionContext);
        }

        @Test
        @DisplayName("Should handle empty namespace list gracefully")
        void shouldHandleEmptyNamespaceListGracefully() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            // Mock empty namespace list
            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then
            verify(contextProvider, atLeastOnce()).getResourceManager(extensionContext);
        }
    }

    @Nested
    @DisplayName("Per-Method Log Path Tests")
    class PerMethodLogPathTests {

        @Test
        @DisplayName("Should create fresh log collector using method context display name")
        void shouldCreateFreshLogCollectorUsingMethodContextDisplayName() {
            // Given
            when(extensionContext.getDisplayName()).thenReturn("testMethodA()");

            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then - collectLogs builds a fresh LogCollector from the current context,
            // so it uses the method display name for path computation.
            // Verify the resource manager was queried to build the new LogCollector.
            verify(contextProvider, atLeastOnce()).getResourceManager(extensionContext);
            verify(resourceManager, atLeastOnce()).kubeClient();
            verify(resourceManager, atLeastOnce()).kubeCmdClient();
        }

        @Test
        @DisplayName("Should use class display name for class-level context")
        void shouldUseClassDisplayNameForClassLevelContext() {
            // Given
            when(extensionContext.getDisplayName()).thenReturn("MyTestClass");

            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of());

            // When
            manager.collectLogs(extensionContext, "test-suffix");

            // Then - a fresh LogCollector is built each time, using context.getDisplayName()
            verify(contextProvider, atLeastOnce()).getResourceManager(extensionContext);
        }

        @Test
        @DisplayName("Should create separate log collectors for each collectLogs call")
        void shouldCreateSeparateLogCollectorsForEachCollectLogsCall() {
            // Given
            TestConfig testConfig = createTestConfig("/logs", LogCollectionStrategy.ON_FAILURE,
                List.of("pods"), List.of(), false);
            when(configurationService.getTestConfig(extensionContext)).thenReturn(testConfig);
            when(contextProvider.getResourceManager(extensionContext)).thenReturn(resourceManager);
            when(contextProvider.getKubeContextManagers(extensionContext)).thenReturn(Map.of());

            NamespaceList namespaceList = mock(NamespaceList.class);
            when(labelSelector.list()).thenReturn(namespaceList);
            when(namespaceList.getItems()).thenReturn(List.of());

            // When - first call with method A
            when(extensionContext.getDisplayName()).thenReturn("testMethodA()");
            manager.collectLogs(extensionContext, "first-suffix");

            // When - second call with method B
            when(extensionContext.getDisplayName()).thenReturn("testMethodB()");
            manager.collectLogs(extensionContext, "second-suffix");

            // Then - resourceManager queried for each fresh LogCollector build
            // kubeClient() is called both in createLogBuilder and collectNamespacesWithLabel
            verify(resourceManager, atLeast(2)).kubeClient();
        }
    }

    // Helper method to create TestConfig instances for testing
    private TestConfig createTestConfig(String logPath, LogCollectionStrategy strategy,
                                        List<String> namespacedResources, List<String> clusterResources,
                                        boolean collectPreviousLogs) {
        return new TestConfig(
            CleanupStrategy.AUTOMATIC,
            false,
            "",
            "#",
            76,
            true,
            strategy,
            logPath,
            collectPreviousLogs,
            namespacedResources,
            clusterResources
        );
    }
}
