/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class that centralizes all ExtensionContext.Store access patterns.
 * This eliminates the repeated boilerplate code for kubeContext store operations
 * and provides type-safe access to stored test data.
 */
class ContextStoreHelper {

    // Extension kubeContext store keys (moved from KubernetesTestExtension)
    private static final String NAMESPACE_KEY = "kubernetes.test.namespace";
    private static final String NAMESPACE_OBJECTS_KEY = "kubernetes.test.namespaceObjects";
    private static final String RESOURCE_MANAGER_KEY = "kubernetes.test.resourceManager";
    private static final String TEST_CONFIG_KEY = "kubernetes.test.config";
    private static final String CREATED_NAMESPACE_KEY = "kubernetes.test.createdNamespace";
    private static final String CREATED_NAMESPACE_NAMES_KEY = "kubernetes.test.createdNamespaceNames";
    private static final String LOG_COLLECTOR_KEY = "kubernetes.test.logCollector";

    // Multi-kubeContext extension store keys
    private static final String CONTEXT_MANAGERS_KEY = "kubernetes.test.contextManagers";
    private static final String CONTEXT_CLOSERS_KEY = "kubernetes.test.contextClosers";
    private static final String CONTEXT_NAMESPACE_OBJECTS_KEY = "kubernetes.test.contextNamespaceObjects";
    private static final String CONTEXT_CREATED_NAMESPACES_KEY = "kubernetes.test.contextCreatedNamespaces";

    // ===============================
    // Basic Store Operations
    // ===============================

    /**
     * Get a value from the extension kubeContext store.
     */
    public <T> T get(ExtensionContext context, String key, Class<T> type) {
        return context.getStore(ExtensionContext.Namespace.GLOBAL).get(key, type);
    }

    /**
     * Put a value in the extension kubeContext store.
     */
    public void put(ExtensionContext context, String key, Object value) {
        context.getStore(ExtensionContext.Namespace.GLOBAL).put(key, value);
    }

    /**
     * Get a value from the extension kubeContext store with generic type (requires casting).
     */
    @SuppressWarnings("unchecked")
    public <T> T getUnchecked(ExtensionContext context, String key) {
        return (T) context.getStore(ExtensionContext.Namespace.GLOBAL).get(key);
    }

    // ===============================
    // Typed Getters for Common Objects
    // ===============================

    /**
     * Gets the test configuration from the extension kubeContext.
     */
    public TestConfig getTestConfig(ExtensionContext context) {
        return get(context, TEST_CONFIG_KEY, TestConfig.class);
    }

    /**
     * Stores the test configuration in the extension kubeContext.
     */
    public void putTestConfig(ExtensionContext context, TestConfig testConfig) {
        put(context, TEST_CONFIG_KEY, testConfig);
    }

    /**
     * Gets the resource manager from the extension kubeContext.
     */
    public KubeResourceManager getResourceManager(ExtensionContext context) {
        return get(context, RESOURCE_MANAGER_KEY, KubeResourceManager.class);
    }

    /**
     * Stores the resource manager in the extension kubeContext.
     */
    public void putResourceManager(ExtensionContext context, KubeResourceManager resourceManager) {
        put(context, RESOURCE_MANAGER_KEY, resourceManager);
    }

    /**
     * Gets the log collector from the extension kubeContext.
     */
    public LogCollector getLogCollector(ExtensionContext context) {
        return get(context, LOG_COLLECTOR_KEY, LogCollector.class);
    }

    /**
     * Stores the log collector in the extension kubeContext.
     */
    public void putLogCollector(ExtensionContext context, LogCollector logCollector) {
        put(context, LOG_COLLECTOR_KEY, logCollector);
    }

    /**
     * Gets the namespace objects map from the extension kubeContext.
     */
    public Map<String, Namespace> getNamespaceObjects(ExtensionContext context) {
        return getUnchecked(context, NAMESPACE_OBJECTS_KEY);
    }

    /**
     * Stores the namespace objects map in the extension kubeContext.
     */
    public void putNamespaceObjects(ExtensionContext context, Map<String, Namespace> namespaceObjects) {
        put(context, NAMESPACE_OBJECTS_KEY, namespaceObjects);
    }


    /**
     * Stores the namespace names array in the extension kubeContext.
     */
    public void putNamespaces(ExtensionContext context, String[] namespaces) {
        put(context, NAMESPACE_KEY, namespaces);
    }

    /**
     * Gets the list of created namespace names from the extension kubeContext.
     */
    public List<String> getCreatedNamespaceNames(ExtensionContext context) {
        return getUnchecked(context, CREATED_NAMESPACE_NAMES_KEY);
    }

    /**
     * Stores the list of created namespace names in the extension kubeContext.
     */
    public void putCreatedNamespaceNames(ExtensionContext context, List<String> createdNamespaces) {
        put(context, CREATED_NAMESPACE_NAMES_KEY, createdNamespaces);
    }


    /**
     * Stores the created namespace flag in the extension kubeContext.
     */
    public void putCreatedNamespaceFlag(ExtensionContext context, boolean created) {
        put(context, CREATED_NAMESPACE_KEY, created);
    }

