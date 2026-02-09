/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ResourceUtilsTest {
    static KubeResourceManager kubeResourceManager = mock(KubeResourceManager.class);
    static KubernetesClient kubernetesClient = mock(KubernetesClient.class);
    static KubeClient kubeClient = mock(KubeClient.class);

    @BeforeAll
    static void setup() {
        when(kubeResourceManager.kubeClient()).thenReturn(kubeClient);
        when(kubeClient.getClient()).thenReturn(kubernetesClient);
    }

    @Test
    void testGetGenericResourceReturnSpecific() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            MixedOperation<GenericKubernetesResource,
                GenericKubernetesResourceList,
                Resource<GenericKubernetesResource>> mixedOperation = mock(MixedOperation.class);
            NonNamespaceOperation<
                GenericKubernetesResource,
                GenericKubernetesResourceList,
                Resource<GenericKubernetesResource>> nonNamespaceOperation = mock(NonNamespaceOperation.class);
            Resource<GenericKubernetesResource> resource = mock(Resource.class);

            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);
            GenericKubernetesResource expectedReturnedResource = new GenericKubernetesResourceBuilder()
                .withKind("Secret")
                .withApiVersion("v1")
                .withNewMetadata()
                    .withName("my-secret")
                    .withNamespace("my-namespace")
                .endMetadata()
                .withAdditionalProperties(Map.of("data", Map.of("my-key", "my-value")))
                .build();

            Secret expectedSecret = new SecretBuilder()
                .withNewMetadata()
                    .withName("my-secret")
                    .withNamespace("my-namespace")
                .endMetadata()
                .withData(Map.of("my-key", "my-value"))
                .build();

            when(kubernetesClient.genericKubernetesResources("v1", "Secret")).thenReturn(mixedOperation);
            when(mixedOperation.inNamespace(anyString())).thenReturn(nonNamespaceOperation);
            when(nonNamespaceOperation.withName(anyString())).thenReturn(resource);
            when(resource.get()).thenReturn(expectedReturnedResource);

            Secret secret = ResourceUtils.getGenericResourceReturnSpecific(
                "my-namespace",
                "my-secret",
                "v1",
                "Secret",
                Secret.class
            );

            assertEquals(secret, expectedSecret);
        }
    }
}
