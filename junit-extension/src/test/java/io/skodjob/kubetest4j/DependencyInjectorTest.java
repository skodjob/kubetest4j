/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.annotations.InjectCmdKubeClient;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectNamespace;
import io.skodjob.kubetest4j.annotations.InjectNamespaces;
import io.skodjob.kubetest4j.annotations.InjectResource;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DependencyInjector.
 * These tests focus on individual method logic and error handling without requiring real Kubernetes resources.
 */
@ExtendWith(MockitoExtension.class)
class DependencyInjectorTest {

    @Mock
    private ContextStoreHelper contextStoreHelper;

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private ParameterContext parameterContext;

    @Mock
    private Parameter parameter;

    @Mock
    private KubeResourceManager resourceManager;

    @Mock
    private KubeClient kubeClient;

    @Mock
    private KubeCmdClient<?> kubeCmdClient;

    private DependencyInjector dependencyInjector;

    @BeforeEach
    void setUp() {
        dependencyInjector = new DependencyInjector(contextStoreHelper);

        // Setup common mocks
        lenient().when(parameterContext.getParameter()).thenReturn(parameter);
        lenient().when(resourceManager.kubeClient()).thenReturn(kubeClient);
        lenient().when(resourceManager.kubeCmdClient()).thenReturn(kubeCmdClient);

        // Setup default lenient stubbing for all annotation checks to return false
        lenient().when(parameterContext.isAnnotated(InjectKubeClient.class)).thenReturn(false);
        lenient().when(parameterContext.isAnnotated(InjectCmdKubeClient.class)).thenReturn(false);
        lenient().when(parameterContext.isAnnotated(InjectResourceManager.class)).thenReturn(false);
        lenient().when(parameterContext.isAnnotated(InjectResource.class)).thenReturn(false);
        lenient().when(parameterContext.isAnnotated(InjectNamespaces.class)).thenReturn(false);
        lenient().when(parameterContext.isAnnotated(InjectNamespace.class)).thenReturn(false);
    }

    @Nested
    @DisplayName("Parameter Resolution Tests")
    class ParameterResolutionTests {

        @Test
        @DisplayName("Should support InjectKubeClient parameter")
        void shouldSupportInjectKubeClientParameter() {
            // Given
            when(parameterContext.isAnnotated(InjectKubeClient.class)).thenReturn(true);

            // When
            boolean supports = dependencyInjector.supportsParameter(parameterContext);

            // Then
            assertTrue(supports);
        }

        @Test
        @DisplayName("Should support InjectCmdKubeClient parameter")
        void shouldSupportInjectCmdKubeClientParameter() {
            // Given
            when(parameterContext.isAnnotated(InjectCmdKubeClient.class)).thenReturn(true);

            // When
            boolean supports = dependencyInjector.supportsParameter(parameterContext);

            // Then
            assertTrue(supports);
        }

        @Test
        @DisplayName("Should support InjectResourceManager parameter")
        void shouldSupportInjectResourceManagerParameter() {
            // Given
            when(parameterContext.isAnnotated(InjectResourceManager.class)).thenReturn(true);

            // When
            boolean supports = dependencyInjector.supportsParameter(parameterContext);

            // Then
            assertTrue(supports);
        }

        @Test
        @DisplayName("Should support InjectResource parameter")
        void shouldSupportInjectResourceParameter() {
            // Given
            when(parameterContext.isAnnotated(InjectResource.class)).thenReturn(true);

            // When
            boolean supports = dependencyInjector.supportsParameter(parameterContext);

            // Then
            assertTrue(supports);
        }

        @Test
        @DisplayName("Should support InjectNamespaces parameter")
        void shouldSupportInjectNamespacesParameter() {
            // Given
            when(parameterContext.isAnnotated(InjectNamespaces.class)).thenReturn(true);

            // When
            boolean supports = dependencyInjector.supportsParameter(parameterContext);

            // Then
            assertTrue(supports);
        }

