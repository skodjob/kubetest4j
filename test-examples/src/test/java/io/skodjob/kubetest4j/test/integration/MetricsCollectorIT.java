/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.skodjob.kubetest4j.MetricsCollector;
import io.skodjob.kubetest4j.MetricsComponent;
import io.skodjob.kubetest4j.metrics.Metric;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class MetricsCollectorIT extends AbstractIT {

    @Test
    void testCollectMetrics() throws IOException {
        //Create deployment
        List<HasMetadata> resources = KubeResourceManager.get().kubeClient()
            .readResourcesFromFile(getClass().getClassLoader().getResourceAsStream("metrics-example.yaml"));

        KubeResourceManager.get().createResourceAsyncWait(resources.toArray(new HasMetadata[0]));

        // Check deployment is not null
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("metrics-test").get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().apps().deployments()
            .inNamespace("metrics-test").withName("prometheus-example").get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().apps().deployments()
            .inNamespace("metrics-test").withName("scraper-pod").get());

        // Create metrics collector
        MetricsCollector collector = new MetricsCollector.Builder()
            .withNamespaceName("metrics-test")
            .withScraperPodName(KubeResourceManager.get().kubeClient()
                .listPodsByPrefixInName("metrics-test", "scraper-pod").get(0)
                .getMetadata().getName())
            .withComponent(new MetricsComponent() {
                public int getDefaultMetricsPort() {
                    return 8080;
                }

                public String getDefaultMetricsPath() {
                    return "/metrics";
                }

                public LabelSelector getLabelSelector() {
                    return new LabelSelectorBuilder()
                        .withMatchLabels(Map.of("app", "prometheus-example-app"))
                        .build();
                }
            })
            .build();

        assertDoesNotThrow(() -> collector.collectMetricsFromPods(30000)); // timeout in milliseconds
        Map<String, List<Metric>> metrics = collector.getCollectedData();
        assertTrue(metrics.containsKey(KubeResourceManager.get().kubeClient()
            .listPodsByPrefixInName("metrics-test", "prometheus-example").get(0)
            .getMetadata().getName()));
    }

    @Test
    void testCollectMetricsWithAutoDeployedPod() throws IOException {
        //Create deployment
        List<HasMetadata> resources = KubeResourceManager.get().kubeClient()
            .readResourcesFromFile(getClass().getClassLoader().getResourceAsStream("metrics-example.yaml"))
            .stream().filter(resource -> !resource.getMetadata().getName().equals("scraper-pod")).toList();

        KubeResourceManager.get().createResourceWithWait(resources.toArray(new HasMetadata[0]));

        // Check deployment is not null
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("metrics-test").get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().apps().deployments()
            .inNamespace("metrics-test").withName("prometheus-example").get());

        // Create metrics collector
        MetricsCollector.Builder mcBuilder = new MetricsCollector.Builder()
            .withNamespaceName("metrics-test")
            .withDeployScraperPod()
            .withScraperPodName("test-scraper-pod")
            .withComponent(new MetricsComponent() {
                public int getDefaultMetricsPort() {
                    return 8080;
                }

                public String getDefaultMetricsPath() {
                    return "/metrics";
                }

                public LabelSelector getLabelSelector() {
                    return new LabelSelectorBuilder()
                        .withMatchLabels(Map.of("app", "prometheus-example-app"))
                        .build();
                }
            });

        MetricsCollector collector = mcBuilder.build();

        // Collect metrics
        assertDoesNotThrow(() -> collector.collectMetricsFromPods(30000)); // timeout in milliseconds
        Map<String, List<Metric>> metrics = collector.getCollectedData();
        assertTrue(metrics.containsKey(KubeResourceManager.get().kubeClient()
            .listPodsByPrefixInName("metrics-test", "prometheus-example").get(0)
            .getMetadata().getName()));

        // Update metrics collector with different image
        MetricsCollector collector2 = mcBuilder.withScraperPodImage("quay.io/curl/curl-base:latest").build();

        // Collect metrics
        assertDoesNotThrow(() -> collector2.collectMetricsFromPods(30000)); // timeout in milliseconds
        Map<String, List<Metric>> metrics2 = collector.getCollectedData();
        assertTrue(metrics2.containsKey(KubeResourceManager.get().kubeClient()
            .listPodsByPrefixInName("metrics-test", "prometheus-example").get(0)
            .getMetadata().getName()));
    }
}
