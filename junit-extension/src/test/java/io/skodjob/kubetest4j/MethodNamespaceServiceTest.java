/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.annotations.MethodNamespace;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MethodNamespaceService.
 */
@ExtendWith(MockitoExtension.class)
class MethodNamespaceServiceTest {

    @Mock
    private ContextStoreHelper contextStoreHelper;

    @Mock
    private NamespaceService.MultiKubeContextProvider multiContextProvider;

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private KubeResourceManager resourceManager;

    @Mock
    private KubeClient kubeClient;

    @Mock
    private KubernetesClient k8sClient;

    @Mock
    private MixedOperation namespacesOp;

    @Mock
    private Resource namespaceResource;

    private MethodNamespaceService service;

    @BeforeEach
    void setUp() {
        service = new MethodNamespaceService(contextStoreHelper, multiContextProvider);

        // Common mock chain for namespace retrieval
        lenient().when(resourceManager.kubeClient()).thenReturn(kubeClient);
        lenient().when(kubeClient.getClient()).thenReturn(k8sClient);
        lenient().when(k8sClient.namespaces()).thenReturn(namespacesOp);
        lenient().when(namespacesOp.withName(any(String.class))).thenReturn(namespaceResource);
    }

    // ===============================
    // Helper methods and classes for method reflection
    // ===============================

    /**
     * Dummy class with methods used to obtain Method objects for generateNamespaceName tests.
     */
    static class MethodHolder {
        void simpleMethod() {
            // Intentionally empty — used only to obtain a Method object via reflection
        }

        void aVeryLongMethodNameThatWillDefinitelyExceedTheSixtyThreeCharacterLimit() {
            // Intentionally empty — used only to obtain a Method object via reflection
        }
    }