        @Test
        @DisplayName("Should support InjectNamespace parameter")
        void shouldSupportInjectNamespaceParameter() {
            // Given
            when(parameterContext.isAnnotated(InjectNamespace.class)).thenReturn(true);

            // When
            boolean supports = dependencyInjector.supportsParameter(parameterContext);

            // Then
            assertTrue(supports);
        }

        @Test
        @DisplayName("Should not support unannotated parameter")
        void shouldNotSupportUnannotatedParameter() {
            // Given - all annotation checks return false by default

            // When
            boolean supports = dependencyInjector.supportsParameter(parameterContext);

            // Then
            assertFalse(supports);
        }

        @Test
        @DisplayName("Should resolve InjectKubeClient parameter")
        void shouldResolveInjectKubeClientParameter() throws Exception {
            // Given
            InjectKubeClient annotation = createInjectKubeClientAnnotation("");
            when(parameterContext.isAnnotated(InjectKubeClient.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectKubeClient.class)).thenReturn(annotation);
            when(contextStoreHelper.getResourceManager(extensionContext)).thenReturn(resourceManager);

            // When
            Object result = dependencyInjector.resolveParameter(parameterContext, extensionContext);

            // Then
            assertEquals(kubeClient, result);
        }

        @Test
        @DisplayName("Should resolve InjectCmdKubeClient parameter")
        void shouldResolveInjectCmdKubeClientParameter() throws Exception {
            // Given
            InjectCmdKubeClient annotation = createInjectCmdKubeClientAnnotation("");
            when(parameterContext.isAnnotated(InjectCmdKubeClient.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectCmdKubeClient.class)).thenReturn(annotation);
            when(contextStoreHelper.getResourceManager(extensionContext)).thenReturn(resourceManager);

            // When
            Object result = dependencyInjector.resolveParameter(parameterContext, extensionContext);

            // Then
            assertEquals(kubeCmdClient, result);
        }

        @Test
        @DisplayName("Should resolve InjectResourceManager parameter")
        void shouldResolveInjectResourceManagerParameter() throws Exception {
            // Given
            InjectResourceManager annotation = createInjectResourceManagerAnnotation("");
            when(parameterContext.isAnnotated(InjectResourceManager.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectResourceManager.class)).thenReturn(annotation);
            when(contextStoreHelper.getResourceManager(extensionContext)).thenReturn(resourceManager);

            // When
            Object result = dependencyInjector.resolveParameter(parameterContext, extensionContext);

            // Then
            assertEquals(resourceManager, result);
        }

        @Test
        @DisplayName("Should resolve InjectNamespaces parameter")
        void shouldResolveInjectNamespacesParameter() throws Exception {
            // Given
            InjectNamespaces annotation = createInjectNamespacesAnnotation("");
            Map<String, Namespace> namespaces = Map.of("test-ns",
                new NamespaceBuilder().withNewMetadata().withName("test-ns").endMetadata().build());

            when(parameterContext.isAnnotated(InjectNamespaces.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectNamespaces.class)).thenReturn(annotation);
            when(contextStoreHelper.getNamespaceObjects(extensionContext)).thenReturn(namespaces);

            // When
            Object result = dependencyInjector.resolveParameter(parameterContext, extensionContext);

            // Then
            assertEquals(namespaces, result);
        }

        @Test
        @DisplayName("Should resolve InjectNamespace parameter")
        void shouldResolveInjectNamespaceParameter() throws Exception {
            // Given
            InjectNamespace annotation = createInjectNamespaceAnnotation("test-ns", "");
            Namespace namespace = new NamespaceBuilder().withNewMetadata().withName("test-ns").endMetadata().build();
            Map<String, Namespace> namespaces = Map.of("test-ns", namespace);

            when(parameterContext.isAnnotated(InjectNamespace.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectNamespace.class)).thenReturn(annotation);
            when(contextStoreHelper.getNamespaceObjects(extensionContext)).thenReturn(namespaces);

            // When
            Object result = dependencyInjector.resolveParameter(parameterContext, extensionContext);

            // Then
            assertEquals(namespace, result);
        }

