/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ClassNamespaceService.
 * These tests verify class namespace creation, cleanup, and resolution
 * without requiring a real Kubernetes cluster.
 */
@ExtendWith(MockitoExtension.class)
class ClassNamespaceServiceTest {

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
    private KubernetesClient kubernetesClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private NonNamespaceOperation namespacesOp;

    @Mock
    @SuppressWarnings("rawtypes")
    private Resource namespaceResource;

    private ClassNamespaceService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        service = new ClassNamespaceService(contextStoreHelper, multiContextProvider);

        // Common mock chain: resourceManager -> kubeClient -> client -> namespaces()
        lenient().when(resourceManager.kubeClient()).thenReturn(kubeClient);
        lenient().when(kubeClient.getClient()).thenReturn(kubernetesClient);
        lenient().when(kubernetesClient.namespaces()).thenReturn(namespacesOp);
        lenient().when(namespacesOp.withName(any())).thenReturn(namespaceResource);
    }

    // ===============================
    // Test helper classes for field scanning
    // ===============================

    static class NoAnnotationTestClass {
        static Namespace noAnnotation;
    }

    static class ValidTestClass {
        @ClassNamespace(name = "test-ns")
        static Namespace testNs;
    }

    static class NonStaticFieldTestClass {
        @ClassNamespace(name = "test-ns")
        Namespace testNs;
    }

    static class WrongTypeFieldTestClass {
        @ClassNamespace(name = "test-ns")
        static String testNs;
    }

    static class WithLabelsAndAnnotationsTestClass {
        @ClassNamespace(
            name = "labeled-ns",
            labels = {"env=test", "team=backend"},
            annotations = {"owner=ci"}
        )
        static Namespace labeledNs;
    }

    static class MultiFieldTestClass {
        @ClassNamespace(name = "ns-one")
        static Namespace nsOne;

        @ClassNamespace(name = "ns-two")
        static Namespace nsTwo;
    }

    // ===============================
    // Nested Test Classes
    // ===============================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException for non-static field")
        void shouldThrowForNonStaticField() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) NonStaticFieldTestClass.class);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createClassNamespaces(extensionContext));

            assertTrue(ex.getMessage().contains("static"));
            assertTrue(ex.getMessage().contains("testNs"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for non-Namespace type field")
        void shouldThrowForWrongType() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) WrongTypeFieldTestClass.class);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createClassNamespaces(extensionContext));

            assertTrue(ex.getMessage().contains("Namespace"));
            assertTrue(ex.getMessage().contains("testNs"));
        }
    }

    @Nested
    @DisplayName("createClassNamespaces Tests")
    class CreateClassNamespacesTests {

        @Test
        @DisplayName("Class with no @ClassNamespace fields produces no entries")
        void shouldProduceNoEntriesForUnannotatedClass() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) NoAnnotationTestClass.class);

            service.createClassNamespaces(extensionContext);

            verify(contextStoreHelper, never())
                .putClassNamespaceEntries(any(), any());
        }

        @Test
        @DisplayName("Existing namespace on cluster uses it with created=false")
        @SuppressWarnings("unchecked")
        void shouldUseExistingNamespace() {
            Namespace existing = new NamespaceBuilder()
                .withNewMetadata().withName("test-ns").endMetadata()
                .build();

            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) ValidTestClass.class);
            when(contextStoreHelper.getResourceManager(extensionContext))
                .thenReturn(resourceManager);
            when(namespaceResource.get()).thenReturn(existing);

            service.createClassNamespaces(extensionContext);

            // Should NOT call createResourceWithWait
            verify(resourceManager, never()).createResourceWithWait(any());

            // Should store entries with created=false
            ArgumentCaptor<List<ClassNamespaceService.ClassNamespaceEntry>> captor =
                ArgumentCaptor.forClass(List.class);
            verify(contextStoreHelper)
                .putClassNamespaceEntries(any(), captor.capture());

            List<ClassNamespaceService.ClassNamespaceEntry> entries = captor.getValue();
            assertEquals(1, entries.size());
            assertEquals(false, entries.get(0).created());
            assertEquals("test-ns",
                entries.get(0).namespace().getMetadata().getName());
        }

        @Test
        @DisplayName("New namespace creates via KRM with created=true")
        @SuppressWarnings("unchecked")
        void shouldCreateNewNamespace() {
            Namespace createdNs = new NamespaceBuilder()
                .withNewMetadata().withName("test-ns").endMetadata()
                .build();

            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) ValidTestClass.class);
            when(contextStoreHelper.getResourceManager(extensionContext))
                .thenReturn(resourceManager);
            // First call returns null (doesn't exist), second call returns created
            when(namespaceResource.get()).thenReturn(null).thenReturn(createdNs);

            service.createClassNamespaces(extensionContext);

            // Should call createResourceWithWait
            verify(resourceManager).createResourceWithWait(any(Namespace.class));
            // Should call removeFromStack
            verify(resourceManager).removeFromStack(any(Namespace.class));

            ArgumentCaptor<List<ClassNamespaceService.ClassNamespaceEntry>> captor =
                ArgumentCaptor.forClass(List.class);
            verify(contextStoreHelper)
                .putClassNamespaceEntries(any(), captor.capture());

            List<ClassNamespaceService.ClassNamespaceEntry> entries = captor.getValue();
            assertEquals(1, entries.size());
            assertEquals(true, entries.get(0).created());
            assertEquals("testNs", entries.get(0).fieldName());
        }

        @Test
        @DisplayName("Labels and annotations from annotation are applied")
        @SuppressWarnings("unchecked")
        void shouldApplyLabelsAndAnnotations() {
            when(extensionContext.getRequiredTestClass())
                .thenReturn((Class) WithLabelsAndAnnotationsTestClass.class);
            when(contextStoreHelper.getResourceManager(extensionContext))
                .thenReturn(resourceManager);

            Namespace createdNs = new NamespaceBuilder()
                .withNewMetadata().withName("labeled-ns").endMetadata()
                .build();
            // First get() returns null (needs creation), second returns created
            when(namespaceResource.get()).thenReturn(null).thenReturn(createdNs);

            service.createClassNamespaces(extensionContext);

            ArgumentCaptor<Namespace> nsCaptor =
                ArgumentCaptor.forClass(Namespace.class);
            verify(resourceManager).createResourceWithWait(nsCaptor.capture());

            Namespace created = nsCaptor.getValue();
            assertEquals("labeled-ns", created.getMetadata().getName());
            assertEquals("test", created.getMetadata().getLabels().get("env"));
            assertEquals("backend",
                created.getMetadata().getLabels().get("team"));
            assertEquals("ci",
                created.getMetadata().getAnnotations().get("owner"));
        }
    }

    @Nested
    @DisplayName("cleanupClassNamespaces Tests")
    class CleanupClassNamespacesTests {

        @Test
        @DisplayName("Null entries is a no-op")
        void shouldNoOpForNullEntries() {
            when(contextStoreHelper.getClassNamespaceEntries(extensionContext))
                .thenReturn(null);

            service.cleanupClassNamespaces(extensionContext);

            verifyNoInteractions(resourceManager);
        }

        @Test
        @DisplayName("Empty entries list is a no-op")
        void shouldNoOpForEmptyEntries() {
            when(contextStoreHelper.getClassNamespaceEntries(extensionContext))
                .thenReturn(new ArrayList<>());

            service.cleanupClassNamespaces(extensionContext);

            verifyNoInteractions(resourceManager);
        }

        @Test
        @DisplayName("Pre-existing namespace (created=false) is skipped")
        void shouldSkipPreExistingNamespace() {
            Namespace ns = new NamespaceBuilder()
                .withNewMetadata().withName("existing-ns").endMetadata()
                .build();
            KubeResourceManager entryRm = mock(KubeResourceManager.class);

            ClassNamespaceService.ClassNamespaceEntry entry =
                new ClassNamespaceService.ClassNamespaceEntry(
                    ns, "testNs", "", false, entryRm);

            when(contextStoreHelper.getClassNamespaceEntries(extensionContext))
                .thenReturn(List.of(entry));

            service.cleanupClassNamespaces(extensionContext);

            verify(entryRm, never()).deleteResourceWithWait(any());
        }

        @Test
        @DisplayName("Created namespace (created=true) is deleted")
        void shouldDeleteCreatedNamespace() {
            Namespace ns = new NamespaceBuilder()
                .withNewMetadata().withName("created-ns").endMetadata()
                .build();
            KubeResourceManager entryRm = mock(KubeResourceManager.class);

            ClassNamespaceService.ClassNamespaceEntry entry =
                new ClassNamespaceService.ClassNamespaceEntry(
                    ns, "testNs", "", true, entryRm);

            when(contextStoreHelper.getClassNamespaceEntries(extensionContext))
                .thenReturn(List.of(entry));

            service.cleanupClassNamespaces(extensionContext);

            verify(entryRm).deleteResourceWithWait(ns);
        }

        @Test
        @DisplayName("Exception during deletion is caught and does not propagate")
        void shouldCatchDeletionException() {
            Namespace ns = new NamespaceBuilder()
                .withNewMetadata().withName("failing-ns").endMetadata()
                .build();
            KubeResourceManager entryRm = mock(KubeResourceManager.class);
            doThrow(new RuntimeException("delete failed"))
                .when(entryRm).deleteResourceWithWait(ns);

            ClassNamespaceService.ClassNamespaceEntry entry =
                new ClassNamespaceService.ClassNamespaceEntry(
                    ns, "testNs", "", true, entryRm);

            when(contextStoreHelper.getClassNamespaceEntries(extensionContext))
                .thenReturn(List.of(entry));

            // Should not throw
            service.cleanupClassNamespaces(extensionContext);

            verify(entryRm).deleteResourceWithWait(ns);
        }
    }

    @Nested
    @DisplayName("resolveClassNamespace Tests")
    class ResolveClassNamespaceTests {

        @Test
        @DisplayName("Returns null when no entries stored")
        void shouldReturnNullWhenNoEntries() {
            when(contextStoreHelper.getClassNamespaceEntries(extensionContext))
                .thenReturn(null);

            Namespace result =
                service.resolveClassNamespace(extensionContext, "testNs");

            assertNull(result);
        }

        @Test
        @DisplayName("Returns correct namespace by field name")
        void shouldReturnNamespaceByFieldName() {
            Namespace nsOne = new NamespaceBuilder()
                .withNewMetadata().withName("ns-one").endMetadata()
                .build();
            Namespace nsTwo = new NamespaceBuilder()
                .withNewMetadata().withName("ns-two").endMetadata()
                .build();
            KubeResourceManager entryRm = mock(KubeResourceManager.class);

            List<ClassNamespaceService.ClassNamespaceEntry> entries = List.of(
                new ClassNamespaceService.ClassNamespaceEntry(
                    nsOne, "nsOne", "", true, entryRm),
                new ClassNamespaceService.ClassNamespaceEntry(
                    nsTwo, "nsTwo", "", true, entryRm)
            );

            when(contextStoreHelper.getClassNamespaceEntries(extensionContext))
                .thenReturn(entries);

            Namespace result =
                service.resolveClassNamespace(extensionContext, "nsTwo");

            assertNotNull(result);
            assertEquals("ns-two", result.getMetadata().getName());
        }

        @Test
        @DisplayName("Returns null for unknown field name")
        void shouldReturnNullForUnknownFieldName() {
            Namespace ns = new NamespaceBuilder()
                .withNewMetadata().withName("ns-one").endMetadata()
                .build();
            KubeResourceManager entryRm = mock(KubeResourceManager.class);

            List<ClassNamespaceService.ClassNamespaceEntry> entries = List.of(
                new ClassNamespaceService.ClassNamespaceEntry(
                    ns, "nsOne", "", true, entryRm)
            );

            when(contextStoreHelper.getClassNamespaceEntries(extensionContext))
                .thenReturn(entries);

            Namespace result =
                service.resolveClassNamespace(extensionContext, "unknown");

            assertNull(result);
        }
    }
}
