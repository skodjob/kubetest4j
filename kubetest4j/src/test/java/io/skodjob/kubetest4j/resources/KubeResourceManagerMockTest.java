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
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

        // Create a fresh spy for each test
        kubeResourceManager = spy(KubeResourceManager.get());

        // Explicitly mock the kubeClient method to ensure it returns our mock
        doReturn(kubeClient).when(kubeResourceManager).kubeClient();
        when(kubeClient.getClient()).thenReturn(kubernetesClient);
        when(kubernetesClient.resource(any(Namespace.class))).thenReturn(namespaceResource);
        when(namespaceResource.delete()).thenReturn(List.of());

        // Mock the waitResourceCondition to avoid actual waiting
        doReturn(true).when(kubeResourceManager).waitResourceCondition(any(), any());
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
    void testHandleAsyncDeletionThrowsException() {
        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            throw new RuntimeException("This is test exception");
        });

        RuntimeException runtimeException = assertThrows(RuntimeException.class,
            () -> kubeResourceManager.handleAsyncDeletion(List.of(cf)),
            "handleAsyncDeletion should throw RuntimeException when future fails");
        assertTrue(runtimeException.getMessage().contains("This is test exception"),
            "Exception message should contain the original exception message");
    }
}
