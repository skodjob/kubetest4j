/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
@TestVisualSeparator
class KubeClientTest {
    private KubernetesClient kubernetesClient;

    @Test
    void testClientFromUrlAndToken() {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());
        assertNotEquals("", cl.getKubeconfigPath());
    }

    @Test
    void testClientFromKubeconfig() {
        String kubeconfigPath = getClass().getClassLoader().getResource("testconfig").getPath();
        KubeClient cl = new KubeClient(kubeconfigPath);
        assertEquals(kubeconfigPath, cl.getKubeconfigPath());
    }

    @Test
    void testCreateDeleteResources() throws IOException {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());
        List<HasMetadata> res = cl.readResourcesFromFile(
            Path.of(getClass().getClassLoader().getResource("resources.yaml").getPath()));

        cl.createOrUpdate(res, r -> {
            r.getMetadata().getLabels().put("label", "value");
            return r;
        });

        assertTrue(cl.namespaceExists("test4"));

        cl.delete(res);

        assertFalse(cl.namespaceExists("test4"));
    }

    @Test
    void testGetOpenShiftClient() {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());
        try {
            OpenShiftClient ocClient = cl.getOpenShiftClient();
            // If no exception, adaptation was successful
            assertNotNull(ocClient);
        } catch (IllegalStateException e) {
            // Expected in mock environment - OpenShift adapter not available
            assertTrue(e.getMessage().contains("No adapter available"));
        }
    }

    @Test
    void testTestReconnect() {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());
        Config newConfig = new ConfigBuilder()
            .withMasterUrl(kubernetesClient.getConfiguration().getMasterUrl())
            .withOauthToken(kubernetesClient.getConfiguration().getOauthToken())
            .build();

        cl.testReconnect(newConfig);
        assertNotNull(cl.getClient());
    }

    @Test
    void testReadResourcesFromInputStream() throws IOException {
        String yamlContent = "apiVersion: v1\nkind: Namespace\nmetadata:\n  name: test-stream\n";
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());

        List<HasMetadata> resources = cl.readResourcesFromFile(inputStream);

        assertNotNull(resources);
        assertFalse(resources.isEmpty());
        assertEquals("Namespace", resources.get(0).getKind());
        assertEquals("test-stream", resources.get(0).getMetadata().getName());
    }

    @Test
    void testCreateUpdateDelete() {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());

        Namespace ns = new NamespaceBuilder()
            .withNewMetadata()
            .withName("test-create")
            .endMetadata()
            .build();

        List<HasMetadata> resources = Collections.singletonList(ns);

        // Test create
        cl.create(resources, r -> r);
        assertTrue(cl.namespaceExists("test-create"));

        // Test update
        cl.update(resources, r -> {
            r.getMetadata().getLabels().put("updated", "true");
            return r;
        });

        // Test delete
        cl.delete(resources);
        assertFalse(cl.namespaceExists("test-create"));
    }

    @Test
    void testListPods() {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());

        // Test listing pods in default namespace
        List<Pod> pods = cl.listPods("default");
        assertNotNull(pods);
    }

    @Test
    void testListPodsWithLabelSelector() {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());

        LabelSelector selector = new LabelSelectorBuilder()
            .withMatchLabels(Collections.singletonMap("app", "test"))
            .build();

        List<Pod> pods = cl.listPods("default", selector);
        assertNotNull(pods);
    }

    @Test
    void testListPodsByPrefixInName() {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());

        List<Pod> pods = cl.listPodsByPrefixInName("default", "test-");
        assertNotNull(pods);
    }

    @Test
    void testGetDeploymentNameByPrefix() {
        KubeClient cl = KubeClient.fromUrlAndToken(kubernetesClient.getConfiguration().getMasterUrl(),
            kubernetesClient.getConfiguration().getOauthToken());

        Deployment deployment = new DeploymentBuilder()
            .withNewMetadata()
            .withName("test-deployment-abc")
            .withNamespace("default")
            .endMetadata()
            .withNewSpec()
            .withReplicas(1)
            .withNewSelector()
            .withMatchLabels(Collections.singletonMap("app", "test"))
            .endSelector()
            .withNewTemplate()
            .withNewMetadata()
            .withLabels(Collections.singletonMap("app", "test"))
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .withImage("nginx")
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

        cl.create(Collections.singletonList(deployment), r -> r);

        String deploymentName = cl.getDeploymentNameByPrefix("default", "test-deployment");
        assertEquals("test-deployment-abc", deploymentName);

        String nonExistentDeployment = cl.getDeploymentNameByPrefix("default", "non-existent");
        assertNull(nonExistentDeployment);

        // Clean up
        cl.delete(Collections.singletonList(deployment));
    }
}
