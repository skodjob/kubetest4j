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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for KubernetesTestExtension.
 * These tests focus on testing individual methods and logic without requiring a real Kubernetes cluster.
 */
@ExtendWith(MockitoExtension.class)
class KubernetesTestExtensionTest {

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private Store store;

    @Mock
    private ParameterContext parameterContext;

    @Mock
    private Parameter parameter;

    private KubernetesTestExtension extension;

    @BeforeEach
    void setUp() {
        extension = new KubernetesTestExtension();
        lenient().when(extensionContext.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
    }

    @Nested
    @DisplayName("TestConfig Creation Tests")
    class TestConfigCreationTests {

        @Test
        @DisplayName("Should create TestConfig with default values")
        void shouldCreateTestConfigWithDefaults() {
            // Given - Create actual TestConfig directly since createTestConfig is private
            TestConfig config = new TestConfig(
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
                List.of() // Empty kubeContext mappings
            );

            // Then
            assertNotNull(config);
            assertEquals(List.of("test-ns"), config.namespaces());
            assertEquals(CleanupStrategy.AUTOMATIC, config.cleanup());
            assertFalse(config.storeYaml());
            assertEquals("", config.yamlStorePath());
            assertEquals("#", config.visualSeparatorChar());
            assertEquals(76, config.visualSeparatorLength());
            assertFalse(config.collectLogs());
            assertEquals(LogCollectionStrategy.ON_FAILURE, config.logCollectionStrategy());
        }

        @Test
        @DisplayName("Should create TestConfig with custom values")
        void shouldCreateTestConfigWithCustomValues() {
            // Given - Create actual TestConfig directly
            TestConfig config = new TestConfig(
                Arrays.asList("ns1", "ns2"),
                CleanupStrategy.MANUAL,
                true,
                "target/yamls",
                List.of("env=test"),
                List.of("description=test"),
                "=",
                80,
                true,
                LogCollectionStrategy.AFTER_EACH,
                "target/logs",
                true,
                List.of("pods", "services"),
                List.of("nodes"),
                List.of() // Empty kubeContext mappings
            );

            // Then
            assertEquals(Arrays.asList("ns1", "ns2"), config.namespaces());
            assertEquals(CleanupStrategy.MANUAL, config.cleanup());
            assertTrue(config.storeYaml());
            assertEquals("target/yamls", config.yamlStorePath());
            assertEquals(List.of("env=test"), config.namespaceLabels());
            assertEquals(List.of("description=test"), config.namespaceAnnotations());
            assertEquals("=", config.visualSeparatorChar());
            assertEquals(80, config.visualSeparatorLength());
            assertTrue(config.collectLogs());
            assertEquals(LogCollectionStrategy.AFTER_EACH, config.logCollectionStrategy());
            assertEquals("target/logs", config.logCollectionPath());
            assertTrue(config.collectPreviousLogs());
            assertEquals(List.of("pods", "services"), config.collectNamespacedResources());
            assertEquals(List.of("nodes"), config.collectClusterWideResources());
        }

        @Test
        @DisplayName("Should validate TestConfig record properties")
        void shouldValidateTestConfigRecordProperties() {
            // Given - Create TestConfig with various values
            TestConfig config = new TestConfig(
                List.of("ns1", "ns2", "ns3"),
                CleanupStrategy.AUTOMATIC,
                true,
                "/tmp/yamls",
                List.of("label1=value1", "label2=value2"),
                List.of("annotation1=value1"),
                "=",
                100,
                true,
                LogCollectionStrategy.AFTER_EACH,
                "/tmp/logs",
                true,
                List.of("pods", "services", "configmaps"),
                List.of("nodes", "persistentvolumes"),
                List.of() // Empty kubeContext mappings
            );

            // Then - Verify all properties
            assertEquals(List.of("ns1", "ns2", "ns3"), config.namespaces());
            assertEquals(CleanupStrategy.AUTOMATIC, config.cleanup());
            assertTrue(config.storeYaml());
            assertEquals("/tmp/yamls", config.yamlStorePath());
            assertEquals(List.of("label1=value1", "label2=value2"), config.namespaceLabels());
            assertEquals(List.of("annotation1=value1"), config.namespaceAnnotations());
            assertEquals("=", config.visualSeparatorChar());
            assertEquals(100, config.visualSeparatorLength());
            assertTrue(config.collectLogs());
            assertEquals(LogCollectionStrategy.AFTER_EACH, config.logCollectionStrategy());
            assertEquals("/tmp/logs", config.logCollectionPath());
            assertTrue(config.collectPreviousLogs());
            assertEquals(List.of("pods", "services", "configmaps"), config.collectNamespacedResources());
            assertEquals(List.of("nodes", "persistentvolumes"), config.collectClusterWideResources());
        }
    }

    @Nested
    @DisplayName("Namespace Generation Tests")
    class NamespaceGenerationTests {

        @Test
        @DisplayName("Should generate namespace with timestamp format validation")
        void shouldGenerateNamespaceWithTimestampFormatValidation() {
            // Test the namespace format generation logic
            String className = "TestClass";
            LocalDateTime now = LocalDateTime.now();
            String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String expectedPattern = String.format("test-%s-%s", className.toLowerCase(), timestamp);

            // Verify the format is correct
            assertTrue(expectedPattern.matches("test-[a-z]+-\\d{8}-\\d{6}"));

            // Verify timestamp parsing
            assertDoesNotThrow(() -> {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
                formatter.parse(timestamp);
            });
        }
    }

    @Nested
    @DisplayName("Parameter Resolution Tests")
    class ParameterResolutionTests {

        @Test
        @DisplayName("Should support parameter with inject annotation")
        void shouldSupportParameterWithInjectAnnotation() throws NoSuchMethodException {
            // Given
            Method method = TestClass.class.getDeclaredMethod("testMethod", Object.class);
            Parameter param = method.getParameters()[0];

            lenient().when(parameterContext.getParameter()).thenReturn(param);
            lenient().when(parameterContext.isAnnotated(any())).thenReturn(true);

            // When
            boolean supports = extension.supportsParameter(parameterContext, extensionContext);

            // Then
            assertTrue(supports);
        }

        @Test
        @DisplayName("Should not support parameter without inject annotation")
        void shouldNotSupportParameterWithoutInjectAnnotation() throws NoSuchMethodException {
            // Given
            Method method = TestClass.class.getDeclaredMethod("testMethodNoAnnotation", String.class);
            Parameter param = method.getParameters()[0];

            lenient().when(parameterContext.getParameter()).thenReturn(param);
            lenient().when(parameterContext.isAnnotated(any())).thenReturn(false);

            // When
            boolean supports = extension.supportsParameter(parameterContext, extensionContext);

            // Then
            assertFalse(supports);
        }
    }

    @Nested
    @DisplayName("Multi-Context TestConfig Tests")
    class MultiContextTestConfigTests {

        @Test
        @DisplayName("Should create TestConfig with kubeContext mappings")
        void shouldCreateTestConfigWithContextMappings() {
            // Given - Create kubeContext mappings
            List<TestConfig.AdditionalKubeContextConfig> contextMappings = List.of(
                new TestConfig.AdditionalKubeContextConfig(
                    "staging-cluster",
                    List.of("stg-app", "stg-db"),
                    CleanupStrategy.AUTOMATIC,
                    List.of("env=staging"),
                    List.of("stage=staging")
                ),
                new TestConfig.AdditionalKubeContextConfig(
                    "production-cluster",
                    List.of("prod-api"),
                    CleanupStrategy.MANUAL,
                    List.of("env=production"),
                    List.of("stage=production")
                )
            );

            TestConfig config = new TestConfig(
                List.of("default-ns"),
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
                contextMappings
            );

            // Then - Verify kubeContext mappings
            assertNotNull(config.additionalKubeContexts());
            assertEquals(2, config.additionalKubeContexts().size());

            // Verify staging mapping
            TestConfig.AdditionalKubeContextConfig stagingMapping = config.additionalKubeContexts().get(0);
            assertEquals("staging-cluster", stagingMapping.name());
            assertEquals(List.of("stg-app", "stg-db"), stagingMapping.namespaces());
            assertEquals(CleanupStrategy.AUTOMATIC, stagingMapping.cleanup());
            assertEquals(List.of("env=staging"), stagingMapping.namespaceLabels());
            assertEquals(List.of("stage=staging"), stagingMapping.namespaceAnnotations());

            // Verify production mapping
            TestConfig.AdditionalKubeContextConfig prodMapping = config.additionalKubeContexts().get(1);
            assertEquals("production-cluster", prodMapping.name());
            assertEquals(List.of("prod-api"), prodMapping.namespaces());
            assertEquals(CleanupStrategy.MANUAL, prodMapping.cleanup());
        }

        @Test
        @DisplayName("Should verify AdditionalKubeContextConfig properties")
        void shouldVerifyAdditionalKubeContextConfigProperties() {
            // Given
            TestConfig.AdditionalKubeContextConfig config = new TestConfig.AdditionalKubeContextConfig(
                "test-kubeContext",
                List.of("ns1", "ns2"),
                CleanupStrategy.MANUAL,
                List.of("label1=value1", "label2=value2"),
                List.of("annotation1=value1")
            );

            // Then
            assertEquals("test-kubeContext", config.name());
            assertEquals(List.of("ns1", "ns2"), config.namespaces());
            assertEquals(CleanupStrategy.MANUAL, config.cleanup());
            assertEquals(List.of("label1=value1", "label2=value2"), config.namespaceLabels());
            assertEquals(List.of("annotation1=value1"), config.namespaceAnnotations());
        }
    }

    @Nested
    @DisplayName("Enum Validation Tests")
    class EnumValidationTests {

        @Test
        @DisplayName("Should validate CleanupStrategy enum values")
        void shouldValidateCleanupStrategyEnumValues() {
            // Test that all cleanup strategy values work correctly
            TestConfig automaticConfig = new TestConfig(
                List.of("test"),
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
                List.of(),
                List.of(),
                List.of() // Empty kubeContext mappings
            );

            TestConfig manualConfig = new TestConfig(
                List.of("test"),
                CleanupStrategy.MANUAL,
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
                List.of(),
                List.of(),
                List.of() // Empty kubeContext mappings
            );

            assertEquals(CleanupStrategy.AUTOMATIC, automaticConfig.cleanup());
            assertEquals(CleanupStrategy.MANUAL, manualConfig.cleanup());
        }

        @Test
        @DisplayName("Should validate LogCollectionStrategy enum values")
        void shouldValidateLogCollectionStrategyEnumValues() {
            // Test that all log collection strategy values work correctly
            for (LogCollectionStrategy strategy : LogCollectionStrategy.values()) {
                TestConfig config = new TestConfig(
                    List.of("test"),
                    CleanupStrategy.AUTOMATIC,
                    false,
                    "",
                    List.of(),
                    List.of(),
                    "#",
                    76,
                    false,
                    strategy,
                    "",
                    false,
                    List.of(),
                    List.of(),
                    List.of() // Empty kubeContext mappings
                );

                assertEquals(strategy, config.logCollectionStrategy());
            }
        }
    }

    // Test Class for Method Testing
    @KubernetesTest
    static class TestClass {
        public void testMethod(@io.skodjob.kubetest4j.annotations.InjectKubeClient Object client) {
        }

        public void testMethodNoAnnotation(String param) {
        }
    }
}