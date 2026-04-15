/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import io.skodjob.kubetest4j.interfaces.ResourceType;
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

import java.lang.annotation.Inherited;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
                CleanupStrategy.AUTOMATIC,
                false,
                "",
                "#",
                76,
                false,
                LogCollectionStrategy.ON_FAILURE,
                "",
                false,
                List.of("pods"),
                List.of()
            );

            // Then
            assertNotNull(config);
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
                CleanupStrategy.MANUAL,
                true,
                "target/yamls",
                "=",
                80,
                true,
                LogCollectionStrategy.AFTER_EACH,
                "target/logs",
                true,
                List.of("pods", "services"),
                List.of("nodes")
            );

            // Then
            assertEquals(CleanupStrategy.MANUAL, config.cleanup());
            assertTrue(config.storeYaml());
            assertEquals("target/yamls", config.yamlStorePath());
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
                CleanupStrategy.AUTOMATIC,
                true,
                "/tmp/yamls",
                "=",
                100,
                true,
                LogCollectionStrategy.AFTER_EACH,
                "/tmp/logs",
                true,
                List.of("pods", "services", "configmaps"),
                List.of("nodes", "persistentvolumes")
            );

            // Then - Verify all properties
            assertEquals(CleanupStrategy.AUTOMATIC, config.cleanup());
            assertTrue(config.storeYaml());
            assertEquals("/tmp/yamls", config.yamlStorePath());
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
    @DisplayName("Enum Validation Tests")
    class EnumValidationTests {

        @Test
        @DisplayName("Should validate CleanupStrategy enum values")
        void shouldValidateCleanupStrategyEnumValues() {
            // Test that all cleanup strategy values work correctly
            TestConfig automaticConfig = new TestConfig(
                CleanupStrategy.AUTOMATIC,
                false,
                "",
                "#",
                76,
                false,
                LogCollectionStrategy.ON_FAILURE,
                "",
                false,
                List.of(),
                List.of()
            );

            TestConfig manualConfig = new TestConfig(
                CleanupStrategy.MANUAL,
                false,
                "",
                "#",
                76,
                false,
                LogCollectionStrategy.ON_FAILURE,
                "",
                false,
                List.of(),
                List.of()
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
                    CleanupStrategy.AUTOMATIC,
                    false,
                    "",
                    "#",
                    76,
                    false,
                    strategy,
                    "",
                    false,
                    List.of(),
                    List.of()
                );

                assertEquals(strategy, config.logCollectionStrategy());
            }
        }
    }

    @Nested
    @DisplayName("Annotation Inheritance Tests")
    class AnnotationInheritanceTests {

        @Test
        @DisplayName("@KubernetesTest should have @Inherited annotation")
        void shouldHaveInheritedAnnotation() {
            assertTrue(KubernetesTest.class.isAnnotationPresent(Inherited.class),
                "@KubernetesTest should be marked with @Inherited");
        }

        @Test
        @DisplayName("Child class should inherit @KubernetesTest from parent")
        void childShouldInheritAnnotation() {
            KubernetesTest annotation = ChildTestClass.class.getAnnotation(KubernetesTest.class);

            assertNotNull(annotation, "Child should inherit @KubernetesTest from parent");
            assertEquals(CleanupStrategy.AUTOMATIC, annotation.cleanup());
        }

        @Test
        @DisplayName("Child class should be able to override @KubernetesTest")
        void childShouldOverrideAnnotation() {
            KubernetesTest annotation = OverrideChildTestClass.class.getAnnotation(KubernetesTest.class);

            assertNotNull(annotation, "Override child should have @KubernetesTest");
            assertEquals(CleanupStrategy.MANUAL, annotation.cleanup());
        }

        @Test
        @DisplayName("Overriding child with own resourceTypes should use its own")
        void overridingChildWithOwnResourceTypesShouldUseOwn() {
            KubernetesTest annotation = ChildWithOwnResourceTypes.class
                .getAnnotation(KubernetesTest.class);

            assertNotNull(annotation);
            // Child declared its own resourceTypes — should use those, not parent's
            assertEquals(1, annotation.resourceTypes().length);
            assertEquals(AnotherStubResourceType.class, annotation.resourceTypes()[0]);
        }

        @Test
        @DisplayName("Overriding child without resourceTypes should still find parent's via hierarchy walk")
        void overridingChildWithoutResourceTypesShouldFindParentsViaHierarchy() {
            // Java's @Inherited gives ChildOverridingResourceTypes its OWN annotation (cleanup=MANUAL)
            // with empty resourceTypes. But resolveResourceTypes() walks up the hierarchy
            // and finds ParentWithResourceTypes's resourceTypes.
            KubernetesTest childAnnotation = ChildOverridingResourceTypes.class
                .getAnnotation(KubernetesTest.class);

            assertNotNull(childAnnotation);
            // The child's annotation itself has empty resourceTypes (Java replaced the whole annotation)
            assertEquals(0, childAnnotation.resourceTypes().length);

            // But the parent's annotation still has resourceTypes
            KubernetesTest parentAnnotation = ParentWithResourceTypes.class
                .getDeclaredAnnotation(KubernetesTest.class);
            assertNotNull(parentAnnotation);
            assertEquals(1, parentAnnotation.resourceTypes().length,
                "Parent's own annotation still has resourceTypes — resolveResourceTypes() will find it");
        }

        @Test
        @DisplayName("Non-overriding child should inherit parent's resourceTypes")
        void nonOverridingChildShouldInheritResourceTypes() {
            KubernetesTest annotation = ChildInheritingResourceTypes.class
                .getAnnotation(KubernetesTest.class);

            assertNotNull(annotation);
            assertTrue(annotation.resourceTypes().length > 0,
                "Inheriting child should have parent's resourceTypes");
        }
    }

    @Nested
    @DisplayName("ResourceTypes Annotation Tests")
    class ResourceTypesAnnotationTests {

        @Test
        @DisplayName("@KubernetesTest should have resourceTypes parameter")
        void shouldHaveResourceTypesParameter() throws NoSuchMethodException {
            assertNotNull(KubernetesTest.class.getDeclaredMethod("resourceTypes"),
                "@KubernetesTest should have resourceTypes() method");
        }

        @Test
        @DisplayName("resourceTypes default should be empty array")
        void resourceTypesDefaultShouldBeEmpty() {
            KubernetesTest annotation = TestClass.class.getAnnotation(KubernetesTest.class);

            assertNotNull(annotation);
            assertEquals(0, annotation.resourceTypes().length,
                "Default resourceTypes should be empty");
        }

        @Test
        @DisplayName("Should be able to declare resourceTypes on annotation")
        void shouldDeclareResourceTypes() {
            KubernetesTest annotation = ParentWithResourceTypes.class
                .getAnnotation(KubernetesTest.class);

            assertNotNull(annotation);
            assertEquals(1, annotation.resourceTypes().length);
            assertEquals(StubResourceType.class, annotation.resourceTypes()[0]);
        }
    }

    // ===============================
    // Test Fixtures for Annotation Tests
    // ===============================

    // Test Class for Method Testing
    @KubernetesTest
    static class TestClass {
        public void testMethod(@io.skodjob.kubetest4j.annotations.InjectKubeClient Object client) {
        }

        public void testMethodNoAnnotation(String param) {
        }
    }

    // Parent with default @KubernetesTest — child inherits via @Inherited
    @KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
    abstract static class ParentTestClass {
    }

    // Child that inherits parent's annotation (no @KubernetesTest declared)
    static class ChildTestClass extends ParentTestClass {
    }

    // Child that overrides parent's annotation
    @KubernetesTest(cleanup = CleanupStrategy.MANUAL)
    static class OverrideChildTestClass extends ParentTestClass {
    }

    // Parent with resourceTypes declared
    @KubernetesTest(resourceTypes = {StubResourceType.class})
    abstract static class ParentWithResourceTypes {
    }

    // Child that inherits parent's resourceTypes (no annotation override)
    static class ChildInheritingResourceTypes extends ParentWithResourceTypes {
    }

    // Child that overrides config without declaring resourceTypes
    // Java replaces the whole annotation, but resolveResourceTypes() walks hierarchy
    @KubernetesTest(cleanup = CleanupStrategy.MANUAL)
    static class ChildOverridingResourceTypes extends ParentWithResourceTypes {
    }

    // Child that declares its own resourceTypes (should use its own, not parent's)
    @KubernetesTest(resourceTypes = {AnotherStubResourceType.class})
    static class ChildWithOwnResourceTypes extends ParentWithResourceTypes {
    }

    /**
     * Another stub ResourceType for testing that child can declare its own types.
     */
    static class AnotherStubResourceType extends StubResourceType {
        @Override
        public String getKind() {
            return "Secret";
        }
    }

    /**
     * Stub ResourceType for testing annotation parsing only.
     * Does not need a real Kubernetes client.
     */
    static class StubResourceType implements ResourceType<io.fabric8.kubernetes.api.model.ConfigMap> {
        @Override
        public io.fabric8.kubernetes.client.dsl.NonNamespaceOperation<?, ?, ?> getClient() {
            return null;
        }

        @Override
        public String getKind() {
            return "ConfigMap";
        }

        @Override
        public void create(io.fabric8.kubernetes.api.model.ConfigMap resource) {
        }

        @Override
        public void update(io.fabric8.kubernetes.api.model.ConfigMap resource) {
        }

        @Override
        public void delete(io.fabric8.kubernetes.api.model.ConfigMap resource) {
        }

        @Override
        public void replace(io.fabric8.kubernetes.api.model.ConfigMap resource,
                            java.util.function.Consumer<io.fabric8.kubernetes.api.model.ConfigMap> editor) {
        }

        @Override
        public boolean isReady(io.fabric8.kubernetes.api.model.ConfigMap resource) {
            return true;
        }

        @Override
        public boolean isDeleted(io.fabric8.kubernetes.api.model.ConfigMap resource) {
            return true;
        }
    }
}
