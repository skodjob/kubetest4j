/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.skodjob.kubetest4j.KubeTestEnv;
import io.skodjob.kubetest4j.executor.Exec;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides functionality to interact with Kubernetes and OpenShift clusters.
 * This includes creating clients, reading resources from files, and managing kubeconfig for authentication.
 */
public class KubeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubeClient.class);

    private KubernetesClient client;

    /**
     * Path of kube‑config file (explicit or temp). May be {@code null}.
     */
    private String kubeconfigPath;

    /* --------------------------------------------------------------------- */
    /* Constructors / factories                                              */
    /* --------------------------------------------------------------------- */

    /**
     * Initializes the Kubernetes client with configuration derived from environment variables or default context.
     */
    public KubeClient() {
        Config cfg = Config.autoConfigure(null);
        this.client = new KubernetesClientBuilder().withConfig(cfg).build();
    }

    /**
     * Build the client from an explicit kube‑config file.
     *
     * @param kubeconfigPath path to kubeconfig YAML
     */
    public KubeClient(String kubeconfigPath) {
        this.kubeconfigPath = kubeconfigPath;
        Config cfg = Config.fromKubeconfig(readFile(kubeconfigPath));
        this.client = new KubernetesClientBuilder().withConfig(cfg).build();
    }

    /**
     * Build the client from API and URL + bearer token.
     *
     * @param apiUrl API server URL (e.g.https://api.cluster:6443)
     * @param token  OAuth/bearer token
     */
    public KubeClient(String apiUrl, String token) {
        Config cfg = new ConfigBuilder()
            .withMasterUrl(apiUrl)
            .withOauthToken(token)
            .withTrustCerts(true)
            .withDisableHostnameVerification(true)
            .build();
        this.client = new KubernetesClientBuilder().withConfig(cfg).build();
        this.kubeconfigPath = generateTempKubeconfig(apiUrl, token);
    }

    /**
     * Convenience factory matching the old static helper name.
     *
     * @param apiUrl url of cluster
     * @param token  token
     * @return kube client
     */
    public static KubeClient fromUrlAndToken(String apiUrl, String token) {
        return new KubeClient(apiUrl, token);
    }

    /**
     * Returns the Kubernetes client.
     *
     * @return The initialized Kubernetes client.
     */
    public KubernetesClient getClient() {
        return client;
    }

    /**
     * Test method only
     * Reconnect a client with new config
     *
     * @param config kubernetes config
     */
    void testReconnect(Config config) {
        this.client = new KubernetesClientBuilder().withConfig(config).build();
    }

    /**
     * Adapts the Kubernetes client to an OpenShift client.
     *
     * @return An instance of OpenShiftClient.
     */
    public OpenShiftClient getOpenShiftClient() {
        return client.adapt(OpenShiftClient.class);
    }

    /**
     * Returns the path to the kubeconfig file used for authentication.
     *
     * @return The kubeconfig file path.
     */
    public String getKubeconfigPath() {
        return kubeconfigPath;
    }

    /**
     * Reads Kubernetes resources from a file at the specified path.
     *
     * @param file The path to the file containing Kubernetes resources.
     * @return A list of {@link HasMetadata} resources defined in the file.
     * @throws IOException If an I/O error occurs reading from the file.
     */
    public List<HasMetadata> readResourcesFromFile(Path file) throws IOException {
        return readResourcesFromFile(Files.newInputStream(file));
    }

    /**
     * Reads Kubernetes resources from an InputStream.
     *
     * @param is The InputStream containing Kubernetes resources.
     * @return A list of {@link HasMetadata} resources defined in the stream.
     * @throws IOException If an I/O error occurs.
     */
    public List<HasMetadata> readResourcesFromFile(InputStream is) throws IOException {
        try (is) {
            return client.load(is).items();
        }
    }

    /**
     * Check if namespace exists in current cluster
     *
     * @param namespace namespace name
     * @return true if namespace exists
     */
    public boolean namespaceExists(String namespace) {
        return client.namespaces().list().getItems().stream()
            .anyMatch(n -> n.getMetadata().getName().equals(namespace));
    }

    /**
     * Creates resource and apply modifier
     *
     * @param resources resources
     * @param modifier  modifier
     */
    public void create(List<HasMetadata> resources, UnaryOperator<HasMetadata> modifier) {
        create(null, resources, modifier);
    }

    /**
     * Updates resources and apply modifier
     *
     * @param resources resources
     * @param modifier  modifier
     */
    public void update(List<HasMetadata> resources, UnaryOperator<HasMetadata> modifier) {
        update(null, resources, modifier);
    }

    /**
     * Creates resource and apply modifier
     *
     * @param namespace namespace
     * @param resources resources
     * @param modifier  modifier
     */
    public void create(String namespace, List<HasMetadata> resources, UnaryOperator<HasMetadata> modifier) {
        resources.forEach(res -> {
            HasMetadata h = modifier.apply(res);
            LOGGER.debug(LoggerUtils.RESOURCE_WITH_NAMESPACE_LOGGER_PATTERN,
                "Creating", h.getKind(), h.getMetadata().getName(), namespace);
            if (namespace == null) {
                client.resource(h).create();
            } else {
                client.resource(h).inNamespace(namespace).create();
            }
        });
    }

    /**
     * Updates resources and apply modifier
     *
     * @param namespace namespace
     * @param resources resources
     * @param modifier  modifier
     */
    public void update(String namespace, List<HasMetadata> resources, UnaryOperator<HasMetadata> modifier) {
        resources.forEach(res -> {
            HasMetadata h = modifier.apply(res);
            LOGGER.debug(LoggerUtils.RESOURCE_WITH_NAMESPACE_LOGGER_PATTERN,
                "Updating", h.getKind(), h.getMetadata().getName(), namespace);
            if (namespace == null) {
                client.resource(h).update();
            } else {
                client.resource(h).inNamespace(namespace).update();
            }
        });
    }

    /**
     * Create or update resources from file and apply modifier
     *
     * @param resources resources
     * @param modifier  modifier method
     */
    public void createOrUpdate(List<HasMetadata> resources, UnaryOperator<HasMetadata> modifier) {
        createOrUpdate(null, resources, modifier);
    }

    /**
     * Create or update resources from file and apply modifier
     *
     * @param ns        namespace
     * @param resources resources
     * @param modifier  modifier method
     */
    public void createOrUpdate(String ns, List<HasMetadata> resources, UnaryOperator<HasMetadata> modifier) {
        resources.forEach(i -> {
            HasMetadata h = modifier.apply(i);
            if (h != null) {
                if (client.resource(h).get() == null) {
                    LOGGER.debug(LoggerUtils.RESOURCE_WITH_NAMESPACE_LOGGER_PATTERN,
                        "Creating", h.getKind(), h.getMetadata().getName(), h.getMetadata().getNamespace());
                    if (ns == null) {
                        client.resource(h).create();
                    } else {
                        client.resource(h).inNamespace(ns).create();
                    }
                } else {
                    LOGGER.debug(LoggerUtils.RESOURCE_WITH_NAMESPACE_LOGGER_PATTERN,
                        "Updating", h.getKind(), h.getMetadata().getName(), h.getMetadata().getNamespace());
                    if (ns == null) {
                        client.resource(h).update();
                    } else {
                        client.resource(h).inNamespace(ns).update();
                    }
                }
            }
        });
    }

    /**
     * Deletes resources
     *
     * @param resources resources
     */
    public void delete(List<HasMetadata> resources) {
        resources.forEach(h -> {
            if (h != null) {
                if (client.resource(h).get() != null) {
                    LOGGER.debug(LoggerUtils.RESOURCE_WITH_NAMESPACE_LOGGER_PATTERN,
                        "Deleting", h.getKind(), h.getMetadata().getName(), h.getMetadata().getNamespace());
                    client.resource(h).delete();
                }
            }
        });
    }

    /**
     * Delete resources
     *
     * @param resources resources
     * @param namespace namespace
     */
    public void delete(List<HasMetadata> resources, String namespace) {
        resources.forEach(h -> {
            if (h != null) {
                if (client.resource(h).inNamespace(namespace).get() != null) {
                    LOGGER.debug(LoggerUtils.RESOURCE_WITH_NAMESPACE_LOGGER_PATTERN,
                        "Deleting", h.getKind(), h.getMetadata().getName(), namespace);
                    client.resource(h).inNamespace(namespace).delete();
                }
            }
        });
    }

    /**
     * Get all pods from namespace
     *
     * @param namespaceName namespace
     * @return list of pods
     */
    public List<Pod> listPods(String namespaceName) {
        return client.pods().inNamespace(namespaceName).list().getItems();
    }

    /**
     * Get all pods with prefix nanme
     *
     * @param namespaceName namespace
     * @param selector      prefix
     * @return lust of pods
     */
    public List<Pod> listPods(String namespaceName, LabelSelector selector) {
        return client.pods().inNamespace(namespaceName).withLabelSelector(selector).list().getItems();
    }

    /**
     * Returns list of pods by prefix in pod name
     *
     * @param namespaceName Namespace name
     * @param podNamePrefix prefix with which the name should begin
     * @return List of pods
     */
    public List<Pod> listPodsByPrefixInName(String namespaceName, String podNamePrefix) {
        return listPods(namespaceName)
            .stream().filter(p -> p.getMetadata().getName().startsWith(podNamePrefix))
            .collect(Collectors.toList());
    }

    /**
     * Return log from pod with one container
     *
     * @param namespaceName namespace of the pod
     * @param podName       pod name
     * @return logs
     */
    public String getLogsFromPod(String namespaceName, String podName) {
        return client.pods().inNamespace(namespaceName).withName(podName).getLog();
    }

    /**
     * Return log from pods specific container
     *
     * @param namespaceName namespace of the pod
     * @param podName       pod name
     * @param containerName container name
     * @return logs
     */
    public String getLogsFromContainer(String namespaceName, String podName, String containerName) {
        return client.pods().inNamespace(namespaceName).withName(podName).inContainer(containerName).getLog();
    }

    /**
     * Returns list of deployments with prefix name
     *
     * @param namespace  namespace
     * @param namePrefix prefix
     * @return list of deployments
     */
    public String getDeploymentNameByPrefix(String namespace, String namePrefix) {
        List<Deployment> prefixDeployments = client.apps().deployments()
            .inNamespace(namespace).list().getItems().stream().filter(rs ->
                rs.getMetadata().getName().startsWith(namePrefix)).toList();

        if (!prefixDeployments.isEmpty()) {
            return prefixDeployments.get(0).getMetadata().getName();
        } else {
            return null;
        }
    }

    private static String readFile(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read " + path, e);
        }
    }

    /**
     * Generates a user‑specific temporary kube‑config on disk so external
     * kubectl/oc commands (used elsewhere in the framework) have authentication.
     */
    private String generateTempKubeconfig(String url, String token) {
        try {
            String host = java.net.URI.create(url).getHost().replaceAll("[^\\w]", "-");
            String suffix = Integer.toHexString((host + token).hashCode()).substring(0, 6);
            String path = Path.of(KubeTestEnv.USER_PATH,
                "test-" + host + "-" + suffix + ".kubeconfig").toString();

            Exec.exec(null, Arrays.asList("kubectl", "config", "set-credentials",
                    "tf-user-" + suffix, "--token", token, "--kubeconfig", path),
                0, false, true);
            Exec.exec(null, Arrays.asList("kubectl", "config", "set-cluster",
                    "tf-cluster-" + suffix, "--insecure-skip-tls-verify=true",
                    "--server", url, "--kubeconfig", path),
                0, false, true);
            Exec.exec(null, Arrays.asList("kubectl", "config", "set-context",
                    "tf-context-" + suffix, "--user", "tf-user-" + suffix,
                    "--cluster", "tf-cluster-" + suffix, "--namespace", "default",
                    "--kubeconfig", path),
                0, false, true);
            Exec.exec(null, Arrays.asList("kubectl", "config", "use-context",
                    "tf-context-" + suffix, "--kubeconfig", path),
                0, false, true);
            return path;
        } catch (Exception ex) {
            LOGGER.warn("Could not generate temp kubeconfig: {}", ex.getMessage());
            return null;
        }
    }
}
