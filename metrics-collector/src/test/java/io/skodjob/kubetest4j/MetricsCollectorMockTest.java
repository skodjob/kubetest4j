/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.clients.cmdClient.Kubectl;
import io.skodjob.kubetest4j.exceptions.IncompleteMetricsException;
import io.skodjob.kubetest4j.exceptions.MetricsCollectionException;
import io.skodjob.kubetest4j.exceptions.NoPodsFoundException;
import io.skodjob.kubetest4j.executor.Exec;
import io.skodjob.kubetest4j.metrics.Gauge;
import io.skodjob.kubetest4j.metrics.Metric;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
final class MetricsCollectorMockTest {

    private KubernetesClient mockClient;
    private KubeCmdClient<Kubectl> mockCmdClient;

    private MetricsCollector metricsCollector;

    @BeforeEach
    void setup() {
        mockClient = mock(KubernetesClient.class);
        mockCmdClient = mock(KubeCmdClient.class);

        // we need to use "kubectl" instead of toString because `"Mock for KubeCmdClient, hashCode: 1774795940"`
        when(mockCmdClient.inNamespace(anyString())).thenReturn(new Kubectl());

        MetricsCollector.Builder builder = new MetricsCollector.Builder()
            .withNamespaceName("test-namespace")
            .withScraperPodName("scraper-pod")
            .withComponent(new MetricsComponent() {
                public int getDefaultMetricsPort() {
                    return 8080;
                }

                public String getDefaultMetricsPath() {
                    return "/metrics";
                }

                public LabelSelector getLabelSelector() {
                    return new LabelSelector();
                }
            });

        this.metricsCollector = builder.build();

        // Dependency Injection of external components
        this.metricsCollector.setKubeClient(mockClient);
        this.metricsCollector.setKubeCmdClient(mockCmdClient);
    }

    @Test
    void testCollectMetricsFromPods() throws InterruptedException,
        ExecutionException, IOException, MetricsCollectionException {
        // Create mock operations for Kubernetes client
        MixedOperation<Pod, PodList, PodResource> podsOperation = mock(MixedOperation.class);
        when(mockClient.pods()).thenReturn(podsOperation);

        // Mock the inNamespace and withLabelSelector behavior
        MixedOperation<Pod, PodList, PodResource> namespaceOperation = mock(MixedOperation.class);
        when(podsOperation.inNamespace(anyString())).thenReturn(namespaceOperation);
        when(namespaceOperation.withLabelSelector((LabelSelector) any())).thenReturn(namespaceOperation);

        Pod pod = new PodBuilder()
            .withNewMetadata()
                .withName("pod1")
                .withNamespace("test-namespace")
            .endMetadata()
            .withNewStatus()
                .withPodIP("192.168.1.1")
            .endStatus()
            .build();

        PodList podList = new PodList();
        podList.setItems(List.of(pod));

        when(namespaceOperation.list()).thenReturn(podList);

        Exec mockExec = mock(Exec.class);
        // Ensure all arguments use matchers if any argument uses a matcher.
        when(mockExec.execute(eq(null), anyList(), eq(null), eq(20000L))).thenReturn(0);
        when(mockExec.out()).thenReturn("metric1 100\nmetric2 200");

        this.metricsCollector.setExec(mockExec);
        this.metricsCollector.collectMetricsFromPods(5000);

        // Verify interactions and assertions
        verify(mockExec, times(1))
            .execute(eq(null), anyList(), eq(null), eq(20000L));
        assertNotNull(this.metricsCollector.getCollectedData());
    }