    private Method getMethod(String name) {
        try {
            return MethodHolder.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private ExtensionContext mockContextWithMethod(String methodName) {
        Method method = getMethod(methodName);
        ExtensionContext ctx = org.mockito.Mockito.mock(ExtensionContext.class);
        when(ctx.getTestMethod()).thenReturn(Optional.of(method));
        return ctx;
    }

    // ===============================
    // Test helper classes for field validation
    // ===============================

    static class NoAnnotationsClass {
        Namespace regularField;
    }

    static class ValidTestClass {
        @MethodNamespace(prefix = "worker")
        Namespace workerNs;
    }

    static class StaticFieldClass {
        @MethodNamespace(prefix = "bad")
        static Namespace staticNs;
    }

    static class WrongTypeClass {
        @MethodNamespace(prefix = "bad")
        String notANamespace;
    }

    static class DualAnnotationClass {
        @MethodNamespace(prefix = "bad")
        @ClassNamespace(name = "shared")
        Namespace conflicting;
    }

    // ===============================
    // Nested test classes
    // ===============================

    @Nested
    @DisplayName("generateNamespaceName Tests")
    class GenerateNamespaceNameTests {

        @Test
        @DisplayName("Simple prefix and method name produces correct format")
        void simplePrefixAndMethodName() {
            ExtensionContext ctx = mockContextWithMethod("simpleMethod");

            String name = MethodNamespaceService.generateNamespaceName("test", ctx, 0);

            assertEquals("test-simplemethod-0", name);
        }

        @Test
        @DisplayName("Special characters in prefix are sanitized to dashes")
        void specialCharactersSanitized() {
            ExtensionContext ctx = mockContextWithMethod("simpleMethod");

            // Underscores and $ in prefix become dashes, consecutive dashes collapsed
            String name = MethodNamespaceService.generateNamespaceName(
                "my_prefix$with.CHARS", ctx, 0);

            assertTrue(name.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?"),
                "Name must be DNS-1123 compliant: " + name);
            assertTrue(name.startsWith("my-prefix-with-chars-"),
                "Prefix must be sanitized: " + name);
            assertTrue(name.endsWith("-0"),
                "Name must end with index: " + name);
            // Verify no consecutive dashes
            assertTrue(!name.contains("--"),
                "Name must not contain consecutive dashes: " + name);
        }

        @Test
        @DisplayName("Long names are truncated to 63 characters max")
        void longNamesTruncated() {
            ExtensionContext ctx = mockContextWithMethod(
                "aVeryLongMethodNameThatWillDefinitelyExceedTheSixtyThreeCharacterLimit");

            String name = MethodNamespaceService.generateNamespaceName("prefix", ctx, 0);

            assertTrue(name.length() <= 63,
                "Name length " + name.length() + " exceeds 63: " + name);
            assertTrue(name.startsWith("prefix-"),
                "Name must start with prefix: " + name);
        }

        @Test
        @DisplayName("Trailing dashes from truncation are removed")
        void trailingDashesRemoved() {
            ExtensionContext ctx = mockContextWithMethod(
                "aVeryLongMethodNameThatWillDefinitelyExceedTheSixtyThreeCharacterLimit");

            String name = MethodNamespaceService.generateNamespaceName("prefix", ctx, 0);

            assertTrue(!name.endsWith("-0") || !name.contains("--"),
                "Truncated name must not have trailing dashes before suffix: " + name);
            // The name should end with -<index>
            assertTrue(name.endsWith("-0"),
                "Name must end with index: " + name);
            // No trailing dash before the last segment
            String withoutSuffix = name.substring(0, name.lastIndexOf("-0"));
            assertTrue(!withoutSuffix.endsWith("-"),
                "Name must not have trailing dash before index: " + name);
        }

        @Test
        @DisplayName("Very long prefix is truncated")
        void veryLongPrefixTruncated() {
            ExtensionContext ctx = mockContextWithMethod("simpleMethod");

            String longPrefix = "a".repeat(70);
            String name = MethodNamespaceService.generateNamespaceName(
                longPrefix, ctx, 0);

            // Prefix is truncated so total stays near the limit
            assertTrue(name.startsWith("a"),
                "Name must start with truncated prefix: " + name);
            assertTrue(name.endsWith("-0"),
                "Name must end with index: " + name);
            // Prefix portion should be shorter than original 70 chars
            String prefixPart = name.substring(
                0, name.indexOf("-", 1));
            assertTrue(prefixPart.length() < 70,
                "Prefix must be truncated from 70: " + prefixPart.length());
        }

        @Test
        @DisplayName("Index value is included in the name")
        void indexIncluded() {
            ExtensionContext ctx = mockContextWithMethod("simpleMethod");

            String name = MethodNamespaceService.generateNamespaceName("ns", ctx, 5);

            assertTrue(name.endsWith("-5"), "Name must end with index 5: " + name);
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Static field with @MethodNamespace throws IllegalArgumentException")
        void staticFieldThrows() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) StaticFieldClass.class);
            lenient().when(extensionContext.getTestMethod())
                .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createMethodNamespaces(extensionContext));

            assertTrue(ex.getMessage().contains("static"),
                "Message should mention static: " + ex.getMessage());
        }

