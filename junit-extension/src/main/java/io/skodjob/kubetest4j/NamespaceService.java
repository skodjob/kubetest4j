/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.utils.KubeUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides namespace auto-labeling for log collection and the multi-kubeContext provider interface.
 * <p>
 * Namespace lifecycle (creation/deletion) is handled by {@link ClassNamespaceService}
 * and {@link MethodNamespaceService}. This service only manages the auto-labeling callback
 * that tags namespaces for log collection when they are created via {@link KubeResourceManager}.
 */
class NamespaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceService.class);

    // Log collection labels
    private static final String LOG_COLLECTION_LABEL_KEY = "kubetest4j.skodjob.io/log-collection";
    private static final String LOG_COLLECTION_LABEL_VALUE = "enabled";

    // Track whether auto-labeling has been configured for the singleton ResourceManager
    private static final AtomicBoolean AUTO_LABELING_CONFIGURED = new AtomicBoolean(false);

    // Track namespaces that have already been labeled to prevent duplicates
    private static final Set<String> LABELED_NAMESPACES = ConcurrentHashMap.newKeySet();

    NamespaceService() {
    }

    /**
     * Sets up automatic namespace labeling for log collection.
     * This method ensures that the callbacks are only registered once for the singleton ResourceManager.
     * Uses {@link AtomicBoolean#compareAndSet} to guarantee only one thread registers the callbacks,
     * making this safe for parallel test execution.
     */
    public void setupNamespaceAutoLabeling(KubeResourceManager resourceManager) {
        if (!AUTO_LABELING_CONFIGURED.compareAndSet(false, true)) {
            LOGGER.debug("Auto-labeling already configured, skipping setup");
            return;
        }

        LOGGER.debug("Setting up namespace auto-labeling for log collection");

        resourceManager.addCreateCallback(resource -> {
            LOGGER.debug("Resource create callback triggered for: {} - {}",
                resource.getKind(), resource.getMetadata().getName());

            if ("Namespace".equals(resource.getKind())) {
                String namespaceName = resource.getMetadata().getName();

                // Check if we've already processed this namespace
                if (!LABELED_NAMESPACES.add(namespaceName)) {
                    LOGGER.debug("Namespace '{}' already processed for auto-labeling, skipping", namespaceName);
                    return;
                }

                LOGGER.debug("Auto-labeling namespace '{}' for log collection", namespaceName);

                try {
                    KubeUtils.labelNamespace(namespaceName, LOG_COLLECTION_LABEL_KEY, LOG_COLLECTION_LABEL_VALUE);
                    LOGGER.debug("Labeled namespace '{}' with {}={}",
                        namespaceName, LOG_COLLECTION_LABEL_KEY, LOG_COLLECTION_LABEL_VALUE);
                } catch (Exception e) {
                    LOGGER.error("Failed to label namespace '{}' for log collection: {}",
                        namespaceName, e.getMessage(), e);
                    // Remove from set on failure so it can be retried
                    LABELED_NAMESPACES.remove(namespaceName);
                }
            }
        });

        // Register delete callback to remove namespaces from the tracking deleted
        resourceManager.addDeleteCallback(resource -> {
            if ("Namespace".equals(resource.getKind())) {
                String namespaceName = resource.getMetadata().getName();
                if (LABELED_NAMESPACES.remove(namespaceName)) {
                    LOGGER.debug("Removed namespace '{}' from auto-labeling tracking", namespaceName);
                }
            }
        });
    }

    // ===============================
    // Helper Classes and Interfaces
    // ===============================

    /**
     * Interface to abstract multi-kubeContext operations for dependency injection.
     * This allows namespace and test services to obtain ResourceManagers for
     * specific Kubernetes contexts without depending on the extension directly.
     */
    public interface MultiKubeContextProvider {
        /**
         * Gets or creates a KubeResourceManager for the specified Kubernetes kubeContext.
         *
         * @param context     the extension kubeContext
         * @param kubeContext the Kubernetes kubeContext name
         * @return the resource manager for the specified Kubernetes kubeContext
         */
        KubeResourceManager getResourceManagerForKubeContext(ExtensionContext context, String kubeContext);
    }
}