    @Test
    void testCollectMetricsWhenNoPodsAvailable() throws Exception {
        // Create mock operations for Kubernetes client
        MixedOperation<Pod, PodList, PodResource> podsOperation = mock(MixedOperation.class);
        when(mockClient.pods()).thenReturn(podsOperation);

        // Mock the inNamespace and withLabelSelector behavior
        MixedOperation<Pod, PodList, PodResource> namespaceOperation = mock(MixedOperation.class);
        when(podsOperation.inNamespace(anyString())).thenReturn(namespaceOperation);
        when(namespaceOperation.withLabelSelector((LabelSelector) any())).thenReturn(namespaceOperation);

        // Mock Kubernetes client operations
        when(namespaceOperation.list())
            .thenReturn(new PodList());  // Empty pod list

        // This would throw WaitException because we do not have any data scraped
        assertThrows(NoPodsFoundException.class, () -> this.metricsCollector.collectMetricsFromPods(2000));

        // Verify interactions
        verify(mock(Exec.class), times(0))
            .execute(eq(null), anyList(), eq(null), eq(20000L));
        assertTrue(this.metricsCollector.getCollectedData().isEmpty());
    }

    @Test
    void testCollectMetricsWithIncompleteData() throws IOException, ExecutionException, InterruptedException {
        // Create mock operations for Kubernetes client
        MixedOperation<Pod, PodList, PodResource> podsOperation = mock(MixedOperation.class);
        when(mockClient.pods()).thenReturn(podsOperation);

        // Mock the inNamespace and withLabelSelector behavior
        MixedOperation<Pod, PodList, PodResource> namespaceOperation = mock(MixedOperation.class);
        when(podsOperation.inNamespace(anyString())).thenReturn(namespaceOperation);
        when(namespaceOperation.withLabelSelector((LabelSelector) any())).thenReturn(namespaceOperation);

        // Create a pod with valid metadata but empty metrics data
        Pod pod = new PodBuilder()
            .withNewMetadata()
                .withName("pod1")
                .withNamespace("test-namespace")
            .endMetadata()
            .withNewStatus()
                .withPodIP("192.168.1.100")
            .endStatus()
            .build();
        PodList podList = new PodList();
        podList.setItems(List.of(pod));
        when(namespaceOperation.list()).thenReturn(podList);

        Exec mockExec = mock(Exec.class);
        // Mock exec behavior to return empty metrics data
        when(mockExec.execute(any(), any(), any(), anyLong())).thenReturn(0);
        when(mockExec.out()).thenReturn("");  // Empty metrics output

        // Set the mock exec in metrics collector
        metricsCollector.setExec(mockExec);

        // Execute and expect an IncompleteMetricsException
        assertThrows(IncompleteMetricsException.class, () -> metricsCollector.collectMetricsFromPods(2000));

        // Verify interactions
        // at least once interactions, because we use waitUntil with 1s
        verify(mockExec, atLeastOnce()).execute(any(), any(), any(), anyLong());
        assertTrue(metricsCollector.getCollectedData().isEmpty());
    }

    @Test
    void testCollectMetricsWithExecutionError() throws IOException, ExecutionException, InterruptedException {
        // Create mock operations for Kubernetes client
        MixedOperation<Pod, PodList, PodResource> podsOperation = mock(MixedOperation.class);
        when(mockClient.pods()).thenReturn(podsOperation);

        // Mock the inNamespace and withLabelSelector behavior
        MixedOperation<Pod, PodList, PodResource> namespaceOperation = mock(MixedOperation.class);
        when(podsOperation.inNamespace(anyString())).thenReturn(namespaceOperation);
        when(namespaceOperation.withLabelSelector((LabelSelector) any())).thenReturn(namespaceOperation);

        // Create a pod with valid metadata
        Pod pod = new PodBuilder()
            .withNewMetadata()
                .withName("pod2")
                .withNamespace("test-namespace")
            .endMetadata()
            .withNewStatus()
                .withPodIP("192.168.1.101")
            .endStatus()
            .build();
        PodList podList = new PodList();
        podList.setItems(List.of(pod));
        when(namespaceOperation.list()).thenReturn(podList);

        Exec mockExec = mock(Exec.class);

        // Simulate an execution error
        when(mockExec.execute(any(), any(), any(), anyLong())).thenThrow(new IOException("Failed to execute command"));

        // Set the mock exec in metrics collector
        metricsCollector.setExec(mockExec);

        // Execute and expect a MetricsCollectionException
        assertThrows(MetricsCollectionException.class, () -> metricsCollector.collectMetricsFromPods(1000));

        // Verify interactions
        verify(mockExec, atLeastOnce()).execute(any(), any(), any(), anyLong());
        assertTrue(metricsCollector.getCollectedData().isEmpty());
    }