        @Test
        @DisplayName("Should throw ParameterResolutionException when injection fails")
        void shouldThrowParameterResolutionExceptionWhenInjectionFails() {
            // Given
            InjectKubeClient annotation = createInjectKubeClientAnnotation("");
            when(parameterContext.isAnnotated(InjectKubeClient.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectKubeClient.class)).thenReturn(annotation);
            when(contextStoreHelper.getResourceManager(extensionContext)).thenReturn(null); // No resource manager

            // When & Then
            assertThrows(ParameterResolutionException.class,
                () -> dependencyInjector.resolveParameter(parameterContext, extensionContext));
        }
    }

    @Nested
    @DisplayName("Field Injection Tests")
    class FieldInjectionTests {

        @Test
        @DisplayName("Should inject annotated fields")
        void shouldInjectAnnotatedFields() throws Exception {
            // Given
            TestClassWithFields testInstance = new TestClassWithFields();
            when(extensionContext.getTestInstance()).thenReturn(Optional.of(testInstance));
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClassWithFields.class);
            when(contextStoreHelper.getResourceManager(extensionContext)).thenReturn(resourceManager);

            // When
            dependencyInjector.injectTestClassFields(extensionContext);

            // Then
            assertNotNull(testInstance.kubeClient);
            assertEquals(kubeClient, testInstance.kubeClient);
        }

        @Test
        @DisplayName("Should handle missing test instance gracefully")
        void shouldHandleMissingTestInstanceGracefully() {
            // Given
            when(extensionContext.getTestInstance()).thenReturn(Optional.empty());

            // When & Then - should not throw exception
            dependencyInjector.injectTestClassFields(extensionContext);
        }

        @Test
        @DisplayName("Should throw RuntimeException when field injection fails")
        void shouldThrowRuntimeExceptionWhenFieldInjectionFails() throws Exception {
            // Given
            TestClassWithFields testInstance = new TestClassWithFields();
            when(extensionContext.getTestInstance()).thenReturn(Optional.of(testInstance));
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClassWithFields.class);
            when(contextStoreHelper.getResourceManager(extensionContext)).thenReturn(null); // Trigger failure

