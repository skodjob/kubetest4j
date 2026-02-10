/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.utils.KubeUtils;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages namespace lifecycle operations for Kubernetes tests.
 * This class handles namespace creation, setup, cleanup, and auto-labeling
 * for both single-kubeContext and multi-kubeContext test scenarios.
 */
class NamespaceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceManager.class);

    // Log collection labels
    private static final String LOG_COLLECTION_LABEL_KEY = "kubetest4j.skodjob.io/log-collection";
    private static final String LOG_COLLECTION_LABEL_VALUE = "enabled";

    // Track whether auto-labeling has been configured for the singleton ResourceManager
    private static boolean autoLabelingConfigured = false;

    // Track namespaces that have already been labeled to prevent duplicates
    private static final Set<String> LABELED_NAMESPACES = ConcurrentHashMap.newKeySet();

    private final ContextStoreHelper contextStoreHelper;
    private final MultiKubeContextProvider multiContextProvider;

    /**
     * Creates a new NamespaceManager with the given dependencies.
     *
     * @param contextStoreHelper   provides access to extension kubeContext storage
     * @param multiContextProvider provides multi-kubeContext operations
     */
    NamespaceManager(ContextStoreHelper contextStoreHelper, MultiKubeContextProvider multiContextProvider) {
        this.contextStoreHelper = contextStoreHelper;
        this.multiContextProvider = multiContextProvider;
    }

    // ===============================
    // Namespace Setup Methods
    // ===============================

    /**
     * Sets up all namespaces for the test, including multi-kubeContext namespaces.
     */
    public void setupNamespaces(ExtensionContext context, TestConfig testConfig,
                                KubeResourceManager resourceManager) {
        List<String> namespaceNames = testConfig.namespaces();
        Map<String, Namespace> namespaceObjects = new HashMap<>();
        List<String> createdNamespaces = new ArrayList<>();

        LOGGER.info("Setting up test namespaces: {}", String.join(", ", namespaceNames));

        // Set up primary kubeContext namespaces
        for (String namespaceName : namespaceNames) {
            NamespaceSetupContext setupContext = new NamespaceSetupContext(
                namespaceName, "", testConfig, resourceManager,
                namespaceObjects, createdNamespaces, context, null
            );
            performNamespaceSetup(setupContext);
        }

        // Set up kubeContext-specific namespaces
        for (TestConfig.KubeContextMappingConfig contextMapping : testConfig.kubeContextMappings()) {
            String clusterContext = contextMapping.kubeContext();
            LOGGER.info("Setting up namespaces for kubeContext '{}': {}", clusterContext,
                String.join(", ", contextMapping.namespaces()));

            // Get or create ResourceManager for this kubeContext
            KubeResourceManager contextResourceManager =
                multiContextProvider.getResourceManagerForKubeContext(context, clusterContext);
            Map<String, Namespace> contextNamespaceObjects =
                multiContextProvider.getOrCreateNamespaceObjectsForKubeContext(context, clusterContext);
            List<String> contextCreatedNamespaces =
                multiContextProvider.getOrCreateCreatedNamespacesForKubeContext(context, clusterContext);

            for (String namespaceName : contextMapping.namespaces()) {
                NamespaceSetupContext setupContext = new NamespaceSetupContext(
                    namespaceName, clusterContext, testConfig, contextResourceManager,
                    contextNamespaceObjects, contextCreatedNamespaces, context, contextMapping
                );
                performNamespaceSetup(setupContext);
            }

            // Store kubeContext-specific namespace objects
            multiContextProvider.storeNamespaceObjectsForKubeContext(context, clusterContext, contextNamespaceObjects);
        }

        // Store which namespaces we actually created (for cleanup)
        if (!createdNamespaces.isEmpty()) {
            contextStoreHelper.putCreatedNamespaceFlag(context, true);
            contextStoreHelper.putCreatedNamespaceNames(context, createdNamespaces);
            LOGGER.info("Created namespaces: {}", String.join(", ", createdNamespaces));
        } else {
            LOGGER.info("No new namespaces were created - all namespaces already existed");
        }

        // Store namespace objects for injection
        contextStoreHelper.putNamespaceObjects(context, namespaceObjects);
        contextStoreHelper.putNamespaces(context, namespaceNames.toArray(new String[0]));
        LOGGER.info("Using namespaces: {}", String.join(", ", namespaceNames));
    }

    /**
     * Sets up automatic namespace labeling for log collection.
     * This method ensures that the callback is only registered once for the singleton ResourceManager.
     */
    public void setupNamespaceAutoLabeling(KubeResourceManager resourceManager) {
        // Check if auto-labeling has already been configured
        if (autoLabelingConfigured) {
            LOGGER.debug("Auto-labeling already configured, skipping setup");
            return;
        }

        LOGGER.info("Setting up automatic namespace labeling for log collection");

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

                LOGGER.info("Auto-labeling namespace '{}' for log collection", namespaceName);

                try {
                    KubeUtils.labelNamespace(namespaceName, LOG_COLLECTION_LABEL_KEY, LOG_COLLECTION_LABEL_VALUE);
                    LOGGER.info("Successfully labeled namespace '{}' with {}={}",
                        namespaceName, LOG_COLLECTION_LABEL_KEY, LOG_COLLECTION_LABEL_VALUE);
                } catch (Exception e) {
                    LOGGER.error("Failed to label namespace '{}' for log collection: {}",
                        namespaceName, e.getMessage(), e);
                    // Remove from set on failure so it can be retried
                    LABELED_NAMESPACES.remove(namespaceName);
                }
            }
        });

        // Mark auto-labeling as configured
        autoLabelingConfigured = true;
    }

    /**
     * Resets the auto-labeling configuration.
     * This should be called when cleaning up to allow fresh configuration in subsequent tests.
     */
    public static void resetAutoLabelingConfiguration() {
        autoLabelingConfigured = false;
        LABELED_NAMESPACES.clear();
        LOGGER.debug("Reset auto-labeling configuration and cleared labeled namespaces");
    }

    /**
     * Cleans up namespaces that were created during the test.
     */
    public void cleanupNamespaces(ExtensionContext context, TestConfig testConfig) {
        // Only delete namespaces that were actually created by this test
        List<String> createdNamespaces = contextStoreHelper.getCreatedNamespaceNames(context);

        if (createdNamespaces == null || createdNamespaces.isEmpty()) {
            LOGGER.info("No namespaces to delete - all namespaces were existing before the test");
            return;
        }

        LOGGER.info("Deleting only test-created namespaces: {}", String.join(", ", createdNamespaces));

        try {
            KubeResourceManager resourceManager = contextStoreHelper.getResourceManager(context);
            for (String namespaceName : createdNamespaces) {
                Namespace namespace = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(namespaceName)
                    .endMetadata()
                    .build();
                LoggerUtils.logResource("Deleting", Level.DEBUG, namespace);
                resourceManager.deleteResourceWithWait(namespace);
            }
            LOGGER.info("Successfully deleted {} test-created namespaces", createdNamespaces.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to delete test-created namespaces: {}", String.join(", ", createdNamespaces), e);
        }
    }

    // ===============================
    // Private Helper Methods
    // ===============================


    private void performNamespaceSetup(NamespaceSetupContext setupContext) {
        String contextLabel = setupContext.clusterContext().isEmpty() ?
            KubeTestConstants.DEFAULT_CONTEXT_NAME : setupContext.clusterContext();
        LOGGER.debug("Setting up namespace '{}' in kubeContext '{}'", setupContext.namespaceName(), contextLabel);

        // Check if namespace already exists
        Namespace existingNamespace = setupContext.resourceManager().kubeClient().getClient().namespaces()
            .withName(setupContext.namespaceName()).get();

        if (existingNamespace != null) {
            LOGGER.info("Using existing namespace '{}' in kubeContext '{}'",
                setupContext.namespaceName(), contextLabel);

            // Do not modify existing namespaces - respect namespace protection principle
            setupContext.namespaceObjects().put(setupContext.namespaceName(), existingNamespace);
        } else {
            // Create the namespace since it was specified in the configuration
            // Prepare labels map
            Map<String, String> labels = new HashMap<>();

            // Add custom labels from global config
            for (String label : setupContext.testConfig().namespaceLabels()) {
                String[] parts = label.split("=", 2);
                if (parts.length == 2) {
                    labels.put(parts[0], parts[1]);
                }
            }

            // Add custom labels from kubeContext mapping if available
            if (setupContext.contextMapping() != null) {
                for (String label : setupContext.contextMapping().namespaceLabels()) {
                    String[] parts = label.split("=", 2);
                    if (parts.length == 2) {
                        labels.put(parts[0], parts[1]);
                    }
                }
            }

            // Prepare annotations map
            Map<String, String> annotations = new HashMap<>();

            // Add annotations from global config
            for (String annotation : setupContext.testConfig().namespaceAnnotations()) {
                String[] parts = annotation.split("=", 2);
                if (parts.length == 2) {
                    annotations.put(parts[0], parts[1]);
                }
            }

            // Add annotations from kubeContext mapping if available
            if (setupContext.contextMapping() != null) {
                for (String annotation : setupContext.contextMapping().namespaceAnnotations()) {
                    String[] parts = annotation.split("=", 2);
                    if (parts.length == 2) {
                        annotations.put(parts[0], parts[1]);
                    }
                }
            }

            // Build namespace with all labels and annotations
            NamespaceBuilder metadataBuilder = new NamespaceBuilder()
                .withNewMetadata()
                .withName(setupContext.namespaceName())
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata();

            Namespace namespace = metadataBuilder.build();
            LOGGER.debug("Creating namespace with labels: {}", labels);
            LOGGER.debug("Creating namespace with annotations: {}", annotations);
            LoggerUtils.logResource("Creating", Level.DEBUG, namespace);
            setupContext.resourceManager().createResourceWithWait(namespace);

            // Wait a moment for namespace to be fully synced before retrieving
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Retrieve the actual namespace from cluster to get complete object with status
            Namespace actualNamespace = setupContext.resourceManager().kubeClient().getClient()
                .namespaces()
                .withName(setupContext.namespaceName())
                .get();

            LOGGER.debug("Retrieved namespace labels: {}",
                actualNamespace.getMetadata().getLabels());
            LOGGER.debug("Retrieved namespace annotations: {}",
                actualNamespace.getMetadata().getAnnotations());

            // If labels are missing from retrieved namespace, use the built one but copy status
            if (actualNamespace.getMetadata().getLabels() == null ||
                actualNamespace.getMetadata().getLabels().size() < labels.size()) {
                LOGGER.warn("Labels missing from retrieved namespace, using built namespace with status");
                namespace.setStatus(actualNamespace.getStatus());
                setupContext.namespaceObjects().put(setupContext.namespaceName(), namespace);
            } else {
                setupContext.namespaceObjects().put(setupContext.namespaceName(), actualNamespace);
            }
            setupContext.createdNamespaces().add(setupContext.namespaceName());
        }
    }

    // ===============================
    // Helper Classes and Interfaces
    // ===============================

    /**
     * Interface to abstract multi-kubeContext operations for dependency injection.
     * This allows NamespaceManager to work with multi-kubeContext functionality
     * without directly depending on specific implementation details.
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

        /**
         * Gets or creates namespace objects for the specified Kubernetes kubeContext.
         *
         * @param context     the extension kubeContext
         * @param kubeContext the Kubernetes kubeContext name
         * @return map of namespace names to namespace objects
         */
        Map<String, Namespace> getOrCreateNamespaceObjectsForKubeContext(ExtensionContext context, String kubeContext);

        /**
         * Gets or creates the list of created namespaces for the specified Kubernetes kubeContext.
         *
         * @param context     the extension kubeContext
         * @param kubeContext the Kubernetes kubeContext name
         * @return list of created namespace names
         */
        List<String> getOrCreateCreatedNamespacesForKubeContext(ExtensionContext context, String kubeContext);

        /**
         * Stores namespace objects for the specified Kubernetes kubeContext.
         *
         * @param context          the extension kubeContext
         * @param kubeContext      the Kubernetes kubeContext name
         * @param namespaceObjects the namespace objects to store
         */
        void storeNamespaceObjectsForKubeContext(ExtensionContext context, String kubeContext,
                                                 Map<String, Namespace> namespaceObjects);
    }

    /**
     * Helper record to group namespace setup parameters and reduce method parameter count.
     */
    private record NamespaceSetupContext(
        String namespaceName,
        String clusterContext,
        TestConfig testConfig,
        KubeResourceManager resourceManager,
        Map<String, Namespace> namespaceObjects,
        List<String> createdNamespaces,
        ExtensionContext extensionContext,
        TestConfig.KubeContextMappingConfig contextMapping
    ) {
    }
}