    @Test
    void testSetExecInjectsExecProperly() {
        Exec mockExec = mock(Exec.class);
        metricsCollector.setExec(mockExec);

        assertEquals(mockExec, metricsCollector.getExec());
    }

    @Test
    void testDeployAndDeleteScraperPodCalledDuringMetricsCollection() throws Exception {
        // Arrange
        Exec execMock = mock(Exec.class);
        when(execMock.execute(any(), any(), any(), anyLong())).thenReturn(0);
        when(execMock.out()).thenReturn("metric_name 100");

        MetricsCollector collector = spy(new MetricsCollector.Builder()
            .withNamespaceName("test-namespace")
            .withScraperPodName("scraper-pod")
            .withScraperPodImage("custom-image:latest")
            .withDeployScraperPod() // IMPORTANT to activate deploy logic
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build());

        collector.setExec(execMock);

        // Stub kubeCmdClient to avoid NPE during getKubeCmdClient().inNamespace(...)
        KubeCmdClient<Kubectl> mockCmdClient = mock(KubeCmdClient.class);
        when(mockCmdClient.inNamespace(anyString())).thenReturn(new Kubectl());
        collector.setKubeCmdClient(mockCmdClient);

        // Mock static access to KubeResourceManager
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            KubeResourceManager resourceManagerMock = mock(KubeResourceManager.class);
            mockedStatic.when(KubeResourceManager::get).thenReturn(resourceManagerMock);

            // Act
            collector.collectMetrics("192.168.0.1", "pod-name");

            // Assert: verify both deploy and delete were triggered
            verify(resourceManagerMock).createResourceWithWait(any(Pod.class));
            verify(resourceManagerMock).deleteResourceWithWait(any(Pod.class));
        }
    }

    @Test
    void testCollectMetricsHandlesMissingPodIp() throws Exception {
        var kubeClient = mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        var podsOp = mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var nsOp = mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);

        var pod = new io.fabric8.kubernetes.api.model.PodBuilder()
            .withNewMetadata().withName("pod1").endMetadata()
            .withNewStatus().withPodIP(null).endStatus()
            .build();

        var podList = new io.fabric8.kubernetes.api.model.PodList();
        podList.setItems(List.of(pod));

        when(kubeClient.pods()).thenReturn(podsOp);
        when(podsOp.inNamespace(anyString())).thenReturn(nsOp);
        when(nsOp.withLabelSelector((LabelSelector) any())).thenReturn(nsOp);
        when(nsOp.list()).thenReturn(podList);

        var exec = mock(io.skodjob.kubetest4j.executor.Exec.class);
        when(exec.execute(any(), any(), any(), anyLong())).thenReturn(0);
        when(exec.out()).thenReturn("metric_no_ip 1.0");

        MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("ns")
            .withScraperPodName("pod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build();

        collector.setKubeClient(kubeClient);
        collector.setExec(exec);
        collector.setKubeCmdClient(mock(io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient.class));

        assertThrows(MetricsCollectionException.class,
            () -> collector.collectMetricsFromPods(1000),
            "Expected error due to missing pod IP");
    }

    @Test
    void testCollectMetricsIPv6Wrapping() throws Exception {
        System.setProperty("io.skodjob.testframe.env.IP_FAMILY", "IPv6");

        var kubeClient = mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        var podsOp = mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var nsOp = mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);

        var pod = new io.fabric8.kubernetes.api.model.PodBuilder()
            .withNewMetadata().withName("pod-ipv6").endMetadata()
            .withNewStatus().withPodIP("fe80::1").endStatus()
            .build();

        var podList = new io.fabric8.kubernetes.api.model.PodList();
        podList.setItems(List.of(pod));

        when(kubeClient.pods()).thenReturn(podsOp);
        when(podsOp.inNamespace(anyString())).thenReturn(nsOp);
        when(nsOp.withLabelSelector((LabelSelector) any())).thenReturn(nsOp);
        when(nsOp.list()).thenReturn(podList);

        var exec = mock(io.skodjob.kubetest4j.executor.Exec.class);
        when(exec.execute(any(), any(), any(), anyLong())).thenReturn(0);
        when(exec.out()).thenReturn("metric_ipv6 123");

        MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("ns")
            .withScraperPodName("pod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build();

        var mockCmdClient = mock(KubeCmdClient.class);
        when(mockCmdClient.inNamespace(anyString())).thenReturn(mockCmdClient);

        collector.setKubeClient(kubeClient);
        collector.setExec(exec);
        collector.setKubeCmdClient(mockCmdClient);

        collector.collectMetricsFromPods(3000);
        assertFalse(collector.getCollectedData().isEmpty(), "Metrics should be collected for IPv6 wrapped address");
    }

    @Test
    void testBuilderUpdateCopiesAllFieldsCorrectly() {
        Exec mockExec = new Exec();
        Map<String, List<Metric>> dummyData = Map.of("pod1", List.of(new Gauge("name", Map.of(), "", 1.0)));

        MetricsCollector original = new MetricsCollector.Builder()
            .withNamespaceName("ns")
            .withScraperPodName("scraper-pod")
            .withScraperPodImage("image:v1")
            .withDeployScraperPod()
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .withCollectedData(dummyData)
            .withExec(mockExec)
            .build();

        // Inject a kubeClient and test setter coverage
        KubernetesClient kubeClient = mock(KubernetesClient.class);
        original.setKubeClient(kubeClient);
        assertNotNull(kubeClient); // trivially touches the injected client

        // Act: Copy to a new builder using updateBuilder()
        MetricsCollector.Builder copyBuilder = original.toBuilder();
        MetricsCollector copy = copyBuilder.build();

        // Assert: All values are preserved
        assertEquals("ns", copy.getNamespaceName());
        assertEquals("scraper-pod", copy.getScraperPodName());
        assertEquals("image:v1", copy.getScraperPodImage());
        assertTrue(copy.getDeployScraperPod());
        assertEquals(dummyData, copy.getCollectedData());
        assertEquals(mockExec, copy.getExec());
        assertNotNull(copy.getComponent());
    }

    @Test
    void testGetKubeClientFallbackThrowsWhenNoClientAvailable() {
        final MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("namespace")
            .withScraperPodName("scraperPod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build();

        // Mock KubeResourceManager.get() -> .kubeClient() -> .getClient()
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            final KubeResourceManager resourceManager = mock(KubeResourceManager.class);
            when(KubeResourceManager.get()).thenReturn(resourceManager);

            // Simulate kubeClient().getClient() returning null
            final KubeClient kubeCmdClient = mock(KubeClient.class);
            when(resourceManager.kubeClient()).thenReturn(kubeCmdClient);
            when(kubeCmdClient.getClient()).thenReturn(null);

            // Then when we try to collect metrics, it should trigger fallback and fail
            assertThrows(IllegalStateException.class, collector::collectMetricsFromPodsWithoutWait);
        }
    }

    @Test
    void testGetKubeCmdClientFallbackThrowsWhenNoClientAvailable() {
        final MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("namespace")
            .withScraperPodName("scraperPod")
            .withComponent(new MetricsCollectorTest.DummyMetricsComponent())
            .build();

        // Don't inject kubeCmdClient manually
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            final KubeResourceManager resourceManager = mock(KubeResourceManager.class);
            when(KubeResourceManager.get()).thenReturn(resourceManager);

            // Simulate kubeCmdClient() returns null
            when(resourceManager.kubeCmdClient()).thenReturn(null);

            assertThrows(IllegalStateException.class, collector::getKubeCmdClient);
        }
    }
}