    // ===============================
    // Context Closers Management
    // ===============================

    /**
     * Gets the primary kubeContext closer from the extension kubeContext.
     */
    public AutoCloseable getContextCloser(ExtensionContext context) {
        return get(context, "kubeContext.closer", AutoCloseable.class);
    }

    /**
     * Stores the primary kubeContext closer in the extension kubeContext.
     */
    public void putContextCloser(ExtensionContext context, AutoCloseable contextCloser) {
        put(context, "kubeContext.closer", contextCloser);
    }

    // ===============================
    // Multi-Context Store Operations
    // ===============================

    /**
     * Get or create a map from the kubeContext store with automatic initialization.
     */
    @SuppressWarnings("unchecked")
    private <T> Map<String, T> getOrCreateMap(ExtensionContext context, String key) {
        Map<String, T> map = (Map<String, T>) context.getStore(ExtensionContext.Namespace.GLOBAL).get(key);
        if (map == null) {
            map = new HashMap<>();
            put(context, key, map);
        }
        return map;
    }

    /**
     * Gets or creates the map of kubeContext managers.
     */
    public Map<String, KubeResourceManager> getOrCreateContextManagers(ExtensionContext context) {
        return getOrCreateMap(context, CONTEXT_MANAGERS_KEY);
    }

    /**
     * Gets or creates the map of kubeContext closers.
     */
    public Map<String, AutoCloseable> getOrCreateContextClosers(ExtensionContext context) {
        return getOrCreateMap(context, CONTEXT_CLOSERS_KEY);
    }

    /**
     * Gets or creates the map of kubeContext namespace objects.
     */
    public Map<String, Map<String, Namespace>> getOrCreateContextNamespaceObjects(ExtensionContext context) {
        return getOrCreateMap(context, CONTEXT_NAMESPACE_OBJECTS_KEY);
    }

    /**
     * Gets or creates the map of kubeContext created namespaces.
     */
    public Map<String, List<String>> getOrCreateContextCreatedNamespaces(ExtensionContext context) {
        return getOrCreateMap(context, CONTEXT_CREATED_NAMESPACES_KEY);
    }

    // ===============================
    // Context-Specific Convenience Methods
    // ===============================

    public KubeResourceManager getContextManager(ExtensionContext context, String clusterContext) {
        Map<String, KubeResourceManager> contextManagers = getOrCreateContextManagers(context);
        return contextManagers.get(clusterContext);
    }

    public void putContextManager(ExtensionContext context, String clusterContext, KubeResourceManager manager) {
        Map<String, KubeResourceManager> contextManagers = getOrCreateContextManagers(context);
        contextManagers.put(clusterContext, manager);
    }

    public AutoCloseable getContextCloser(ExtensionContext context, String clusterContext) {
        Map<String, AutoCloseable> contextClosers = getOrCreateContextClosers(context);
        return contextClosers.get(clusterContext);
    }

    public void putContextCloser(ExtensionContext context, String clusterContext, AutoCloseable closer) {
        Map<String, AutoCloseable> contextClosers = getOrCreateContextClosers(context);
        contextClosers.put(clusterContext, closer);
    }

    public Map<String, Namespace> getNamespaceObjectsForContext(ExtensionContext context, String clusterContext) {
        Map<String, Map<String, Namespace>> contextNamespaces = getOrCreateContextNamespaceObjects(context);
        return contextNamespaces.get(clusterContext);
    }

    public void putNamespaceObjectsForContext(ExtensionContext context, String clusterContext,
                                              Map<String, Namespace> namespaceObjects) {
        Map<String, Map<String, Namespace>> contextNamespaces = getOrCreateContextNamespaceObjects(context);
        contextNamespaces.put(clusterContext, namespaceObjects);
    }

    public Map<String, Namespace> getOrCreateNamespaceObjectsForContext(ExtensionContext context,
                                                                        String clusterContext) {
        Map<String, Map<String, Namespace>> contextNamespaces = getOrCreateContextNamespaceObjects(context);
        return contextNamespaces.computeIfAbsent(clusterContext, k -> new HashMap<>());
    }

    public List<String> getOrCreateCreatedNamespacesForContext(ExtensionContext context, String clusterContext) {
        Map<String, List<String>> contextCreatedNamespaces = getOrCreateContextCreatedNamespaces(context);
        return contextCreatedNamespaces.computeIfAbsent(clusterContext, k -> new java.util.ArrayList<>());
    }

    public Map<String, KubeResourceManager> getContextManagers(ExtensionContext context) {
        Map<String, KubeResourceManager> contextManagers = getOrCreateContextManagers(context);
        return contextManagers.isEmpty() ? java.util.Collections.emptyMap() : contextManagers;
    }

    public Map<String, AutoCloseable> getAllContextClosers(ExtensionContext context) {
        Map<String, AutoCloseable> contextClosers = getOrCreateContextClosers(context);
        return contextClosers.isEmpty() ? java.util.Collections.emptyMap() : contextClosers;
    }
}