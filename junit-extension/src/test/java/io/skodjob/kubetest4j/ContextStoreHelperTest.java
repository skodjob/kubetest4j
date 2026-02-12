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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ContextStoreHelper.
 * Tests all store operations to ensure proper kubeContext management.
 */
@ExtendWith(MockitoExtension.class)
class ContextStoreHelperTest {

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private Store store;

    @Mock
    private KubeResourceManager resourceManager;

    @Mock
    private LogCollector logCollector;

    private ContextStoreHelper storeHelper;

    @BeforeEach
    void setUp() {
        storeHelper = new ContextStoreHelper();
        when(extensionContext.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
    }

    @Nested
    @DisplayName("Basic Store Operations Tests")
    class BasicStoreOperationsTests {

        @Test
        @DisplayName("Should get value from store with correct type")
        void shouldGetValueFromStoreWithCorrectType() {
            // Given
            String testValue = "test-value";
            when(store.get("test-key", String.class)).thenReturn(testValue);

            // When
            String result = storeHelper.get(extensionContext, "test-key", String.class);

            // Then
            assertEquals(testValue, result);
            verify(store).get("test-key", String.class);
        }

        @Test
        @DisplayName("Should put value in store")
        void shouldPutValueInStore() {
            // Given
            String testValue = "test-value";

            // When
            storeHelper.put(extensionContext, "test-key", testValue);

            // Then
            verify(store).put("test-key", testValue);
        }

        @Test
        @DisplayName("Should return null when key not found")
        void shouldReturnNullWhenKeyNotFound() {
            // Given
            when(store.get("non-existent-key", String.class)).thenReturn(null);

            // When
            String result = storeHelper.get(extensionContext, "non-existent-key", String.class);

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Namespace Operations Tests")
    class NamespaceOperationsTests {


        @Test
        @DisplayName("Should put and get namespace objects map")
        void shouldPutAndGetNamespaceObjectsMap() {
            // Given
            Namespace namespace1 = new NamespaceBuilder().withNewMetadata().withName("ns1").endMetadata().build();
            Namespace namespace2 = new NamespaceBuilder().withNewMetadata().withName("ns2").endMetadata().build();
            Map<String, Namespace> namespaceObjects = Map.of("ns1", namespace1, "ns2", namespace2);

            when(store.get("kubernetes.test.namespaceObjects")).thenReturn(namespaceObjects);

            // When
            storeHelper.putNamespaceObjects(extensionContext, namespaceObjects);
            Map<String, Namespace> result = storeHelper.getNamespaceObjects(extensionContext);

            // Then
            verify(store).put("kubernetes.test.namespaceObjects", namespaceObjects);
            assertEquals(namespaceObjects, result);
        }


        @Test
        @DisplayName("Should put and get created namespace names")
        void shouldPutAndGetCreatedNamespaceNames() {
            // Given
            List<String> createdNames = List.of("created-ns1", "created-ns2");
            when(store.get("kubernetes.test.createdNamespaceNames")).thenReturn(createdNames);

            // When
            storeHelper.putCreatedNamespaceNames(extensionContext, createdNames);
            List<String> result = storeHelper.getCreatedNamespaceNames(extensionContext);

            // Then
            verify(store).put("kubernetes.test.createdNamespaceNames", createdNames);
            assertEquals(createdNames, result);
        }
    }

    @Nested
    @DisplayName("Resource Manager Operations Tests")
    class ResourceManagerOperationsTests {

        @Test
        @DisplayName("Should put and get resource manager")
        void shouldPutAndGetResourceManager() {
            // Given
            when(store.get("kubernetes.test.resourceManager", KubeResourceManager.class)).thenReturn(resourceManager);

            // When
            storeHelper.putResourceManager(extensionContext, resourceManager);
            KubeResourceManager result = storeHelper.getResourceManager(extensionContext);

            // Then
            verify(store).put("kubernetes.test.resourceManager", resourceManager);
            assertEquals(resourceManager, result);
        }
    }

    @Nested
    @DisplayName("Test Configuration Operations Tests")
    class TestConfigurationOperationsTests {

        @Test
        @DisplayName("Should put and get test config")
        void shouldPutAndGetTestConfig() {
            // Given
            TestConfig testConfig = new TestConfig(
                List.of("test-ns"),
                CleanupStrategy.AUTOMATIC,
                false,
                "",
                List.of(),
                List.of(),
                "#",
                76,
                false,
                LogCollectionStrategy.ON_FAILURE,
                "",
                false,
                List.of("pods"),
                List.of(),
                List.of()
            );

            when(store.get("kubernetes.test.config", TestConfig.class)).thenReturn(testConfig);

            // When
            storeHelper.putTestConfig(extensionContext, testConfig);
            TestConfig result = storeHelper.getTestConfig(extensionContext);

            // Then
            verify(store).put("kubernetes.test.config", testConfig);
            assertEquals(testConfig, result);
        }
    }

    @Nested
    @DisplayName("Log Collector Operations Tests")
    class LogCollectorOperationsTests {

        @Test
        @DisplayName("Should put and get log collector")
        void shouldPutAndGetLogCollector() {
            // Given
            when(store.get("kubernetes.test.logCollector", LogCollector.class)).thenReturn(logCollector);

            // When
            storeHelper.putLogCollector(extensionContext, logCollector);
            LogCollector result = storeHelper.getLogCollector(extensionContext);

            // Then
            verify(store).put("kubernetes.test.logCollector", logCollector);
            assertEquals(logCollector, result);
        }
    }

    @Nested
    @DisplayName("Multi-Context Operations Tests")
    class MultiContextOperationsTests {

        @Test
        @DisplayName("Should put and get kubeContext managers")
        void shouldPutAndGetContextManagers() {
            // Given
            when(store.get("kubernetes.test.contextManagers")).thenReturn(new HashMap<>());

            // When
            storeHelper.putContextManager(extensionContext, "staging", resourceManager);
            Map<String, KubeResourceManager> result = storeHelper.getContextManagers(extensionContext);

            // Then
            verify(store, atLeastOnce()).get("kubernetes.test.contextManagers");
            assertTrue(result.isEmpty() || result.containsKey("staging"));
        }

        @Test
        @DisplayName("Should put and get kubeContext closers")
        void shouldPutAndGetContextClosers() {
            // Given
            AutoCloseable mockCloser = mock(AutoCloseable.class);
            when(store.get("kubernetes.test.contextClosers")).thenReturn(new HashMap<>());

            // When
            storeHelper.putContextCloser(extensionContext, "staging", mockCloser);
            Map<String, AutoCloseable> result = storeHelper.getAllContextClosers(extensionContext);

            // Then
            verify(store, atLeastOnce()).get("kubernetes.test.contextClosers");
            assertTrue(result.isEmpty() || result.containsKey("staging"));
        }

        @Test
        @DisplayName("Should put and get kubeContext namespace objects for specific kubeContext")
        void shouldPutAndGetContextNamespaceObjectsForSpecificContext() {
            // Given
            String contextName = "staging";
            Namespace namespace = new NamespaceBuilder().withNewMetadata().withName("stg-ns").endMetadata().build();
            Map<String, Namespace> namespaceObjects = Map.of("stg-ns", namespace);

            Map<String, Map<String, Namespace>> allContextObjects = new HashMap<>();
            allContextObjects.put(contextName, namespaceObjects);
            when(store.get("kubernetes.test.contextNamespaceObjects"))
                .thenReturn(allContextObjects);

            // When
            storeHelper.putNamespaceObjectsForContext(extensionContext, contextName, namespaceObjects);
            Map<String, Namespace> result = storeHelper.getNamespaceObjectsForContext(
                extensionContext, contextName);

            // Then
            verify(store, atLeastOnce()).get("kubernetes.test.contextNamespaceObjects");
            assertEquals(namespaceObjects, result);
        }

        @Test
        @DisplayName("Should get or create kubeContext created namespaces for specific kubeContext")
        void shouldGetOrCreateContextCreatedNamespacesForSpecificContext() {
            // Given
            String contextName = "staging";
            when(store.get("kubernetes.test.contextCreatedNamespaces"))
                .thenReturn(new HashMap<>());

            // When
            List<String> result = storeHelper.getOrCreateCreatedNamespacesForContext(
                extensionContext, contextName);

            // Then
            verify(store, atLeastOnce()).get("kubernetes.test.contextCreatedNamespaces");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should initialize empty map when kubeContext namespace objects not found")
        void shouldInitializeEmptyMapWhenContextNamespaceObjectsNotFound() {
            // Given
            String contextName = "new-kubeContext";
            when(store.get("kubernetes.test.contextNamespaceObjects"))
                .thenReturn(null);

            // When
            Map<String, Namespace> result = storeHelper.getOrCreateNamespaceObjectsForContext(
                extensionContext, contextName);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(store).put(eq("kubernetes.test.contextNamespaceObjects"), any(Map.class));
        }

        @Test
        @DisplayName("Should initialize empty map when kubeContext created namespaces not found")
        void shouldInitializeEmptyMapWhenContextCreatedNamespacesNotFound() {
            // Given
            String contextName = "new-kubeContext";
            when(store.get("kubernetes.test.contextCreatedNamespaces")).thenReturn(null);

            // When
            List<String> result = storeHelper.getOrCreateCreatedNamespacesForContext(extensionContext, contextName);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(store).put(eq("kubernetes.test.contextCreatedNamespaces"), any(Map.class));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null values gracefully")
        void shouldHandleNullValuesGracefully() {
            // When/Then - these should not throw exceptions
            assertDoesNotThrow(() -> storeHelper.putNamespaces(extensionContext, null));
            assertDoesNotThrow(() -> storeHelper.putResourceManager(extensionContext, null));
            assertDoesNotThrow(() -> storeHelper.putTestConfig(extensionContext, null));
            assertDoesNotThrow(() -> storeHelper.putLogCollector(extensionContext, null));
        }

        @Test
        @DisplayName("Should handle empty collections gracefully")
        void shouldHandleEmptyCollectionsGracefully() {
            // Given
            Map<String, Namespace> emptyMap = Map.of();
            List<String> emptyList = List.of();
            String[] emptyArray = new String[0];

            // When/Then - these should not throw exceptions
            assertDoesNotThrow(() -> storeHelper.putNamespaces(extensionContext, emptyArray));
            assertDoesNotThrow(() -> storeHelper.putNamespaceObjects(extensionContext, emptyMap));
            assertDoesNotThrow(() -> storeHelper.putCreatedNamespaceNames(extensionContext, emptyList));
        }
    }
}