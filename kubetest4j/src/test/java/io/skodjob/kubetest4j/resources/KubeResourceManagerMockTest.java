/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.interfaces.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@TestVisualSeparator
public class KubeResourceManagerMockTest {
    KubeResourceManager kubeResourceManager;
    KubernetesClient kubernetesClient = mock(KubernetesClient.class);
    KubeClient kubeClient = mock(KubeClient.class);
    @SuppressWarnings("unchecked")
    NamespaceableResource<Namespace> namespaceResource = mock(NamespaceableResource.class);

    @BeforeEach
    void setup() {
        // Reset all mocks to ensure clean state
        Mockito.reset(kubernetesClient, kubeClient, namespaceResource);

        // Clear singleton instances to avoid stale clients from other test classes
        KubeResourceManager.clearInstances();

        // Create a fresh spy for each test
        kubeResourceManager = spy(KubeResourceManager.get());

        // Explicitly mock the kubeClient method to ensure it returns our mock
        doReturn(kubeClient).when(kubeResourceManager).kubeClient();
        when(kubeClient.getClient()).thenReturn(kubernetesClient);
        when(kubernetesClient.resource(any(Namespace.class))).thenReturn(namespaceResource);
        when(namespaceResource.delete()).thenReturn(List.of());

        // Mock the waitResourceCondition to avoid actual waiting
        doReturn(true).when(kubeResourceManager).waitResourceCondition(any(), any());

        // Mock the test context so pushToStack/deleteResources can resolve the display name
        ExtensionContext mockContext = mock(ExtensionContext.class);
        when(mockContext.getDisplayName()).thenReturn("mockTest");
        kubeResourceManager.setTestContext(mockContext);

        // Reset shared static state that may leak from other tests
        kubeResourceManager.setStoreYamlPath(null);
    }

    @Test
    void testDeleteResourceWithWait() {
        Namespace myNamespace = new NamespaceBuilder().withNewMetadata().withName("my-namespace").endMetadata().build();

        // Test that deleteResourceWithWait completes without throwing exceptions
        assertDoesNotThrow(() -> kubeResourceManager.deleteResourceWithWait(myNamespace),
            "deleteResourceWithWait should complete successfully");
    }

    @Test
    void testDeleteResourceWithWaitAsync() {
        Namespace myNamespace = new NamespaceBuilder().withNewMetadata().withName("my-namespace").endMetadata().build();

        // Test that deleteResourceAsyncWait completes without throwing exceptions
        assertDoesNotThrow(() -> kubeResourceManager.deleteResourceAsyncWait(myNamespace),
            "deleteResourceAsyncWait should complete successfully");
    }

    @Test
    void testDeleteResourceWithoutWait() {
        Namespace myNamespace = new NamespaceBuilder().withNewMetadata().withName("my-namespace").endMetadata().build();

        // Test that deleteResourceWithoutWait completes without throwing exceptions
        assertDoesNotThrow(() -> kubeResourceManager.deleteResourceWithoutWait(myNamespace),
            "deleteResourceWithoutWait should complete successfully");
    }

    @Test
    void testHandleAsyncDeletionBeingCalled() {
        Namespace myNamespace = new NamespaceBuilder().withNewMetadata()
            .withName("my-namespace").endMetadata().build();
        Namespace mySecondNamespace = new NamespaceBuilder().withNewMetadata()
            .withName("second-namespace").endMetadata().build();

        // Test that deleteResourceAsyncWait with multiple resources completes successfully
        assertDoesNotThrow(() -> kubeResourceManager.deleteResourceAsyncWait(myNamespace, mySecondNamespace),
            "deleteResourceAsyncWait with multiple resources should complete successfully");
    }

