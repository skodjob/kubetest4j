/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.conditions;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.VersionInfo;
import io.skodjob.kubetest4j.annotations.RequiresKubernetes;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

/**
 * JUnit 5 execution condition that checks if a Kubernetes cluster is available and accessible.
 * Used by the {@link RequiresKubernetes} annotation to conditionally execute tests.
 */
public class KubernetesCondition implements ExecutionCondition {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesCondition.class);
    private static final String DEFAULT_SKIP_REASON = "Kubernetes cluster is not available or accessible";

    /**
     * Package-private constructor for JUnit instantiation.
     * External code should not directly instantiate this condition.
     */
    KubernetesCondition() {
        // Default constructor
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<RequiresKubernetes> annotation = findAnnotation(context.getElement(), RequiresKubernetes.class);

        if (annotation.isEmpty()) {
            return ConditionEvaluationResult.enabled("@RequiresKubernetes not present");
        }

        RequiresKubernetes requiresK8s = annotation.get();
        String skipReason = requiresK8s.skipReason().isEmpty() ? DEFAULT_SKIP_REASON : requiresK8s.skipReason();

        try {
            boolean clusterAvailable = checkKubernetesCluster(requiresK8s);
            if (clusterAvailable) {
                return ConditionEvaluationResult.enabled("Kubernetes cluster is available and accessible");
            } else {
                return ConditionEvaluationResult.disabled(skipReason);
            }
        } catch (Exception e) {
            LOGGER.debug("Kubernetes cluster check failed", e);
            return ConditionEvaluationResult.disabled(skipReason + ": " + e.getMessage());
        }
    }

    private boolean checkKubernetesCluster(RequiresKubernetes annotation) {
        try {
            return CompletableFuture.supplyAsync(() -> performClusterCheck(annotation))
                .get(annotation.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOGGER.debug("Kubernetes cluster check timed out after {}ms", annotation.timeoutMs());
            return false;
        } catch (InterruptedException e) {
            LOGGER.debug("Kubernetes cluster check was interrupted", e);
            Thread.currentThread().interrupt(); // Re-interrupt the current thread
            return false;
        } catch (Exception e) {
            LOGGER.debug("Kubernetes cluster check failed", e);
            return false;
        }
    }

    private boolean performClusterCheck(RequiresKubernetes annotation) {
        try {
            KubeClient kubeClient;

            // Use specific context if specified
            if (!annotation.kubeContext().isEmpty()) {
                try (AutoCloseable contextCloser = KubeResourceManager.get().useContext(annotation.kubeContext())) {
                    kubeClient = KubeResourceManager.get().kubeClient();
                } catch (Exception e) {
                    LOGGER.debug("Failed to switch to context: {}", annotation.kubeContext(), e);
                    return false;
                }
            } else {
                kubeClient = KubeResourceManager.get().kubeClient();
            }

            KubernetesClient client = kubeClient.getClient();

            // Basic connectivity check - try to get server version
            VersionInfo versionInfo = client.getKubernetesVersion();
            if (versionInfo == null) {
                LOGGER.debug("Could not retrieve Kubernetes version");
                return false;
            }
            String version = versionInfo.getGitVersion();
            LOGGER.debug("Connected to Kubernetes cluster version: {}", version);

            // Perform full cluster health check if requested
            if (annotation.checkClusterHealth()) {
                return performHealthCheck(client);
            }

            return true;

        } catch (KubernetesClientException e) {
            LOGGER.debug("Kubernetes client error: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.debug("Unexpected error during cluster check", e);
            return false;
        }
    }

    private boolean performHealthCheck(KubernetesClient client) {
        try {
            // Check if we can list nodes (requires cluster-level permissions)
            List<Node> nodes = client.nodes().list().getItems();
            if (nodes.isEmpty()) {
                LOGGER.debug("No nodes found in cluster");
                return false;
            }

            // Check if at least one node is ready
            boolean hasReadyNode = nodes.stream()
                .anyMatch(node -> node.getStatus() != null
                    && node.getStatus().getConditions() != null
                    && node.getStatus().getConditions().stream()
                        .anyMatch(condition -> "Ready".equals(condition.getType())
                            && "True".equals(condition.getStatus())));

            if (!hasReadyNode) {
                LOGGER.debug("No ready nodes found in cluster");
                return false;
            }

            LOGGER.debug("Cluster health check passed: {} nodes found, at least one ready", nodes.size());
            return true;

        } catch (Exception e) {
            LOGGER.debug("Cluster health check failed", e);
            return false;
        }
    }
}