            // When & Then
            assertThrows(RuntimeException.class,
                () -> dependencyInjector.injectTestClassFields(extensionContext));
        }
    }

    @Nested
    @DisplayName("Context-Specific Injection Tests")
    class ContextSpecificInjectionTests {

        @Test
        @DisplayName("Should inject KubeClient for specific context")
        void shouldInjectKubeClientForSpecificContext() throws Exception {
            // Given
            InjectKubeClient annotation = createInjectKubeClientAnnotation("staging");
            when(parameterContext.isAnnotated(InjectKubeClient.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectKubeClient.class)).thenReturn(annotation);
            when(contextStoreHelper.getContextManager(extensionContext, "staging")).thenReturn(resourceManager);

            // When
            Object result = dependencyInjector.resolveParameter(parameterContext, extensionContext);

            // Then
            assertEquals(kubeClient, result);
        }

        @Test
        @DisplayName("Should inject namespaces for specific context")
        void shouldInjectNamespacesForSpecificContext() throws Exception {
            // Given
            InjectNamespaces annotation = createInjectNamespacesAnnotation("staging");
            Map<String, Namespace> namespaces = Map.of("stg-ns",
                new NamespaceBuilder().withNewMetadata().withName("stg-ns").endMetadata().build());

            when(parameterContext.isAnnotated(InjectNamespaces.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectNamespaces.class)).thenReturn(annotation);
            when(contextStoreHelper.getNamespaceObjectsForContext(extensionContext, "staging")).thenReturn(namespaces);

            // When
            Object result = dependencyInjector.resolveParameter(parameterContext, extensionContext);

            // Then
            assertEquals(namespaces, result);
        }

        @Test
        @DisplayName("Should throw exception when context manager not available")
        void shouldThrowExceptionWhenContextManagerNotAvailable() {
            // Given
            InjectKubeClient annotation = createInjectKubeClientAnnotation("nonexistent");
            when(parameterContext.isAnnotated(InjectKubeClient.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectKubeClient.class)).thenReturn(annotation);
            when(contextStoreHelper.getContextManager(extensionContext, "nonexistent")).thenReturn(null);

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> dependencyInjector.resolveParameter(parameterContext, extensionContext));
            assertTrue(exception.getMessage()
                .contains("KubeResourceManager not available for kubeContext: nonexistent"));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for namespace not in test configuration")
        void shouldThrowExceptionForNamespaceNotInTestConfiguration() {
            // Given
            InjectNamespace annotation = createInjectNamespaceAnnotation("nonexistent-ns", "");
            Map<String, Namespace> namespaces = Map.of("test-ns",
                new NamespaceBuilder().withNewMetadata().withName("test-ns").endMetadata().build());

            when(parameterContext.isAnnotated(InjectNamespace.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectNamespace.class)).thenReturn(annotation);
            when(contextStoreHelper.getNamespaceObjects(extensionContext)).thenReturn(namespaces);

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> dependencyInjector.resolveParameter(parameterContext, extensionContext));
            assertTrue(exception.getMessage().contains("Namespace 'nonexistent-ns' not found"));
        }

        @Test
        @DisplayName("Should throw exception when namespaces not available")
        void shouldThrowExceptionWhenNamespacesNotAvailable() {
            // Given
            InjectNamespaces annotation = createInjectNamespacesAnnotation("");
            when(parameterContext.isAnnotated(InjectNamespaces.class)).thenReturn(true);
            when(parameter.getAnnotation(InjectNamespaces.class)).thenReturn(annotation);
            when(contextStoreHelper.getNamespaceObjects(extensionContext)).thenReturn(null);

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> dependencyInjector.resolveParameter(parameterContext, extensionContext));
            assertTrue(exception.getMessage().contains("Namespace objects not available for kubeContext: primary"));
        }

        @Test
        @DisplayName("Should throw exception for unsupported injection type")
        void shouldThrowExceptionForUnsupportedInjectionType() {
            // Given - parameter with no supported annotations
            when(parameter.toString()).thenReturn("unsupported parameter");

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> dependencyInjector.resolveParameter(parameterContext, extensionContext));
            assertTrue(exception.getMessage().contains("Cannot resolve injection for: unsupported parameter"));
        }
    }

    // ===============================
    // Test Helper Classes and Methods
    // ===============================

    static class TestClassWithFields {
        @InjectKubeClient
        KubeClient kubeClient;

        @InjectCmdKubeClient
        KubeCmdClient<?> cmdKubeClient;

        @InjectResourceManager
        KubeResourceManager resourceManager;

        // Non-annotated field
        String nonInjectable;
    }

    // Mock annotation creation methods
    private InjectKubeClient createInjectKubeClientAnnotation(String context) {
        return new InjectKubeClient() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return InjectKubeClient.class;
            }

            @Override
            public String kubeContext() {
                return context;
            }
        };
    }

    private InjectCmdKubeClient createInjectCmdKubeClientAnnotation(String context) {
        return new InjectCmdKubeClient() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return InjectCmdKubeClient.class;
            }

            @Override
            public String kubeContext() {
                return context;
            }
        };
    }

    private InjectResourceManager createInjectResourceManagerAnnotation(String context) {
        return new InjectResourceManager() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return InjectResourceManager.class;
            }

            @Override
            public String kubeContext() {
                return context;
            }
        };
    }

    private InjectNamespaces createInjectNamespacesAnnotation(String context) {
        return new InjectNamespaces() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return InjectNamespaces.class;
            }

            @Override
            public String kubeContext() {
                return context;
            }
        };
    }

    private InjectNamespace createInjectNamespaceAnnotation(String name, String context) {
        return new InjectNamespace() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return InjectNamespace.class;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String kubeContext() {
                return context;
            }
        };
    }
}