    @Test
    void testGetResourceTypesReturnsCurrentTypes() {
        // Given - save original types for restore
        ResourceType<?>[] originalTypes = kubeResourceManager.getResourceTypes();

        try {
            // When - set resource types to empty
            kubeResourceManager.setResourceTypes();

            // Then - should return empty array
            ResourceType<?>[] result = kubeResourceManager.getResourceTypes();
            assertEquals(0, result.length, "Should return empty array when no types set");
        } finally {
            // Restore original types
            kubeResourceManager.setResourceTypes(originalTypes);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetResourceTypesAfterSet() {
        // Given - save original types for restore
        ResourceType<?>[] originalTypes = kubeResourceManager.getResourceTypes();
        ResourceType<Namespace> mockType = mock(ResourceType.class);

        try {
            // When
            kubeResourceManager.setResourceTypes(mockType);

            // Then
            ResourceType<?>[] result = kubeResourceManager.getResourceTypes();
            assertEquals(1, result.length, "Should return 1 type after setting");
            assertSame(mockType, result[0], "Should return the mock type");
        } finally {
            // Restore original types
            kubeResourceManager.setResourceTypes(originalTypes);
        }
    }

    @Test
    void testCollectAsyncErrorsCollectsFailures() {
        KubeResourceManager realManager = KubeResourceManager.get();

        CompletableFuture<Void> cf = new CompletableFuture<>();
        cf.completeExceptionally(new RuntimeException("This is test exception"));

        List<Exception> errors = new ArrayList<>();
        assertDoesNotThrow(
            () -> realManager.collectAsyncErrors(List.of(cf), errors),
            "collectAsyncErrors should not throw");
        assertFalse(errors.isEmpty(),
            "Errors list should contain the failure");
    }

    @Test
    void testDeleteResourcesSyncContinuesAfterFailure() {
        Namespace ns1 = new NamespaceBuilder().withNewMetadata()
            .withName("ns-1").endMetadata().build();
        Namespace ns2 = new NamespaceBuilder().withNewMetadata()
            .withName("ns-2").endMetadata().build();

        AtomicInteger deleteCount = new AtomicInteger(0);

        kubeResourceManager.pushToStack(new ResourceItem<>(() -> {
            deleteCount.incrementAndGet();
        }, ns1));
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> {
            deleteCount.incrementAndGet();
            throw new RuntimeException("Simulated deletion failure");
        }, ns2));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> kubeResourceManager.deleteResources(false),
            "Should throw composite exception");
        assertEquals(1, ex.getSuppressed().length,
            "Should have exactly one suppressed exception");
        assertEquals(2, deleteCount.get(),
            "Both resources should have been attempted");
    }

    @Test
    void testDeleteResourcesWithNullResourceItem() {
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> { }));

        assertDoesNotThrow(() -> kubeResourceManager.deleteResources(false),
            "Should handle null-resource ResourceItem without NPE");
    }

    @Test
    void testDeleteResourcesWithNullMetadataResource() {
        HasMetadata resourceWithNullMeta = mock(HasMetadata.class);
        when(resourceWithNullMeta.getMetadata()).thenReturn(null);

        kubeResourceManager.pushToStack(new ResourceItem<>(() -> {
            throw new RuntimeException("Deletion failure");
        }, resourceWithNullMeta));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> kubeResourceManager.deleteResources(false),
            "Should throw composite exception");
        assertEquals(1, ex.getSuppressed().length,
            "Should have one suppressed exception");
    }

    @Test
    void testResourceDescriptionWithNullMetadata() {
        HasMetadata resource = mock(HasMetadata.class);
        when(resource.getMetadata()).thenReturn(null);
        ResourceItem<?> item = new ResourceItem<>(() -> { }, resource);

        assertEquals("<unknown resource>",
            KubeResourceManager.resourceDescription(item),
            "Should return unknown for null metadata");
    }

    @Test
    void testResourceDescriptionWithNullResource() {
        ResourceItem<?> item = new ResourceItem<>(() -> { });

        assertEquals("<unknown resource>",
            KubeResourceManager.resourceDescription(item),
            "Should return unknown for null resource");
    }

    @Test
    void testResourceDescriptionWithKindAndNamespace() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("my-ns").withNamespace("default").endMetadata().build();
        ResourceItem<?> item = new ResourceItem<>(() -> { }, ns);

        String desc = KubeResourceManager.resourceDescription(item);
        assertEquals("Namespace default/my-ns", desc,
            "Should include kind, namespace and name");
    }

    @Test
    void testResourceDescriptionClusterScoped() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("cluster-ns").endMetadata().build();
        ResourceItem<?> item = new ResourceItem<>(() -> { }, ns);

        String desc = KubeResourceManager.resourceDescription(item);
        assertEquals("Namespace cluster-ns", desc,
            "Should include kind and name without namespace");
    }

    @Test
    void testInvokeCreateCallbacksSafelyWithThrowingCallback() {
        AtomicInteger callbackCount = new AtomicInteger(0);
        Consumer<HasMetadata> throwingCallback = resource -> {
            callbackCount.incrementAndGet();
            throw new RuntimeException("Create callback failure");
        };
        Consumer<HasMetadata> normalCallback = resource -> callbackCount.incrementAndGet();

        kubeResourceManager.addCreateCallback(throwingCallback);
        kubeResourceManager.addCreateCallback(normalCallback);

        try {
            Namespace ns = new NamespaceBuilder().withNewMetadata()
                .withName("cb-ns").endMetadata().build();

            KubeResourceManager realManager = KubeResourceManager.get();
            assertDoesNotThrow(
                () -> realManager.invokeCreateCallbacksSafely(ns),
                "Should not throw despite failing callback");
            assertEquals(2, callbackCount.get(),
                "Both callbacks should have been invoked");
        } finally {
            KubeResourceManager.getCreateCallbacks().remove(throwingCallback);
            KubeResourceManager.getCreateCallbacks().remove(normalCallback);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testReplaceResourceWithRetriesCompletionException() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("replace-ns").endMetadata().build();
        Consumer<Namespace> editor = n -> { };

        doThrow(new CompletionException(new RuntimeException("wrapped error")))
            .when(kubeResourceManager).replaceResource(any(), any());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> kubeResourceManager.replaceResourceWithRetries(ns, editor, 1),
            "Should throw the unwrapped cause");
        assertEquals("wrapped error", ex.getMessage(),
            "Should unwrap CompletionException cause");
    }

    @Test
    void testCollectAsyncErrorsFallbackWhenNoIndividualErrors() {
        KubeResourceManager realManager = KubeResourceManager.get();

        CompletableFuture<Void> cf = new CompletableFuture<>();
        cf.completeExceptionally(new RuntimeException("async failure"));

        CompletableFuture<Void> successful = CompletableFuture.completedFuture(null);

        List<Exception> errors = new ArrayList<>();
        assertDoesNotThrow(
            () -> realManager.collectAsyncErrors(
                List.of(successful, cf), errors),
            "collectAsyncErrors should not throw");
        assertFalse(errors.isEmpty(),
            "Errors list should contain the failure");
    }

    @Test
    void testCollectAsyncErrorsHandlesInterruption() {
        KubeResourceManager realManager = KubeResourceManager.get();

        CompletableFuture<Void> neverCompletes = new CompletableFuture<>();
        List<Exception> errors = new ArrayList<>();

        try {
            Thread.currentThread().interrupt();
            assertDoesNotThrow(
                () -> realManager.collectAsyncErrors(
                    List.of(neverCompletes), errors),
                "collectAsyncErrors should not throw on interruption");
            assertFalse(errors.isEmpty(),
                "Should collect the interruption error");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testDeleteResourcesSyncHandlesInterruption() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("int-ns").endMetadata().build();

        CountDownLatch blockRunner = new CountDownLatch(1);
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> {
            blockRunner.await();
        }, ns));

        try {
            Thread.currentThread().interrupt();
            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> kubeResourceManager.deleteResources(false),
                "Should throw composite exception with interruption error");
            assertEquals(1, ex.getSuppressed().length,
                "Should have one suppressed exception");
        } finally {
            blockRunner.countDown();
            Thread.interrupted();
        }
    }
}
