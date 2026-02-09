/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.clients.KubeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@TestVisualSeparator
public class KubeResourceManagerMockTest {
    static KubeResourceManager kubeResourceManager = spy(KubeResourceManager.class);
    static KubernetesClient kubernetesClient = mock(KubernetesClient.class);
    static KubeClient kubeClient = mock(KubeClient.class);
    @SuppressWarnings("unchecked")
    static NamespaceableResource<Namespace> namespaceResource = mock(NamespaceableResource.class);

    @BeforeEach
    void setup() {
        when(kubeResourceManager.kubeClient()).thenReturn(kubeClient);
        when(kubeClient.getClient()).thenReturn(kubernetesClient);
        when(kubernetesClient.resource(any(Namespace.class))).thenReturn(namespaceResource);
        when(namespaceResource.delete()).then(invocationOnMock -> List.of());
    }

    @Test
    void testDeleteResourceWithWait() {
        AtomicBoolean deletionWaitWasCalled = new AtomicBoolean(false);
        Namespace myNamespace = new NamespaceBuilder().withNewMetadata().withName("my-namespace").endMetadata().build();

        doAnswer(invocation -> {
            deletionWaitWasCalled.set(true);
            return true;
        }).when(kubeResourceManager).decideDeleteWaitAsync(anyList(), anyBoolean(), any());

        kubeResourceManager.deleteResourceWithWait(myNamespace);

        assertTrue(deletionWaitWasCalled.get());
    }

    @Test
    void testDeleteResourceWithWaitAsync() {
        AtomicBoolean asyncWaitTriggered = new AtomicBoolean(false);

        Namespace myNamespace = new NamespaceBuilder().withNewMetadata().withName("my-namespace").endMetadata().build();

        doAnswer(invocation -> {
            boolean async = invocation.getArgument(1);

            if (async) {
                asyncWaitTriggered.set(true);
            }

            // Simulate the same logic if needed
            return null;
        }).when(kubeResourceManager).decideDeleteWaitAsync(anyList(), anyBoolean(), any());

        kubeResourceManager.deleteResourceAsyncWait(myNamespace);

        assertTrue(asyncWaitTriggered.get());
    }

    @Test
    void testDeleteResourceWithoutWait() {
        AtomicBoolean deletionWaitWasCalled = new AtomicBoolean(false);
        Namespace myNamespace = new NamespaceBuilder().withNewMetadata().withName("my-namespace").endMetadata().build();

        doAnswer(invocation -> {
            deletionWaitWasCalled.set(true);
            return true;
        }).when(kubeResourceManager).waitResourceCondition(any(), eq(ResourceCondition.deletion()));

        kubeResourceManager.deleteResourceWithoutWait(myNamespace);

        assertFalse(deletionWaitWasCalled.get());
    }

    @Test
    void testHandleAsyncDeletionBeingCalled() {
        AtomicBoolean handleAsyncDeletionCalled = new AtomicBoolean(false);
        Namespace myNamespace = new NamespaceBuilder().withNewMetadata()
            .withName("my-namespace").endMetadata().build();
        Namespace mySecondNamespace = new NamespaceBuilder().withNewMetadata()
            .withName("second-namespace").endMetadata().build();

        doAnswer(invocation -> {
            handleAsyncDeletionCalled.set(true);
            return true;
        }).when(kubeResourceManager).handleAsyncDeletion(any());

        kubeResourceManager.deleteResourceAsyncWait(myNamespace, mySecondNamespace);

        assertTrue(handleAsyncDeletionCalled.get());
    }

    @Test
    void testHandleAsyncDeletionThrowsException() {
        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            throw new RuntimeException("This is test exception");
        });

        RuntimeException runtimeException = assertThrows(RuntimeException.class,
            () -> kubeResourceManager.handleAsyncDeletion(List.of(cf)));
        assertTrue(runtimeException.getMessage().contains("This is test exception"));
    }
}
