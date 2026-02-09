/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.skodjob.kubetest4j.CollectorConstants;
import io.skodjob.kubetest4j.LogCollector;
import io.skodjob.kubetest4j.LogCollectorBuilder;
import io.skodjob.kubetest4j.LogCollectorUtils;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class LogCollectorIT extends AbstractIT {
    private final String folderRoot = "/tmp/log-collector-examples";
    private final String[] resourcesToBeCollected = new String[] {"secret", "configmap", "deployment"};
    private final LogCollector logCollector = new LogCollectorBuilder()
        .withNamespacedResources(resourcesToBeCollected)
        .withClusterWideResources("nodes")
        .withRootFolderPath(folderRoot)
        .build();

    /**
     * Test checks that LogCollector is able to collect from multiple namespaces, where in each are different resources
     * that are specified by the user.
     */
    @Test
    void testCollectFromMultipleNamespacesWithDifferentResources() {
        String folderPath = "test1";
        String fullFolderPath = LogCollectorUtils.getFolderPath(folderRoot, folderPath);

        String namespaceName1 = "my-namespace1";
        String namespaceName2 = "my-namespace2";

        String configMapName = "my-config-map";
        String secretName = "my-secret";
        String deploymentName = "my-deployment";

        int deploymentReplicas = 2;

        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder()
                .editOrNewMetadata()
                    .withName(namespaceName1)
                .endMetadata()
                .build(),
            new NamespaceBuilder()
                .editOrNewMetadata()
                    .withName(namespaceName2)
                .endMetadata()
                .build()
        );

        KubeResourceManager.get().createResourceWithWait(
            new DeploymentBuilder()
                .editOrNewMetadata()
                    .withNamespace(namespaceName1)
                    .withName(deploymentName)
                .endMetadata()
                .editOrNewSpec()
                    .withReplicas(deploymentReplicas)
                    .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(Map.of("app", "nginx"))
                        .build())
                    .editOrNewTemplate()
                        .editOrNewMetadata()
                            .withLabels(Map.of("app", "nginx"))
                        .endMetadata()
                        .editOrNewSpec()
                            .addToContainers(new ContainerBuilder()
                                .withName("nginx")
                                .withImage("quay.io/nginx/nginx-unprivileged")
                                .withPorts(new ContainerPortBuilder()
                                    .withContainerPort(80)
                                    .build())
                                .addAllToVolumeMounts(List.of(
                                    new VolumeMountBuilder()
                                        .withName("nginx-dir")
                                        .withMountPath("/etc/nginx/conf.d/")
                                        .build(),
                                    new VolumeMountBuilder()
                                        .withName("nginx-empty")
                                        .withMountPath("/var/cache/nginx/client_temp")
                                        .build(),
                                    new VolumeMountBuilder()
                                        .withName("nginx-run")
                                        .withMountPath("/var/run/")
                                        .build()
                                ))
                                .build())
                .addToVolumes(
                    new VolumeBuilder()
                        .withName("nginx-dir")
                        .withNewEmptyDir()
                        .endEmptyDir()
                        .build(),
                    new VolumeBuilder()
                        .withName("nginx-empty")
                        .withNewEmptyDir()
                        .endEmptyDir()
                        .build(),
                    new VolumeBuilder()
                        .withName("nginx-run")
                        .withNewEmptyDir()
                        .endEmptyDir()
                        .build())
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build(),
            new SecretBuilder()
                .editOrNewMetadata()
                    .withNamespace(namespaceName1)
                    .withName(secretName)
                .endMetadata()
                .withData(Map.of("data", Base64.getEncoder().encodeToString(
                        "top-secret-password-for-ultimate-access-to-everything".getBytes(StandardCharsets.UTF_8))))
                .build(),
            new ConfigMapBuilder()
                .editOrNewMetadata()
                    .withNamespace(namespaceName2)
                    .withName(configMapName)
                .endMetadata()
                .withData(Map.of("config", "configuration-of-my-ultimate-machine"))
                .build()
        );

        logCollector.collectFromNamespacesToFolder(List.of(namespaceName1, namespaceName2), folderPath);
        logCollector.collectClusterWideResourcesToFolder(folderPath);
        logCollector.collectClusterWideResourcesToFolder(false, folderPath);

        List<String> podNames = KubeResourceManager.get().kubeClient()
            .listPods(namespaceName1).stream().map(pod -> pod.getMetadata().getName()).toList();
        List<String> secretNames = KubeResourceManager.get().kubeClient()
            .getClient().secrets().inNamespace(namespaceName1).list().getItems().stream()
            .map(secret -> secret.getMetadata().getName()).toList();

        File rootFolder = Paths.get(fullFolderPath).toFile();

        assertNotNull(rootFolder);

        File[] namespaceFolders = rootFolder.listFiles();

        assertNotNull(namespaceFolders);
        assertEquals(3, namespaceFolders.length);

        assertTrue(rootFolder.toPath()
                .resolve(CollectorConstants.CLUSTER_WIDE_FOLDER).resolve("nodes.yaml").toFile().exists());

        Arrays.stream(namespaceFolders).filter(File::isDirectory).forEach(namespaceFolder -> {
            List<File> namespaceFolderFiles = Arrays.asList(Objects.requireNonNull(namespaceFolder.listFiles()));
            List<String> namespaceFolderFileNames = namespaceFolderFiles.stream().map(File::getName).toList();

            if (namespaceFolder.getName().equals(namespaceName1)) {
                assertTrue(namespaceFolderFileNames.contains("events.log"));
                assertTrue(namespaceFolderFileNames.contains("pod"));

                File podFolder = namespaceFolderFiles.stream()
                    .filter(file -> file.getName().equals("pod")).findFirst().get();
                List<File> podFolderFiles = Arrays.asList(Objects.requireNonNull(podFolder.listFiles()));
                List<String> podFolderFileNames = podFolderFiles.stream().map(File::getName).toList();

                assertEquals(4, podFolderFiles.size());
                podNames.forEach(podName -> {
                    assertTrue(podFolderFileNames.contains(LogCollectorUtils.getLogFileNameForPodDescription(podName)));
                    assertTrue(podFolderFileNames.contains(
                        LogCollectorUtils.getLogFileNameForPodContainer(podName, "nginx")));
                });

                assertTrue(namespaceFolderFileNames.contains("deployment"));

                File depFolder = namespaceFolderFiles.stream()
                    .filter(file -> file.getName().equals("deployment")).findFirst().get();
                List<File> depFolderFiles = Arrays.asList(Objects.requireNonNull(depFolder.listFiles()));
                List<String> depFolderFileNames = depFolderFiles.stream().map(File::getName).toList();

                assertEquals(1, depFolderFileNames.size());
                assertEquals(depFolderFileNames.get(0), LogCollectorUtils.getYamlFileNameForResource(deploymentName));

                assertTrue(namespaceFolderFileNames.contains("secret"));

                File secretFolder = namespaceFolderFiles.stream()
                    .filter(file -> file.getName().equals("secret")).findFirst().get();
                List<File> secretFolderFiles = Arrays.asList(Objects.requireNonNull(secretFolder.listFiles()));
                List<String> secretFolderFileNames = secretFolderFiles.stream().map(File::getName).toList();

                assertTrue(secretFolderFileNames.contains(LogCollectorUtils.getYamlFileNameForResource(secretName)));
                secretNames.forEach(
                    secret -> assertTrue(secretFolderFileNames
                            .contains(LogCollectorUtils.getYamlFileNameForResource(secret)))
                );
            } else if (namespaceFolder.getName().equals(namespaceName2)) {
                assertFalse(namespaceFolderFileNames.contains("pod"));
                assertFalse(namespaceFolderFileNames.contains("deployment"));
                assertTrue(namespaceFolderFileNames.contains("configmap"));

                File configMapFolder = namespaceFolderFiles.stream()
                    .filter(file -> file.getName().equals("configmap")).findFirst().get();
                List<File> configMapFolderFiles = Arrays.asList(Objects.requireNonNull(configMapFolder.listFiles()));
                List<String> configMapFolderFileNames = configMapFolderFiles.stream().map(File::getName).toList();

                assertTrue(configMapFolderFileNames
                        .contains(LogCollectorUtils.getYamlFileNameForResource(configMapName)));
            } else if (namespaceFolder.getName().equals(CollectorConstants.CLUSTER_WIDE_FOLDER)) {
                int countOfNodes = KubeResourceManager.get().kubeClient().getClient().nodes().list().getItems().size();
                int countOfFiles = Objects.requireNonNull(
                    Arrays.stream(Objects.requireNonNull(namespaceFolder.listFiles()))
                        .filter(file -> file.getName().equals("nodes")).toList()
                        .stream().findFirst().get().listFiles()).length;
                assertEquals(countOfNodes, countOfFiles);
            }
        });
    }

    @AfterEach
    void cleanUpFiles() throws IOException {
        // remove the directories created by the tests to clean-up everything
        FileUtils.deleteDirectory(Paths.get(folderRoot).toFile());
    }
}
