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

import io.skodjob.kubetest4j.wait.WaitException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
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
        kubeResourceManager.setResourceTypes();
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

        when(namespaceResource.get())
            .thenThrow(new CompletionException(
                new RuntimeException("wrapped error")));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> kubeResourceManager.replaceResourceWithRetries(ns, editor, 0),
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
    void testCollectAsyncErrorsWaitsForAllFutures() {
        KubeResourceManager realManager = KubeResourceManager.get();

        AtomicInteger completedCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> waiters = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            waiters.add(CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                completedCount.incrementAndGet();
            }));
        }

        List<Exception> errors = new ArrayList<>();
        realManager.collectAsyncErrors(waiters, errors);

        assertEquals(5, completedCount.get(),
            "All futures must complete before barrier returns");
        assertTrue(errors.isEmpty(), "No errors expected");
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

    @Test
    void testBatchLIFODeletionOrder() {
        List<String> deletionOrder = Collections.synchronizedList(new ArrayList<>());

        Namespace ns1 = new NamespaceBuilder().withNewMetadata()
            .withName("batch1-ns").endMetadata().build();
        Namespace ns2 = new NamespaceBuilder().withNewMetadata()
            .withName("batch2-ns").endMetadata().build();

        kubeResourceManager.pushToStack(new ResourceItem<>(() ->
            deletionOrder.add("batch1-ns"), ns1));

        kubeResourceManager.pushToStack(new ResourceItem<>(() ->
            deletionOrder.add("batch2-ns"), ns2));

        kubeResourceManager.deleteResources(false);

        assertEquals(2, deletionOrder.size(), "Both items should be deleted");
        assertEquals("batch2-ns", deletionOrder.get(0),
            "Last pushed batch should be deleted first (LIFO)");
        assertEquals("batch1-ns", deletionOrder.get(1),
            "First pushed batch should be deleted last (LIFO)");
    }

    @Test
    void testExplicitBatchGroupsResources() {
        List<String> deletionOrder = Collections.synchronizedList(new ArrayList<>());

        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("infra-ns").endMetadata().build();

        kubeResourceManager.pushToStack(new ResourceItem<>(() ->
            deletionOrder.add("infra-ns"), ns));

        kubeResourceManager.startBatch();
        Namespace app1 = new NamespaceBuilder().withNewMetadata()
            .withName("app-1").endMetadata().build();
        Namespace app2 = new NamespaceBuilder().withNewMetadata()
            .withName("app-2").endMetadata().build();
        kubeResourceManager.pushToStack(new ResourceItem<>(() ->
            deletionOrder.add("app-1"), app1));
        kubeResourceManager.pushToStack(new ResourceItem<>(() ->
            deletionOrder.add("app-2"), app2));
        kubeResourceManager.endBatch();

        kubeResourceManager.deleteResources(false);

        assertEquals(3, deletionOrder.size(), "All items should be deleted");
        // The explicit batch (app-1, app-2) was pushed last, so deleted first
        // Within the batch, sequential deletion preserves order
        assertTrue(deletionOrder.indexOf("app-1") < deletionOrder.indexOf("infra-ns"),
            "Explicit batch items should be deleted before earlier single-item batch");
        assertTrue(deletionOrder.indexOf("app-2") < deletionOrder.indexOf("infra-ns"),
            "Explicit batch items should be deleted before earlier single-item batch");
    }

    @Test
    void testOpenBatchAutoCloseable() throws Exception {
        List<String> deletionOrder = Collections.synchronizedList(new ArrayList<>());

        kubeResourceManager.pushToStack(new ResourceItem<>(() ->
            deletionOrder.add("before-batch"),
            new NamespaceBuilder().withNewMetadata().withName("before").endMetadata().build()));

        try (AutoCloseable ignored = kubeResourceManager.openBatch()) {
            kubeResourceManager.pushToStack(new ResourceItem<>(() ->
                deletionOrder.add("in-batch-1"),
                new NamespaceBuilder().withNewMetadata().withName("in1").endMetadata().build()));
            kubeResourceManager.pushToStack(new ResourceItem<>(() ->
                deletionOrder.add("in-batch-2"),
                new NamespaceBuilder().withNewMetadata().withName("in2").endMetadata().build()));
        }

        kubeResourceManager.deleteResources(false);

        assertEquals(3, deletionOrder.size());
        assertTrue(deletionOrder.indexOf("in-batch-1") < deletionOrder.indexOf("before-batch"),
            "Batch items should be deleted before items pushed earlier");
        assertTrue(deletionOrder.indexOf("in-batch-2") < deletionOrder.indexOf("before-batch"),
            "Batch items should be deleted before items pushed earlier");
    }

    @Test
    void testNoBatchOverlapWithAsyncDeletion() {
        AtomicInteger activeBatches = new AtomicInteger(0);
        AtomicInteger maxConcurrentBatches = new AtomicInteger(0);
        List<String> events = Collections.synchronizedList(new ArrayList<>());

        kubeResourceManager.pushToStack(new ResourceItem<>(() -> {
            int running = activeBatches.incrementAndGet();
            maxConcurrentBatches.updateAndGet(curr -> Math.max(curr, running));
            events.add("batch0-start");
            Thread.sleep(200);
            events.add("batch0-end");
            activeBatches.decrementAndGet();
        }, new NamespaceBuilder().withNewMetadata()
            .withName("b0-ns").endMetadata().build()));

        kubeResourceManager.startBatch();
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> {
            int running = activeBatches.incrementAndGet();
            maxConcurrentBatches.updateAndGet(curr -> Math.max(curr, running));
            events.add("batch1-start");
            Thread.sleep(200);
            events.add("batch1-end");
            activeBatches.decrementAndGet();
        }, new NamespaceBuilder().withNewMetadata()
            .withName("b1-ns").endMetadata().build()));
        kubeResourceManager.endBatch();

        kubeResourceManager.deleteResources(true);

        int b1End = events.indexOf("batch1-end");
        int b0Start = events.indexOf("batch0-start");
        assertTrue(b1End >= 0 && b0Start >= 0,
            "Both batches should have executed. Events: " + events);
        assertTrue(b1End < b0Start,
            "Batch 1 must complete before batch 0 starts (LIFO, no overlap). Events: " + events);
        assertEquals(1, maxConcurrentBatches.get(),
            "Only one batch should be active at a time");
    }

    @Test
    void testStartBatchThrowsWhenAlreadyOpen() {
        kubeResourceManager.startBatch();
        try {
            assertThrows(IllegalStateException.class,
                () -> kubeResourceManager.startBatch(),
                "Should throw when batch is already open");
        } finally {
            kubeResourceManager.endBatch();
        }
    }

    @Test
    void testEndBatchThrowsWhenNoneOpen() {
        assertThrows(IllegalStateException.class,
            () -> kubeResourceManager.endBatch(),
            "Should throw when no batch is open");
    }

    @Test
    void testAsyncBatchDeletionDeletesWithinBatchConcurrently() {
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(2);

        kubeResourceManager.startBatch();
        Namespace ns1 = new NamespaceBuilder().withNewMetadata()
            .withName("concurrent-1").endMetadata().build();
        Namespace ns2 = new NamespaceBuilder().withNewMetadata()
            .withName("concurrent-2").endMetadata().build();
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> {
            int c = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(curr -> Math.max(curr, c));
            barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            concurrentCount.decrementAndGet();
        }, ns1));
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> {
            int c = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(curr -> Math.max(curr, c));
            barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            concurrentCount.decrementAndGet();
        }, ns2));
        kubeResourceManager.endBatch();

        kubeResourceManager.deleteResources(true);

        assertEquals(2, maxConcurrent.get(),
            "Both items in the batch should run concurrently");
    }

    @Test
    void testGetCurrentResourcesFlattensBatches() {
        Namespace ns1 = new NamespaceBuilder().withNewMetadata()
            .withName("res-1").endMetadata().build();
        Namespace ns2 = new NamespaceBuilder().withNewMetadata()
            .withName("res-2").endMetadata().build();
        Namespace ns3 = new NamespaceBuilder().withNewMetadata()
            .withName("res-3").endMetadata().build();

        kubeResourceManager.pushToStack(new ResourceItem<>(() -> { }, ns1));

        kubeResourceManager.startBatch();
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> { }, ns2));
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> { }, ns3));
        kubeResourceManager.endBatch();

        List<HasMetadata> resources = kubeResourceManager.getCurrentResources();
        assertEquals(3, resources.size(), "Should flatten all batches into one list");

        kubeResourceManager.deleteResources(false);
    }

    @Test
    void testClearCurrentBatchFlushesToDeque() {
        List<String> deletionOrder = Collections.synchronizedList(new ArrayList<>());

        kubeResourceManager.pushToStack(new ResourceItem<>(() ->
            deletionOrder.add("before"),
            new NamespaceBuilder().withNewMetadata().withName("before").endMetadata().build()));

        // Simulate a leaked batch (startBatch without endBatch)
        kubeResourceManager.startBatch();
        kubeResourceManager.pushToStack(new ResourceItem<>(() ->
            deletionOrder.add("leaked"),
            new NamespaceBuilder().withNewMetadata().withName("leaked").endMetadata().build()));
        // endBatch() NOT called — simulates test failure

        // clearCurrentBatch should flush items to deque, not discard
        kubeResourceManager.clearCurrentBatch();

        // deleteResources should still find and delete the leaked item
        kubeResourceManager.deleteResources(false);

        assertEquals(2, deletionOrder.size(), "Both items should be deleted");
        assertTrue(deletionOrder.contains("leaked"),
            "Leaked batch items should be flushed and deleted");
    }

    @Test
    void testClearCurrentBatchNoopWhenNoBatch() {
        // Should not throw when no batch is open
        assertDoesNotThrow(() -> kubeResourceManager.clearCurrentBatch());
    }

    @Test
    void testWaitForDeletionReturnsWhenResourceIsNull() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("gone-ns").endMetadata().build();

        when(namespaceResource.get()).thenReturn(null);

        ResourceDeleteService deleteService =
            new ResourceDeleteService(kubeResourceManager);
        assertDoesNotThrow(
            () -> deleteService.waitForDeletionWithRetry(ns, 5000, 100, 200),
            "Should return immediately when resource is already gone");
    }

    @Test
    void testWaitForDeletionReturnsAfterResourceDisappears() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("slow-ns").endMetadata().build();

        when(namespaceResource.get())
            .thenReturn(ns)
            .thenReturn(ns)
            .thenReturn(null);

        ResourceDeleteService deleteService =
            new ResourceDeleteService(kubeResourceManager);
        assertDoesNotThrow(
            () -> deleteService.waitForDeletionWithRetry(ns, 5000, 50, 5000),
            "Should return once resource disappears after polling");
    }

    @Test
    void testWaitForDeletionReissuesDeleteWhenResourceReappears() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("recreated-ns").endMetadata().build();

        AtomicInteger deleteCount = new AtomicInteger(0);
        when(namespaceResource.get())
            .thenReturn(ns)
            .thenReturn(ns)
            .thenReturn(ns)
            .thenReturn(null);
        when(namespaceResource.delete()).thenAnswer(invocation -> {
            deleteCount.incrementAndGet();
            return List.of();
        });

        ResourceDeleteService deleteService =
            new ResourceDeleteService(kubeResourceManager);
        // retryInterval=0 forces re-delete on every poll
        deleteService.waitForDeletionWithRetry(ns, 5000, 50, 0);

        assertTrue(deleteCount.get() >= 2,
            "Should re-issue DELETE when resource persists. "
                + "Actual delete count: " + deleteCount.get());
    }

    @Test
    void testWaitForDeletionTimesOut() {
        Namespace ns = new NamespaceBuilder().withNewMetadata()
            .withName("stuck-ns").endMetadata().build();

        when(namespaceResource.get()).thenReturn(ns);

        ResourceDeleteService deleteService =
            new ResourceDeleteService(kubeResourceManager);
        assertThrows(WaitException.class,
            () -> deleteService.waitForDeletionWithRetry(ns, 500, 50, 5000),
            "Should throw WaitException when resource never disappears");
    }

    @Test
    void testRemoveFromStackAcrossBatches() {
        Namespace ns1 = new NamespaceBuilder().withNewMetadata()
            .withName("rm-1").endMetadata().build();
        Namespace ns2 = new NamespaceBuilder().withNewMetadata()
            .withName("rm-2").endMetadata().build();

        kubeResourceManager.pushToStack(new ResourceItem<>(() -> { }, ns1));
        kubeResourceManager.pushToStack(new ResourceItem<>(() -> { }, ns2));

        kubeResourceManager.removeFromStack(ns1);

        List<HasMetadata> resources = kubeResourceManager.getCurrentResources();
        assertEquals(1, resources.size());
        assertEquals("rm-2", resources.get(0).getMetadata().getName());

        kubeResourceManager.deleteResources(false);
    }
}
