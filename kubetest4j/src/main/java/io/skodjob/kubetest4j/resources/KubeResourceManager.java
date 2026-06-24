/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.KubeTestEnv;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.clients.cmdClient.Kubectl;
import io.skodjob.kubetest4j.clients.cmdClient.Oc;
import io.skodjob.kubetest4j.environment.TestEnvironmentVariables;
import io.skodjob.kubetest4j.interfaces.ResourceType;
import io.skodjob.kubetest4j.wait.Wait;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * <h2>KubeResourceManager</h2>
 * Manages Kubernetes resources for testing purposes.
 *
 * <h3>Environment variable patterns</h3>
 * <pre>
 *   # default context (optional – falls back to ~/.kube/config current‑context)
 *   KUBE_URL     = https://api.dev:6443
 *   KUBE_TOKEN   = token
 *   KUBECONFIG   = /path/to/default.kubeconfig   # overrides URL/TOKEN
 *
 *   # extra contexts
 *   KUBECONFIG_PROD = /path/to/prod.kubeconfig
 *   KUBE_URL_STAGE  = https://api.stage:6443
 *   KUBE_TOKEN_STAGE= token
 *   KUBE_URL_QA     = https://api.qa:6443
 *   KUBE_TOKEN_QA   = token
 * </pre>
 * <p>
 * The suffix after the final underscore becomes the context id (lower‑case).
 * The default context has no suffix.
 *
 * <h3>Multi-context usage</h3>
 * <pre>
 * KubeResourceManager defaultMgr = KubeResourceManager.get();
 * KubeResourceManager prodMgr = KubeResourceManager.getForContext("prod");
 * defaultMgr.createResourceWithWait(myDeployment);
 * prodMgr.createResourceWithWait(prodDeployment);
 * </pre>
 *
 * <p>Implementation is split across package-private helpers:
 * <ul>
 *   <li>{@link ResourceTrackerService} — batch/stack tracking</li>
 *   <li>{@link ResourceCreateService} — create execution</li>
 *   <li>{@link ResourceUpdateService} — update/replace execution</li>
 *   <li>{@link ResourceDeleteService} — delete/cleanup</li>
 * </ul>
 */