        @Test
        @DisplayName("Non-Namespace type field throws IllegalArgumentException")
        void wrongTypeFieldThrows() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) WrongTypeClass.class);
            lenient().when(extensionContext.getTestMethod())
                .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createMethodNamespaces(extensionContext));

            assertTrue(ex.getMessage().contains("must be of type Namespace"),
                "Message should mention type: " + ex.getMessage());
        }

        @Test
        @DisplayName("Field with both @MethodNamespace and @ClassNamespace throws")
        void dualAnnotationThrows() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) DualAnnotationClass.class);
            lenient().when(extensionContext.getTestMethod())
                .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createMethodNamespaces(extensionContext));

            assertTrue(ex.getMessage().contains("@MethodNamespace and @ClassNamespace"),
                "Message should mention both annotations: " + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("createMethodNamespaces Tests")
    class CreateMethodNamespacesTests {

        @Test
        @DisplayName("Class with no @MethodNamespace fields produces no entries")
        void noAnnotatedFieldsProducesNoEntries() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) NoAnnotationsClass.class);
            when(extensionContext.getTestMethod()).thenReturn(Optional.empty());

            service.createMethodNamespaces(extensionContext);

            verify(contextStoreHelper, never()).putMethodNamespaceEntries(any(), any());
        }

        @Test
        @DisplayName("Field with @MethodNamespace creates namespace via KRM")
        void fieldCreatesNamespace() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) ValidTestClass.class);
            Method method = getMethod("simpleMethod");
            when(extensionContext.getTestMethod()).thenReturn(Optional.of(method));
            when(extensionContext.getDisplayName()).thenReturn("simpleMethod()");
            when(contextStoreHelper.getResourceManager(extensionContext))
                .thenReturn(resourceManager);

            Namespace retrievedNs = new NamespaceBuilder()
                .withNewMetadata()
                .withName("worker-simplemethod-0")
                .endMetadata()
                .build();
            when(namespaceResource.get()).thenReturn(retrievedNs);

            service.createMethodNamespaces(extensionContext);

            verify(resourceManager).createResourceWithWait(any(Namespace.class));
            verify(contextStoreHelper).putMethodNamespaceEntries(
                any(ExtensionContext.class), any(List.class));
        }

        @Test
        @DisplayName("When cluster returns null, built namespace is used as fallback")
        void fallbackToBuiltNamespaceWhenClusterReturnsNull() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) ValidTestClass.class);
            Method method = getMethod("simpleMethod");
            when(extensionContext.getTestMethod()).thenReturn(Optional.of(method));
            when(extensionContext.getDisplayName()).thenReturn("simpleMethod()");
            when(contextStoreHelper.getResourceManager(extensionContext))
                .thenReturn(resourceManager);
            when(namespaceResource.get()).thenReturn(null);

            service.createMethodNamespaces(extensionContext);

            verify(resourceManager).createResourceWithWait(any(Namespace.class));
            verify(contextStoreHelper).putMethodNamespaceEntries(
                any(ExtensionContext.class), any(List.class));
        }
    }

    @Nested
    @DisplayName("resolveMethodNamespace Tests")
    class ResolveMethodNamespaceTests {

        @Test
        @DisplayName("Returns null when no entries stored")
        void returnsNullWhenNoEntries() {
            when(contextStoreHelper.getMethodNamespaceEntries(extensionContext))
                .thenReturn(null);

            Namespace result = service.resolveMethodNamespace(extensionContext, "workerNs");

            assertNull(result);
        }

        @Test
        @DisplayName("Returns correct namespace by source identity")
        void returnsCorrectNamespaceByIdentity() {
            Namespace ns = new NamespaceBuilder()
                .withNewMetadata().withName("worker-test-0").endMetadata().build();
            List<MethodNamespaceService.MethodNamespaceEntry> entries = new ArrayList<>();
            entries.add(new MethodNamespaceService.MethodNamespaceEntry(ns, "workerNs"));
            when(contextStoreHelper.getMethodNamespaceEntries(extensionContext))
                .thenReturn(entries);

            Namespace result = service.resolveMethodNamespace(extensionContext, "workerNs");

            assertNotNull(result);
            assertEquals("worker-test-0", result.getMetadata().getName());
        }

        @Test
        @DisplayName("Returns null for unknown source identity")
        void returnsNullForUnknownIdentity() {
            Namespace ns = new NamespaceBuilder()
                .withNewMetadata().withName("worker-test-0").endMetadata().build();
            List<MethodNamespaceService.MethodNamespaceEntry> entries = new ArrayList<>();
            entries.add(new MethodNamespaceService.MethodNamespaceEntry(ns, "workerNs"));
            when(contextStoreHelper.getMethodNamespaceEntries(extensionContext))
                .thenReturn(entries);

            Namespace result = service.resolveMethodNamespace(extensionContext, "unknownField");

            assertNull(result);
        }
    }
}
