/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ConfigurationManager.
 * Tests configuration creation and management logic.
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private ContextStoreHelper contextStoreHelper;

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private Store store;

    // Use a real test class instead of mocking Class<?>
    private Class<?> testClass = ConfigurationServiceTest.class;

    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = new ConfigurationService(contextStoreHelper);
        lenient().when(extensionContext.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        lenient().when(extensionContext.getRequiredTestClass()).thenReturn((Class) testClass);
    }

    @Nested
    @DisplayName("Annotation Retrieval Tests")
    class AnnotationRetrievalTests {

        @Test
        @DisplayName("Should get KubernetesTest annotation from test class")
        void shouldGetKubernetesTestAnnotationFromTestClass() {
            // When - ConfigurationManagerTest doesn't have @KubernetesTest annotation
            KubernetesTest result = configurationService.getKubernetesTestAnnotation(extensionContext);

            // Then - should return null since the real test class has no annotation
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when annotation is not present")
        void shouldReturnNullWhenAnnotationIsNotPresent() {
            // When - using real class without @KubernetesTest annotation
            KubernetesTest result = configurationService.getKubernetesTestAnnotation(extensionContext);

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("TestConfig Creation Tests")
    class TestConfigCreationTests {

        @Test
        @DisplayName("Should create TestConfig with provided namespaces")
        void shouldCreateTestConfigWithProvidedNamespaces() {
            // Given
            KubernetesTest annotation = createMockKubernetesTestAnnotation();
            lenient().when(annotation.namespaces()).thenReturn(new String[]{"ns1", "ns2"});

            // When
            TestConfig config = configurationService.createTestConfig(extensionContext, annotation);

            // Then
            assertEquals(List.of("ns1", "ns2"), config.namespaces());
        }

        @Test
        @DisplayName("Should use default namespace when none provided")
        void shouldUseDefaultNamespaceWhenNoneProvided() {
            // Given
            KubernetesTest annotation = createMockKubernetesTestAnnotation();
            lenient().when(annotation.namespaces()).thenReturn(new String[0]);

            // When
            TestConfig config = configurationService.createTestConfig(extensionContext, annotation);

            // Then
            assertEquals(1, config.namespaces().size());
            assertEquals("default-test", config.namespaces().get(0));
        }

        @Test
        @DisplayName("Should create TestConfig with all annotation properties")
        void shouldCreateTestConfigWithAllAnnotationProperties() {
            // Given
            KubernetesTest annotation = createMockKubernetesTestAnnotation();
            when(annotation.namespaces()).thenReturn(new String[]{"test-ns"});
            when(annotation.cleanup()).thenReturn(CleanupStrategy.MANUAL);
            when(annotation.kubeContext()).thenReturn("test-kubeContext");
            when(annotation.storeYaml()).thenReturn(true);
            when(annotation.yamlStorePath()).thenReturn("/test/path");
            when(annotation.namespaceLabels()).thenReturn(new String[]{"label=value"});
            when(annotation.namespaceAnnotations()).thenReturn(new String[]{"annotation=value"});
            when(annotation.visualSeparatorChar()).thenReturn("=");
            when(annotation.visualSeparatorLength()).thenReturn(100);
            when(annotation.collectLogs()).thenReturn(true);
            when(annotation.logCollectionStrategy()).thenReturn(LogCollectionStrategy.AFTER_EACH);
            when(annotation.logCollectionPath()).thenReturn("/logs");
            when(annotation.collectPreviousLogs()).thenReturn(true);
            when(annotation.collectNamespacedResources()).thenReturn(new String[]{"pods", "services"});
            when(annotation.collectClusterWideResources()).thenReturn(new String[]{"nodes"});
            when(annotation.kubeContextMappings()).thenReturn(new KubernetesTest.KubeContextMapping[0]);

            // When
            TestConfig config = configurationService.createTestConfig(extensionContext, annotation);

            // Then
            assertEquals(List.of("test-ns"), config.namespaces());
            assertEquals(CleanupStrategy.MANUAL, config.cleanup());
            assertEquals("test-kubeContext", config.context());
            assertTrue(config.storeYaml());
            assertEquals("/test/path", config.yamlStorePath());
            assertEquals(List.of("label=value"), config.namespaceLabels());
            assertEquals(List.of("annotation=value"), config.namespaceAnnotations());
            assertEquals("=", config.visualSeparatorChar());
            assertEquals(100, config.visualSeparatorLength());
            assertTrue(config.collectLogs());
            assertEquals(LogCollectionStrategy.AFTER_EACH, config.logCollectionStrategy());
            assertEquals("/logs", config.logCollectionPath());
            assertTrue(config.collectPreviousLogs());
            assertEquals(List.of("pods", "services"), config.collectNamespacedResources());
            assertEquals(List.of("nodes"), config.collectClusterWideResources());
        }

        @Test
        @DisplayName("Should convert kubeContext mappings from annotation")
        void shouldConvertKubeContextMappingsFromAnnotation() {
            // Given
            KubernetesTest annotation = createMockKubernetesTestAnnotation();
            KubernetesTest.KubeContextMapping kubeContextMapping = createMockKubeContextMappingAnnotation();
            when(annotation.kubeContextMappings())
                .thenReturn(new KubernetesTest.KubeContextMapping[]{kubeContextMapping});
            when(annotation.namespaces()).thenReturn(new String[]{"test-ns"});

            when(kubeContextMapping.kubeContext()).thenReturn("staging");
            when(kubeContextMapping.namespaces()).thenReturn(new String[]{"stg-ns1", "stg-ns2"});
            when(kubeContextMapping.cleanup()).thenReturn(CleanupStrategy.AUTOMATIC);
            when(kubeContextMapping.namespaceLabels()).thenReturn(new String[]{"env=staging"});
            when(kubeContextMapping.namespaceAnnotations()).thenReturn(new String[]{"deploy=auto"});

            // When
            TestConfig config = configurationService.createTestConfig(extensionContext, annotation);

            // Then
            assertEquals(1, config.kubeContextMappings().size());
            TestConfig.KubeContextMappingConfig mapping = config.kubeContextMappings().get(0);
            assertEquals("staging", mapping.kubeContext());
            assertEquals(List.of("stg-ns1", "stg-ns2"), mapping.namespaces());
            assertEquals(CleanupStrategy.AUTOMATIC, mapping.cleanup());
            assertEquals(List.of("env=staging"), mapping.namespaceLabels());
            assertEquals(List.of("deploy=auto"), mapping.namespaceAnnotations());
        }
    }

    @Nested
    @DisplayName("Create and Store Tests")
    class CreateAndStoreTests {

        @Test
        @DisplayName("Should throw exception when annotation is missing")
        void shouldThrowExceptionWhenAnnotationIsMissing() {
            // Given - ConfigurationManagerTest doesn't have @KubernetesTest annotation

            // When/Then - should throw exception since real class has no annotation
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                configurationService.createAndStoreTestConfig(extensionContext));

            assertEquals("@KubernetesTest annotation not found on test class", exception.getMessage());
        }

        @Test
        @DisplayName("Should create TestConfig from annotation when present")
        void shouldCreateTestConfigFromAnnotationWhenPresent() {
            // Given
            KubernetesTest annotation = createMockKubernetesTestAnnotation();
            when(annotation.namespaces()).thenReturn(new String[]{"test-ns"});

            // When
            TestConfig result = configurationService.createTestConfig(extensionContext, annotation);

            // Then
            assertNotNull(result);
            assertEquals(List.of("test-ns"), result.namespaces());
        }
    }


    @Nested
    @DisplayName("Get TestConfig Tests")
    class GetTestConfigTests {

        @Test
        @DisplayName("Should get TestConfig from extension context store helper")
        void shouldGetTestConfigFromExtensionContextStoreHelper() {
            // Given
            TestConfig expectedConfig = new TestConfig(
                List.of("test-ns"), CleanupStrategy.AUTOMATIC, "", false, "",
                List.of(), List.of(), "#", 76, false, LogCollectionStrategy.ON_FAILURE,
                "", false, List.of("pods"), List.of(), List.of()
            );
            when(contextStoreHelper.getTestConfig(extensionContext)).thenReturn(expectedConfig);

            // When
            TestConfig result = configurationService.getTestConfig(extensionContext);

            // Then
            assertEquals(expectedConfig, result);
            verify(contextStoreHelper).getTestConfig(extensionContext);
        }

        @Test
        @DisplayName("Should return null when TestConfig not found")
        void shouldReturnNullWhenTestConfigNotFound() {
            // Given
            when(contextStoreHelper.getTestConfig(extensionContext)).thenReturn(null);

            // When
            TestConfig result = configurationService.getTestConfig(extensionContext);

            // Then
            assertNull(result);
        }
    }

    // Helper methods to create mock annotations
    private KubernetesTest createMockKubernetesTestAnnotation() {
        KubernetesTest annotation = mock(KubernetesTest.class);

        // Set up default return values for all annotation methods
        when(annotation.namespaces()).thenReturn(new String[0]);
        when(annotation.cleanup()).thenReturn(CleanupStrategy.AUTOMATIC);
        when(annotation.kubeContext()).thenReturn("");
        when(annotation.storeYaml()).thenReturn(false);
        when(annotation.yamlStorePath()).thenReturn("");
        when(annotation.namespaceLabels()).thenReturn(new String[0]);
        when(annotation.namespaceAnnotations()).thenReturn(new String[0]);
        when(annotation.visualSeparatorChar()).thenReturn("#");
        when(annotation.visualSeparatorLength()).thenReturn(76);
        when(annotation.collectLogs()).thenReturn(false);
        when(annotation.logCollectionStrategy()).thenReturn(LogCollectionStrategy.ON_FAILURE);
        when(annotation.logCollectionPath()).thenReturn("");
        when(annotation.collectPreviousLogs()).thenReturn(false);
        when(annotation.collectNamespacedResources()).thenReturn(new String[]{"pods"});
        when(annotation.collectClusterWideResources()).thenReturn(new String[0]);
        when(annotation.kubeContextMappings()).thenReturn(new KubernetesTest.KubeContextMapping[0]);

        return annotation;
    }

    private KubernetesTest.KubeContextMapping createMockKubeContextMappingAnnotation() {
        KubernetesTest.KubeContextMapping kubeContextMapping = mock(KubernetesTest.KubeContextMapping.class);

        // Set up default return values
        when(kubeContextMapping.kubeContext()).thenReturn("");
        when(kubeContextMapping.namespaces()).thenReturn(new String[0]);
        when(kubeContextMapping.cleanup()).thenReturn(CleanupStrategy.AUTOMATIC);
        when(kubeContextMapping.namespaceLabels()).thenReturn(new String[0]);
        when(kubeContextMapping.namespaceAnnotations()).thenReturn(new String[0]);

        return kubeContextMapping;
    }
}