public final class KubeResourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubeResourceManager.class);

    private static final Map<String, TestEnvironmentVariables.ClusterConfig> CLUSTER_CONFIGS =
        KubeTestEnv.CLUSTER_CONFIGS;

    // Per-context singleton instances
    private static final Map<String, KubeResourceManager> CONTEXT_INSTANCES = new ConcurrentHashMap<>();

    // Global configuration shared across all kube cluster contexts
    private static volatile String globalStoreYamlPath;
    private static final AtomicReference<ResourceType<?>[]> GLOBAL_RESOURCE_TYPES =
        new AtomicReference<>(new ResourceType<?>[]{});
    private static final List<Consumer<HasMetadata>> GLOBAL_CREATE_CALLBACKS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<HasMetadata>> GLOBAL_DELETE_CALLBACKS = new CopyOnWriteArrayList<>();

    // Instance-level variables (per kube cluster context)
    private final String contextId;
    private final Map<String, ClusterContext<? extends KubeCmdClient<?>>> clientCache = new ConcurrentHashMap<>();

    // Static variables shared
    private static final ThreadLocal<String> CURRENT_CLUSTER_CONTEXT = ThreadLocal.withInitial(() ->
        KubeTestConstants.DEFAULT_CONTEXT_NAME);
    private static final ThreadLocal<ExtensionContext> TEST_CONTEXT = new ThreadLocal<>();

    private static final Object CREATION_LOCK = new Object();

    private static final AtomicInteger MAX_CONCURRENT_OPERATIONS =
        new AtomicInteger(KubeTestConstants.DEFAULT_MAX_CONCURRENT_OPERATIONS);
    private static final AtomicReference<Semaphore> OPERATION_SEMAPHORE =
        new AtomicReference<>(new Semaphore(KubeTestConstants.DEFAULT_MAX_CONCURRENT_OPERATIONS));

    private record ClusterContext<K extends KubeCmdClient<K>>(KubeClient kubeClient, K cmdClient) {
    }

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // Extracted helpers (per-instance, created lazily to allow spy wiring)
    private ResourceTrackerService tracker;
    private ResourceCreateService createService;
    private ResourceUpdateService updateService;
    private ResourceDeleteService deleteService;

    private KubeResourceManager(String contextId) {
        this.contextId = contextId;
    }

    /* ─────────────────  SINGLETON MANAGEMENT  ──────────────── */

    /**
     * Gets KubeResourceManager instance for the default context.
     *
     * @return default context KubeResourceManager
     */
    public static KubeResourceManager get() {
        return getForContext(KubeTestConstants.DEFAULT_CONTEXT_NAME);
    }

    /**
     * Gets or creates a KubeResourceManager for the given context id.
     *
     * @param contextId the cluster context identifier
     * @return KubeResourceManager for the context
     */
    public static KubeResourceManager getForContext(String contextId) {
        String normalizedContextId = Optional.ofNullable(contextId)
            .orElse(KubeTestConstants.DEFAULT_CONTEXT_NAME)
            .toLowerCase();
        return CONTEXT_INSTANCES.computeIfAbsent(normalizedContextId,
            KubeResourceManager::new);
    }

    /**
     * Switches cluster context for the current thread.
     *
     * @param id the context id to switch to
     * @return AutoCloseable that restores the previous context
     */
    public AutoCloseable useContext(String id) {
        String ctxId = Optional.ofNullable(id)
            .orElse(KubeTestConstants.DEFAULT_CONTEXT_NAME)
            .toLowerCase();

        if (!CLUSTER_CONFIGS.containsKey(ctxId)) {
            throw new IllegalArgumentException(
                "Unknown context '" + ctxId
                    + "'. Define env vars [KUBE_URL|KUBE_TOKEN|KUBECONFIG]_"
                    + ctxId.toUpperCase());
        }

        LOGGER.info("Switching to context {}", ctxId);
        String prev = CURRENT_CLUSTER_CONTEXT.get();
        CURRENT_CLUSTER_CONTEXT.set(ctxId);
        return () -> {
            LOGGER.info("Closing context {}", ctxId);
            CURRENT_CLUSTER_CONTEXT.set(prev);
        };
    }

    /* ─────────────────  CLIENT ACCESSORS  ──────────────────── */

    @SuppressWarnings("unchecked")
    private <K extends KubeCmdClient<K>> ClusterContext<K> clusterContext(String id) {
        return (ClusterContext<K>) clientCache.computeIfAbsent(id, cid -> {
            TestEnvironmentVariables.ClusterConfig c = CLUSTER_CONFIGS.get(cid);
            if (c == null) {
                throw new IllegalStateException(
                    "Credentials missing for context " + cid);
            }

            KubeClient kube;
            if (c.kubeconfigPath() != null) {
                kube = new KubeClient(c.kubeconfigPath());
            } else if (c.url() != null && c.token() != null) {
                kube = KubeClient.fromUrlAndToken(c.url(), c.token());
            } else {
                kube = new KubeClient();
            }

            if (KubeTestEnv.CLIENT_TYPE.equals(KubeTestConstants.KUBERNETES_CLIENT)) {
                Kubectl kubectl = new Kubectl(kube.getKubeconfigPath());
                return new ClusterContext<>(kube, kubectl);
            } else {
                Oc oc = new Oc(kube.getKubeconfigPath());
                return new ClusterContext<>(kube, oc);
            }
        });
    }

    private ClusterContext<? extends KubeCmdClient<?>> clusterContext() {
        return clusterContext(activeContextId());
    }

    /**
     * Returns the effective context id for the current thread.
     * For the default instance, honors {@code useContext()} overrides;
     * non-default instances always use their own fixed context id.
     *
     * @return the active context id
     */
    String activeContextId() {
        if (KubeTestConstants.DEFAULT_CONTEXT_NAME.equals(this.contextId)) {
            return CURRENT_CLUSTER_CONTEXT.get();
        }
        return this.contextId;
    }

    /**
     * Returns kube client for current context.
     *
     * @return kube client
     */
    public KubeClient kubeClient() {
        return clusterContext().kubeClient;
    }

    /**
     * Returns kube cmd client for current context.
     *
     * @param <K> Type extending {@link KubeCmdClient}
     * @return kube cmd client
     */
    @SuppressWarnings("unchecked")
    public <K extends KubeCmdClient<K>> K kubeCmdClient() {
        return (K) clusterContext().cmdClient;
    }

    /* ─────────────────  CONFIGURATION  ─────────────────────── */

    /**
     * Sets the path for storing YAML representations of created resources.
     *
     * @param path The directory path, or null to disable YAML storage.
     */
    public void setStoreYamlPath(String path) {
        globalStoreYamlPath = path;
    }

    /**
     * Returns the current YAML storage path.
     *
     * @return yaml storage path or null
     */
    public String getStoreYamlPath() {
        return globalStoreYamlPath;
    }

    /**
     * Returns the currently configured resource types.
     *
     * @return array of resource types
     */
    public ResourceType<?>[] getResourceTypes() {
        return GLOBAL_RESOURCE_TYPES.get();
    }

    /**
     * Sets resource types used for typed creation, deletion and readiness checks.
     *
     * @param types resource type implementations
     */
    public void setResourceTypes(ResourceType<?>... types) {
        GLOBAL_RESOURCE_TYPES.set(types);
    }

    /**
     * Registers a callback invoked after each resource creation.
     *
     * @param cb the callback
     */
    public void addCreateCallback(Consumer<HasMetadata> cb) {
        GLOBAL_CREATE_CALLBACKS.add(cb);
    }

    /**
     * Registers a callback invoked after each resource deletion.
     *
     * @param cb the callback
     */
    public void addDeleteCallback(Consumer<HasMetadata> cb) {
        GLOBAL_DELETE_CALLBACKS.add(cb);
    }

    /**
     * Clears all singleton instances.
     * This is needed to isolate mock tests from real client state.
     */
    /* test */ static void clearInstances() {
        CONTEXT_INSTANCES.clear();
        ResourceTrackerService.reset();
    }

    /**
     * Returns the list of create callbacks for testing purposes.
     *
     * @return list of create callbacks
     */
    /* test */ static List<Consumer<HasMetadata>> getCreateCallbacks() {
        return GLOBAL_CREATE_CALLBACKS;
    }

    /**
     * Sets the maximum number of concurrent async operations.
     *
     * @param maxConcurrentOps maximum number of concurrent operations
     */
    public void setMaxConcurrentOperations(int maxConcurrentOps) {
        if (maxConcurrentOps <= 0) {
            throw new IllegalArgumentException(
                "maxConcurrentOperations must be positive, got: "
                    + maxConcurrentOps);
        }
        MAX_CONCURRENT_OPERATIONS.set(maxConcurrentOps);
        OPERATION_SEMAPHORE.set(new Semaphore(maxConcurrentOps));
        LOGGER.info("Max concurrent operations set to {}",
            maxConcurrentOps);
    }

    /**
     * Returns the configured maximum number of concurrent operations.
     *
     * @return max concurrent operations
     */
    public int getMaxConcurrentOperations() {
        return MAX_CONCURRENT_OPERATIONS.get();
    }

    /* ─────────────────  TEST CONTEXT LIFECYCLE  ────────────── */

    /**
     * Sets the JUnit test context for the current thread.
     *
     * @param ctx the extension context
     */
    public void setTestContext(ExtensionContext ctx) {
        TEST_CONTEXT.set(ctx);
    }

    /**
     * Returns the JUnit test context for the current thread.
     *
     * @return the extension context
     */
    public ExtensionContext getTestContext() {
        return TEST_CONTEXT.get();
    }

    /**
     * Cleans up the test context ThreadLocal.
     */
    public void cleanTestContext() {
        TEST_CONTEXT.remove();
    }

    /**
     * Cleans up the cluster context ThreadLocal.
     */
    public void cleanClusterContext() {
        CURRENT_CLUSTER_CONTEXT.remove();
    }

    /* ─────────────────  PACKAGE-PRIVATE ACCESSORS  ─────────── */

    synchronized ResourceTrackerService tracker() {
        if (tracker == null) {
            tracker = new ResourceTrackerService(this, this::getTestContext,
                r -> {
                    KubeResourceManager active =
                        getForContext(activeContextId());
                    return new ResourceItem<>(
                        () -> active.deleteResourceWithWait(r), r);
                });
        }
        return tracker;
    }

    private synchronized ResourceCreateService createService() {
        if (createService == null) {
            createService = new ResourceCreateService(this);
        }
        return createService;
    }

    private synchronized ResourceUpdateService updateService() {
        if (updateService == null) {
            updateService = new ResourceUpdateService(this);
        }
        return updateService;
    }

    private synchronized ResourceDeleteService deleteService() {
        if (deleteService == null) {
            deleteService = new ResourceDeleteService(this);
        }
        return deleteService;
    }

    String contextId() {
        return contextId;
    }

    static List<Consumer<HasMetadata>> createCallbacks() {
        return GLOBAL_CREATE_CALLBACKS;
    }

    static List<Consumer<HasMetadata>> deleteCallbacks() {
        return GLOBAL_DELETE_CALLBACKS;
    }

    static AtomicReference<Semaphore> operationSemaphore() {
        return OPERATION_SEMAPHORE;
    }

    static Executor executor() {
        return EXECUTOR;
    }

    static Object creationLock() {
        return CREATION_LOCK;
    }

    /* ─────────────────  STACK / BATCH API  ─────────────────── */

    /**
     * Pushes a resource to the stack.
     *
     * @param resource The resource to push.
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void pushToStack(T resource) {
        tracker().pushToStack(resource);
    }

    /**
     * Pushes a resource item to the stack.
     *
     * @param item The resource item to push.
     */
    public void pushToStack(ResourceItem<?> item) {
        tracker().pushToStack(item);
    }

    /**
     * Removes a resource from the stack.
     *
     * @param resource The resource to remove.
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void removeFromStack(T resource) {
        tracker().removeFromStack(resource);
    }

    /**
     * Opens an explicit batch. Resources created between
     * {@code startBatch()} and {@code endBatch()} are grouped.
     *
     * @throws IllegalStateException if a batch is already open
     */
    public void startBatch() {
        tracker().startBatch();
    }

    /**
     * Closes the explicit batch and pushes collected items.
     *
     * @throws IllegalStateException if no batch is open
     */
    public void endBatch() {
        tracker().endBatch();
    }

    /**
     * Opens a batch with try-with-resources support.
     *
     * @return AutoCloseable that calls endBatch()
     */
    public AutoCloseable openBatch() {
        return tracker().openBatch();
    }

    /**
     * Flushes any open explicit batch to the deque.
     * Safety net for lifecycle callbacks.
     */
    public void clearCurrentBatch() {
        tracker().clearCurrentBatch();
    }

    /* ─────────────────  RESOURCE I/O  ─────────────────────── */

    /**
     * Reads Kubernetes resources from a file.
     *
     * @param file path to the resource file
     * @return list of resources
     * @throws IOException on I/O error
     */
    public List<HasMetadata> readResourcesFromFile(Path file) throws IOException {
        return kubeClient().readResourcesFromFile(file);
    }

    /**
     * Reads Kubernetes resources from an InputStream.
     *
     * @param is the input stream
     * @return list of resources
     * @throws IOException on I/O error
     */
    public List<HasMetadata> readResourcesFromFile(InputStream is) throws IOException {
        return kubeClient().readResourcesFromFile(is);
    }

    /* ─────────────────  LOGGING / INSPECTION  ──────────────── */

    /**
     * Logs all managed resources across all test contexts.
     *
     * @param logLevel slf4j log level
     */
    public void printAllResources(Level logLevel) {
        tracker().printAllResources(logLevel);
    }

    /**
     * Logs managed resources in the current test context.
     *
     * @param logLevel slf4j log level
     */
    public void printCurrentResources(Level logLevel) {
        tracker().printCurrentResources(logLevel);
    }

    /**
     * Returns tracked resources for the active test context.
     *
     * @return list of tracked resources
     */
    public List<HasMetadata> getCurrentResources() {
        return tracker().getCurrentResources();
    }

    /* ─────────────────  CREATE / UPDATE  ───────────────────── */

    /**
     * Creates resources without waiting for readiness.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createResourceWithoutWait(T... resources) {
        createService().createOrUpdateResource(false, false, false, resources);
    }

    /**
     * Creates resources and waits for readiness.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createResourceWithWait(T... resources) {
        createService().createOrUpdateResource(false, true, false, resources);
    }

    /**
     * Creates or updates resources and waits for readiness.
     *
     * @param resources The resources to create or update.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createOrUpdateResourceWithWait(T... resources) {
        createService().createOrUpdateResource(false, true, true, resources);
    }

    /**
     * Creates or updates resources without waiting.
     *
     * @param resources The resources to create or update.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createOrUpdateResourceWithoutWait(T... resources) {
        createService().createOrUpdateResource(false, false, true, resources);
    }

    /**
     * Creates resources asynchronously, waits for all at end.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createResourceAsyncWait(T... resources) {
        createService().createOrUpdateResource(true, true, false, resources);
    }

    /**
     * Creates or updates resources asynchronously, waits for all at end.
     *
     * @param resources The resources to create or update.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createOrUpdateResourceAsyncWait(T... resources) {
        createService().createOrUpdateResource(true, true, true, resources);
    }

    /* ─────────────────  DELETE / REPLACE  ──────────────────── */

    /**
     * Deletes resources with wait asynchronously.
     *
     * @param resources The resources to delete.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void deleteResourceAsyncWait(T... resources) {
        deleteService().deleteResource(true, true, resources);
    }

    /**
     * Deletes resources and waits for each to be fully removed.
     *
     * @param resources The resources to delete.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void deleteResourceWithWait(T... resources) {
        deleteService().deleteResource(false, true, resources);
    }

    /**
     * Deletes resources without waiting for removal.
     *
     * @param resources The resources to delete.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void deleteResourceWithoutWait(T... resources) {
        deleteService().deleteResource(false, false, resources);
    }

    /**
     * Updates resources.
     *
     * @param resources The resources to update.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void updateResource(T... resources) {
        updateService().updateResource(resources);
    }

    /**
     * Replaces a resource with retries on conflict (default 3).
     *
     * @param resource The resource to replace.
     * @param editor   Editor with changes.
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void replaceResourceWithRetries(
        T resource, Consumer<T> editor) {
        updateService().replaceResourceWithRetries(resource, editor);
    }

    /**
     * Replaces a resource with retries on conflict.
     *
     * @param resource The resource to replace.
     * @param editor   Editor with changes.
     * @param retries  Number of retries.
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void replaceResourceWithRetries(
        T resource, Consumer<T> editor, int retries) {
        updateService().replaceResourceWithRetries(resource, editor, retries);
    }

    /**
     * Replaces a resource using the editor.
     *
     * @param resource The resource to replace.
     * @param editor   Editor with changes.
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void replaceResource(
        T resource, Consumer<T> editor) {
        updateService().replaceResource(resource, editor);
    }

    /* ─────────────────  WAIT CONDITIONS  ──────────────────── */

    /**
     * Waits for a resource condition with default timeout.
     *
     * @param resource  The resource to wait for.
     * @param condition The condition to fulfill.
     * @param <T>       The type of the resource.
     * @return true if condition is fulfilled
     */
    public <T extends HasMetadata> boolean waitResourceCondition(
        T resource, ResourceCondition<T> condition) {
        return waitResourceCondition(resource, condition,
            KubeTestConstants.GLOBAL_TIMEOUT);
    }

    /**
     * Waits for a resource condition with custom timeout.
     *
     * @param resource        The resource to wait for.
     * @param condition       The condition to fulfill.
     * @param resourceTimeout Timeout in milliseconds.
     * @param <T>             The type of the resource.
     * @return true if condition is fulfilled
     */
    public <T extends HasMetadata> boolean waitResourceCondition(
        T resource, ResourceCondition<T> condition,
        long resourceTimeout) {
        return waitResourceCondition(resource, condition, resourceTimeout,
            () -> kubeClient().getClient().resource(resource).get());
    }

    /**
     * Waits for a resource condition with custom supplier.
     *
     * @param resource         The resource to wait for.
     * @param condition        The condition to fulfill.
     * @param resourceTimeout  Timeout in milliseconds.
     * @param resourceSupplier Supplier for current resource state.
     * @param <T>              The type of the resource.
     * @return true if condition is fulfilled
     */
    public <T extends HasMetadata> boolean waitResourceCondition(
        T resource, ResourceCondition<T> condition,
        long resourceTimeout,
        java.util.function.Supplier<T> resourceSupplier) {
        assertNotNull(resource);
        assertNotNull(resource.getMetadata());
        assertNotNull(resource.getMetadata().getName());
        boolean[] ready = new boolean[1];
        Wait.until(String.format(
            "Resource condition: %s to be fulfilled for resource %s/%s",
            condition.conditionName(), resource.getKind(),
            resource.getMetadata().getName()),
            KubeTestConstants.GLOBAL_POLL_INTERVAL_MEDIUM,
            resourceTimeout, () -> {
                LOGGER.trace("Obtaining current state of resource: {}/{}",
                    resource.getKind(), resource.getMetadata().getName());
                T r = resourceSupplier.get();
                LOGGER.trace("Finished obtaining resource: {}/{}",
                    resource.getKind(), resource.getMetadata().getName());
                ready[0] = condition.predicate().test(r);
                return ready[0];
            });
        return ready[0];
    }

    /* ─────────────────  BULK CLEANUP  ─────────────────────── */

    /**
     * Deletes all stored resources (async, batch-LIFO order).
     */
    public void deleteResources() {
        deleteService().deleteResources(true);
    }

    /**
     * Deletes all stored resources in batch-LIFO order.
     *
     * @param async if true, items within a batch are deleted concurrently
     */
    public void deleteResources(boolean async) {
        deleteService().deleteResources(async);
    }

    /* ─────────────────  SHARED HELPERS  ────────────────────── */

    /**
     * Finds a registered ResourceType by kind.
     *
     * @param resource the resource to look up
     * @param <T>      resource type
     * @return the ResourceType or null
     */
    @SuppressWarnings("unchecked")
    <T extends HasMetadata> ResourceType<T> findResourceType(T resource) {
        ResourceType<?>[] types = GLOBAL_RESOURCE_TYPES.get();
        for (ResourceType<?> rt : types) {
            if (rt.getKind().equals(resource.getKind())) {
                return (ResourceType<T>) rt;
            }
        }
        return null;
    }

    /**
     * Returns a null-safe description of a resource item for logging.
     *
     * @param item The resource item to describe.
     * @return A string like "Kind namespace/name" or
     *         "&lt;unknown resource&gt;".
     */
    /* test */ static String resourceDescription(ResourceItem<?> item) {
        if (item.resource() == null
            || item.resource().getMetadata() == null) {
            return "<unknown resource>";
        }
        String kind = item.resource().getKind();
        String ns = item.resource().getMetadata().getNamespace();
        String name = item.resource().getMetadata().getName();
        String prefix = kind != null ? kind + " " : "";
        if (ns != null) {
            return prefix + ns + "/" + name;
        }
        return prefix + name;
    }

    /* ────────  TEST-ONLY DELEGATORS (preserve test compat)  ── */

    /**
     * Collects errors from async deletion futures.
     *
     * @param waiters List of futures.
     * @param errors  List to collect exceptions.
     */
    /* test */ void collectAsyncErrors(
        List<CompletableFuture<Void>> waiters, List<Exception> errors) {
        deleteService().collectAsyncErrors(waiters, errors);
    }

    /**
     * Invokes create callbacks safely.
     *
     * @param resource The resource.
     * @param <T>      The type.
     */
    /* test */ <T extends HasMetadata> void invokeCreateCallbacksSafely(
        T resource) {
        createService().invokeCreateCallbacksSafely(resource);
    }

    /**
     * Decides async or sync deletion wait.
     *
     * @param waiters futures list
     * @param async   async flag
     * @param res     the resource
     * @param <T>     resource type
     */
    /* test */ <T extends HasMetadata> void decideDeleteWaitAsync(
        List<CompletableFuture<Void>> waiters, boolean async, T res) {
        deleteService().decideDeleteWaitAsync(waiters, async, res);
    }
}
