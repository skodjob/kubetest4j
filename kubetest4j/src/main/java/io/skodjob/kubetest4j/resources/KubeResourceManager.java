/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.KubeTestEnv;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.clients.cmdClient.Kubectl;
import io.skodjob.kubetest4j.clients.cmdClient.Oc;
import io.skodjob.kubetest4j.environment.TestEnvironmentVariables;
import io.skodjob.kubetest4j.interfaces.ResourceType;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import io.skodjob.kubetest4j.wait.Wait;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 *   KUBECONFIG_PROD = /path/to/prod.kubeconfig  # the highest precedence per context
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
 *
 * <pre>
 * // Default context (backward compatible)
 * KubeResourceManager defaultMgr = KubeResourceManager.get();
 *
 * // Specific context instances
 * KubeResourceManager prodMgr = KubeResourceManager.getForContext("prod");
 * KubeResourceManager stageMgr = KubeResourceManager.getForContext("stage");
 *
 * // Both can be used simultaneously
 * defaultMgr.createResourceWithWait(myDeployment);
 * prodMgr.createResourceWithWait(prodDeployment);
 * </pre>
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
    private static final Map<String, Map<String, Stack<ResourceItem<?>>>> STORED_RESOURCES = new ConcurrentHashMap<>();

    // Lock used during store of resources that are being created by KubeResourceManager
    private static final Object CREATION_LOCK = new Object();

    // Configured maximum number of concurrent async operations.
    private static final AtomicInteger MAX_CONCURRENT_OPERATIONS =
        new AtomicInteger(KubeTestConstants.DEFAULT_MAX_CONCURRENT_OPERATIONS);

    // Semaphore controlling the maximum number of concurrent async operations (create/delete)
    private static final AtomicReference<Semaphore> OPERATION_SEMAPHORE =
        new AtomicReference<>(new Semaphore(KubeTestConstants.DEFAULT_MAX_CONCURRENT_OPERATIONS));

    /**
     * Stores connected kube clients for context
     *
     * @param kubeClient kube client
     * @param cmdClient  cmd client
     */
    private record ClusterContext<K extends KubeCmdClient<K>>(KubeClient kubeClient, K cmdClient) {
    }

    private KubeResourceManager(String contextId) {
        this.contextId = contextId;
    }

    /**
     * Virtual Thread executor concurrency in Kubernetes resource operations.
     */
    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Gets KubeResourceManager instance for the default context.
     * This method preserves backward compatibility.
     *
     * @return singleton instance for default context
     */
    public static KubeResourceManager get() {
        return getForContext(KubeTestConstants.DEFAULT_CONTEXT_NAME);
    }

    /**
     * Gets KubeResourceManager instance for a specific Kubernetes context.
     * Each context has its own singleton instance with separate client connections,
     * but shared configuration (callbacks, resource types, etc.).
     *
     * @param contextId the Kubernetes context identifier
     * @return singleton instance for the specified context
     */
    public static KubeResourceManager getForContext(String contextId) {
        String normalizedContextId = Optional.ofNullable(contextId)
            .orElse(KubeTestConstants.DEFAULT_CONTEXT_NAME)
            .toLowerCase();

        return CONTEXT_INSTANCES.computeIfAbsent(normalizedContextId,
            KubeResourceManager::new);
    }

    /**
     * Set the active context for this thread and auto‑restore on close.
     * This method is maintained for backward compatibility with existing thread-local context switching.
     *
     * @param id name of cluster context
     * @return context
     */
    public AutoCloseable useContext(String id) {
        String ctxId = Optional.ofNullable(id).orElse(KubeTestConstants.DEFAULT_CONTEXT_NAME).toLowerCase();
        if (!CLUSTER_CONFIGS.containsKey(ctxId)) {
            throw new IllegalArgumentException("Unknown context '" + ctxId +
                "'. Define env vars [KUBE_URL|KUBE_TOKEN|KUBECONFIG]_" + ctxId.toUpperCase());
        }
        LOGGER.info("Switching to context {}", ctxId);
        String prev = CURRENT_CLUSTER_CONTEXT.get();
        CURRENT_CLUSTER_CONTEXT.set(ctxId);
        return () -> {
            LOGGER.info("Closing context {}", ctxId);
            CURRENT_CLUSTER_CONTEXT.set(prev);
        };
    }

    /**
     * Creates context for cluster id and connect clients
     *
     * @param id id of cluster
     * @return context
     */
    private ClusterContext<? extends KubeCmdClient<?>> clusterContext(String id) {
        return clientCache.computeIfAbsent(id, cid -> {
            TestEnvironmentVariables.ClusterConfig c = CLUSTER_CONFIGS.get(cid);
            if (c == null) {
                throw new IllegalStateException("Credentials missing for context " + cid);
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

    /**
     * Gets current context for this instance
     *
     * @return context
     */
    private ClusterContext<? extends KubeCmdClient<?>> clusterContext() {
        return clusterContext(this.contextId);
    }

    /* ───────────────  kube clients accessors  ─────────────── */

    /**
     * Returns kube client for current context
     *
     * @return kube client
     */
    public KubeClient kubeClient() {
        return clusterContext().kubeClient;
    }

    /**
     * Returns kube cmd client for current context
     *
     * @param <K> Type extending {@link KubeCmdClient}
     * @return kube cmd client
     */
    @SuppressWarnings("unchecked")
    public <K extends KubeCmdClient<K>> K kubeCmdClient() {
        return (K) clusterContext().cmdClient;
    }

    /**
     * Set path for storing yaml resources (applies to all contexts)
     *
     * @param path root path for storing
     */
    public void setStoreYamlPath(String path) {
        globalStoreYamlPath = path;
    }

    /**
     * Returns root path of stored yaml resources
     *
     * @return path
     */
    public String getStoreYamlPath() {
        return globalStoreYamlPath;
    }

    /**
     * Returns the currently registered resource types.
     *
     * @return array of registered resource types (may be empty, never null)
     */
    public ResourceType<?>[] getResourceTypes() {
        return GLOBAL_RESOURCE_TYPES.get();
    }

    /**
     * Add resource types for special handling by resource manager (applies to all contexts)
     *
     * @param types resource types implementation
     */
    public void setResourceTypes(ResourceType<?>... types) {
        GLOBAL_RESOURCE_TYPES.set(types);
    }

    /**
     * Adds callback which is called after every created resource (applies to all contexts)
     *
     * @param cb callback
     */
    public void addCreateCallback(Consumer<HasMetadata> cb) {
        GLOBAL_CREATE_CALLBACKS.add(cb);
    }

    /**
     * Adds delete callback which is called after every deletion of resource (applies to all contexts)
     *
     * @param cb callback
     */
    public void addDeleteCallback(Consumer<HasMetadata> cb) {
        GLOBAL_DELETE_CALLBACKS.add(cb);
    }

    /**
     * Clears all singleton instances, forcing fresh instances on next access.
     * This is needed to isolate mock tests from real client state left by
     * integration-style tests sharing the same JVM.
     */
    /* test */ static void clearInstances() {
        CONTEXT_INSTANCES.clear();
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
     * Sets the maximum number of concurrent async operations (create/delete) against the Kubernetes API.
     *
     * @param maxConcurrentOps maximum number of concurrent operations
     */
    public void setMaxConcurrentOperations(int maxConcurrentOps) {
        if (maxConcurrentOps <= 0) {
            throw new IllegalArgumentException(
                "maxConcurrentOperations must be positive, got: " + maxConcurrentOps);
        }
        MAX_CONCURRENT_OPERATIONS.set(maxConcurrentOps);
        OPERATION_SEMAPHORE.set(new Semaphore(maxConcurrentOps));
        LOGGER.info("Max concurrent operations set to {}", maxConcurrentOps);
    }

    /**
     * Returns the configured maximum number of concurrent async operations.
     *
     * @return configured max concurrent operations
     */
    public int getMaxConcurrentOperations() {
        return MAX_CONCURRENT_OPERATIONS.get();
    }

    /**
     * Sets test extension context
     *
     * @param ctx extension context
     */
    public void setTestContext(ExtensionContext ctx) {
        TEST_CONTEXT.set(ctx);
    }

    /**
     * Returns extension context for current test
     *
     * @return extension context
     */
    public ExtensionContext getTestContext() {
        return TEST_CONTEXT.get();
    }

    /**
     * Clean test extension context
     */
    public void cleanTestContext() {
        TEST_CONTEXT.remove();
    }

    /**
     * Clean test extension context
     */
    public void cleanClusterContext() {
        CURRENT_CLUSTER_CONTEXT.remove();
    }

    /**
     * Pushes a resource to the stack.
     *
     * @param resource The resource to push.
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void pushToStack(T resource) {
        STORED_RESOURCES
            .computeIfAbsent(this.contextId, c -> new ConcurrentHashMap<>())
            .computeIfAbsent(getTestContext().getDisplayName(), t -> new Stack<>())
            .push(new ResourceItem<>(() -> deleteResourceWithWait(resource), resource));
    }

    /**
     * Pushes a resource item to the stack.
     *
     * @param item The resource item to push.
     */
    public void pushToStack(ResourceItem<?> item) {
        STORED_RESOURCES
            .computeIfAbsent(this.contextId, c -> new ConcurrentHashMap<>())
            .computeIfAbsent(getTestContext().getDisplayName(), t -> new Stack<>())
            .push(item);
    }

    /**
     * Removes a resource from the stack.
     *
     * @param resource The resource to remove.
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void removeFromStack(T resource) {
        Map<String, Stack<ResourceItem<?>>> byTest = STORED_RESOURCES.get(this.contextId);
        if (byTest == null) {
            return;
        }
        ExtensionContext ctx = getTestContext();
        if (ctx == null) {
            return;
        }
        Stack<ResourceItem<?>> stack = byTest.get(ctx.getDisplayName());
        if (stack == null) {
            return;
        }
        stack.removeIf(item -> item.resource() != null
            && item.resource().getKind().equals(resource.getKind())
            && Objects.equals(item.resource().getMetadata().getNamespace(), resource.getMetadata().getNamespace())
            && item.resource().getMetadata().getName().equals(resource.getMetadata().getName()));
    }

    /* ─────────────────────────  RESOURCE I/O HELPERS  ─────────────────────── */

    /**
     * Reads Kubernetes resources from a file at the specified path.
     *
     * @param file The path to the file containing Kubernetes resources.
     * @return A list of {@link HasMetadata} resources defined in the file.
     * @throws IOException If an I/O error occurs reading from the file.
     */
    public List<HasMetadata> readResourcesFromFile(Path file) throws IOException {
        return kubeClient().readResourcesFromFile(file);
    }

    /**
     * Reads Kubernetes resources from an InputStream.
     *
     * @param is The InputStream containing Kubernetes resources.
     * @return A list of {@link HasMetadata} resources defined in the stream.
     * @throws IOException If an I/O error occurs.
     */
    public List<HasMetadata> readResourcesFromFile(InputStream is) throws IOException {
        return kubeClient().readResourcesFromFile(is);
    }

    /* ───────────────────────────  LOGGING HELPERS  ─────────────────────────── */


    /**
     * Logs all managed resources across all test contexts with a set log level
     *
     * @param logLevel slf4j log level event
     */
    public void printAllResources(Level logLevel) {
        LOGGER.atLevel(logLevel).log("Printing all managed resources across all contexts");
        STORED_RESOURCES.forEach((ctxId, byTest) -> {
            LOGGER.atLevel(logLevel).log("Context [{}]", ctxId);
            byTest.forEach((test, stack) -> {
                LOGGER.atLevel(logLevel).log("  Test: {}", test);
                stack.forEach(item -> Optional.ofNullable(item.resource())
                    .ifPresent(r -> LoggerUtils.logResource("Managed resource:", logLevel, r)));
            });
        });
    }

    /**
     * Logs all managed resources in current test context with set log level
     *
     * @param logLevel slf4j log level event
     */
    public void printCurrentResources(Level logLevel) {
        String ctxId = this.contextId;
        String test = getTestContext().getDisplayName();
        LOGGER.atLevel(logLevel).log("Resources in [{}]/{}", ctxId, test);
        Optional.ofNullable(STORED_RESOURCES.get(ctxId))
            .map(m -> m.get(test))
            .ifPresent(stack -> stack.forEach(i ->
                Optional.ofNullable(i.resource()).ifPresent(r ->
                    LoggerUtils.logResource("Managed resource:", logLevel, r))));
    }

    /**
     * Returns a list of all resources currently tracked for the active test context.
     *
     * @return list of tracked resources for the current test
     */
    public List<HasMetadata> getCurrentResources() {
        String test = getTestContext().getDisplayName();
        return Optional.ofNullable(STORED_RESOURCES.get(this.contextId))
            .map(m -> m.get(test))
            .map(stack -> {
                List<HasMetadata> resources = new ArrayList<>();
                for (ResourceItem<?> item : stack) {
                    if (item.resource() != null) {
                        resources.add(item.resource());
                    }
                }
                return Collections.unmodifiableList(resources);
            })
            .orElse(Collections.emptyList());
    }

    /* ──────────────────  CREATE / UPDATE / DELETE IMPLEMENTATION  ─────────── */

    // ---------------------------  Resource create  ---------------------------

    /**
     * Creates resources without waiting for readiness.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createResourceWithoutWait(T... resources) {
        createOrUpdateResource(false, false, false, resources);
    }

    /**
     * Creates resources and waits for readiness.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createResourceWithWait(T... resources) {
        createOrUpdateResource(false, true, false, resources);
    }

    /**
     * Creates or updates resources and waits for readiness.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createOrUpdateResourceWithWait(T... resources) {
        createOrUpdateResource(false, true, true, resources);
    }

    /**
     * Creates or updates resources.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createOrUpdateResourceWithoutWait(T... resources) {
        createOrUpdateResource(false, false, true, resources);
    }

    /**
     * Creates resources and wait on the end for all readiness.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createResourceAsyncWait(T... resources) {
        createOrUpdateResource(true, true, false, resources);
    }

    /**
     * Creates or updates resources and wait on the end for all readiness.
     *
     * @param resources The resources to create.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void createOrUpdateResourceAsyncWait(T... resources) {
        createOrUpdateResource(true, true, true, resources);
    }

    /**
     * Creates resources with or without waiting for readiness.
     *
     * @param async       Flag waiting for all resources on the end
     * @param waitReady   Flag indicating whether to wait for readiness.
     * @param allowUpdate Flag indicating if update resource is allowed
     * @param resources   The resources to create.
     * @param <T>         The type of the resources.
     */
    @SafeVarargs
    private <T extends HasMetadata> void createOrUpdateResource(
        boolean async, boolean waitReady, boolean allowUpdate, T... resources) {
        List<CompletableFuture<Void>> promises = new ArrayList<>();

        for (T resource : resources) {
            ResourceType<T> type = findResourceType(resource);
            pushToStack(resource);
            if (globalStoreYamlPath != null) {
                writeResourceAsYaml(resource);
            }

            if (type == null) {
                promises.add(createOrUpdateResource(async, waitReady, allowUpdate, resource));
            } else {
                promises.add(createOrUpdateResource(async, waitReady, allowUpdate, resource, type));
            }
        }

        try {
            CompletableFuture.allOf(promises.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            LOGGER.error("Exception during wait for resources to be ready", cause);
            throw new IllegalStateException(cause.getMessage(), cause);
        }
    }

    /**
     * Creates a single resource with or without waiting for readiness.
     *
     * @param async       Flag waiting for all resources on the end
     * @param waitReady   Flag indicating whether to wait for readiness.
     * @param allowUpdate Flag indicating if update resource is allowed
     * @param resource    The resource to create.
     * @param <T>         The type of the resources.
     * @return a CompletableFuture promise that will complete when the resource is
     *         created/updated or when the resource becomes ready, if waitReady is
     *         true.
     */
    private <T extends HasMetadata> CompletableFuture<Void> createOrUpdateResource(
            boolean async, boolean waitReady, boolean allowUpdate, T resource) {

        CompletableFuture<Void> promise = CompletableFuture.runAsync(() -> {
            Semaphore sem = OPERATION_SEMAPHORE.get();
            sem.acquireUninterruptibly();
            try {
                if (allowUpdate && kubeClient().getClient().resource(resource).get() != null) {
                    LoggerUtils.logResource("Updating", resource);
                    kubeClient().getClient().resource(resource).update();
                } else {
                    LoggerUtils.logResource("Creating", resource);
                    kubeClient().getClient().resource(resource).create();
                }
            } finally {
                sem.release();
            }
            invokeCreateCallbacksSafely(resource);
        }, EXECUTOR);

        if (!waitReady) {
            return promise;
        }

        promise = promise.thenRunAsync(() -> {
            Semaphore sem = OPERATION_SEMAPHORE.get();
            sem.acquireUninterruptibly();
            try {
                assertTrue(waitResourceCondition(resource,
                        new ResourceCondition<>(p -> {
                            if (isResourceWithReadiness(resource)) {
                                return kubeClient().getClient().resource(resource).isReady();
                            }
                            return kubeClient().getClient().resource(resource) != null;
                        }, "ready")),
                    "Timed out waiting for " + resource.getKind() + "/" +
                        resource.getMetadata().getName());
            } finally {
                sem.release();
            }
        }, EXECUTOR);

        if (async) {
            return promise;
        } else {
            promise.whenComplete((nothing, error) -> {
                if (error != null) {
                    LOGGER.error("Exception during wait for resource {}/{} to be ready",
                        resource.getMetadata().getNamespace(),
                        resource.getMetadata().getName(),
                        error
                    );
                }
            }).join(); // will throw CompletionException when error != null

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Creates a single resource with or without waiting for readiness.
     *
     * @param async       Flag waiting for all resources on the end
     * @param waitReady   Flag indicating whether to wait for readiness.
     * @param allowUpdate Flag indicating if update resource is allowed
     * @param resource    The resource to create.
     * @param type        The resource type helper
     * @param <T>         The type of the resources.
     * @return a CompletableFuture promise that will complete when the resource is
     *         created/updated or when the resource becomes ready, if waitReady is
     *         true.
     */
    private <T extends HasMetadata> CompletableFuture<Void> createOrUpdateResource(
            boolean async, boolean waitReady, boolean allowUpdate, T resource, ResourceType<T> type) {

        CompletableFuture<Void> promise = CompletableFuture.runAsync(() -> {
            Semaphore sem = OPERATION_SEMAPHORE.get();
            sem.acquireUninterruptibly();
            try {
                if (allowUpdate && kubeClient().getClient().resource(resource).get() != null) {
                    LoggerUtils.logResource("Updating", resource);
                    type.update(resource);
                } else {
                    LoggerUtils.logResource("Creating", resource);
                    type.create(resource);
                }
            } finally {
                sem.release();
            }
            invokeCreateCallbacksSafely(resource);
        }, EXECUTOR);

        if (!waitReady) {
            return promise;
        }

        long timeout = Objects.requireNonNullElse(type.getTimeoutForResourceReadiness(),
            KubeTestConstants.GLOBAL_TIMEOUT_MEDIUM);

        promise = promise.thenRunAsync(() -> {
            Semaphore sem = OPERATION_SEMAPHORE.get();
            sem.acquireUninterruptibly();
            try {
                assertTrue(waitResourceCondition(resource, ResourceCondition.readiness(type), timeout),
                    "Timed out waiting for " + resource.getKind() + "/" +
                        resource.getMetadata().getName());
            } finally {
                sem.release();
            }
        }, EXECUTOR);

        if (async) {
            return promise;
        } else {
            promise.whenComplete((nothing, error) -> {
                if (error != null) {
                    LOGGER.error("Exception during wait for resource {}/{} to be ready",
                        resource.getMetadata().getNamespace(),
                        resource.getMetadata().getName(),
                        error
                    );
                }
            }).join(); // will throw CompletionException when error != null

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Deletes resources with wait asynchronously.
     *
     * @param resources The resources to delete.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void deleteResourceAsyncWait(T... resources) {
        deleteResource(true, true, resources);
    }

    /**
     * Deletes resources with wait.
     *
     * @param resources The resources to delete.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void deleteResourceWithWait(T... resources) {
        deleteResource(false, true, resources);
    }

    /**
     * Deletes resources without wait.
     *
     * @param resources The resources to delete.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void deleteResourceWithoutWait(T... resources) {
        deleteResource(false, false, resources);
    }

    /**
     * Deletes resources.
     *
     * @param async           Enables async deletion.
     * @param waitForDeletion Flag indicating whether to wait for resource deletion.
     * @param resources       The resources to delete.
     * @param <T>             The type of the resources.
     */
    @SafeVarargs
    private <T extends HasMetadata> void deleteResource(boolean async, boolean waitForDeletion, T... resources) {
        List<CompletableFuture<Void>> waiters = new ArrayList<>();
        for (T resource : resources) {
            ResourceType<T> type = findResourceType(resource);
            LoggerUtils.logResource("Deleting", resource);
            try {
                if (type == null) {
                    kubeClient().getClient().resource(resource).delete();
                } else {
                    type.delete(resource);
                }

                removeFromStack(resource);

                if (waitForDeletion) {
                    decideDeleteWaitAsync(waiters, async, resource);
                }

                for (Consumer<HasMetadata> cb : GLOBAL_DELETE_CALLBACKS) {
                    try {
                        cb.accept(resource);
                    } catch (Exception cbEx) {
                        LOGGER.warn("Delete callback failed for {}/{}: {}",
                            resource.getKind(), resource.getMetadata().getName(),
                            cbEx.getMessage(), cbEx);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Deletion of {}/{} failed with the following error: {}",
                    resource.getKind(), resource.getMetadata().getName(), e.getMessage(), e);
            }
        }

        List<Exception> errors = new ArrayList<>();
        collectAsyncErrors(waiters, errors);
        if (!errors.isEmpty()) {
            RuntimeException composite = new RuntimeException(
                "Failed to delete " + errors.size() + " resource(s)");
            errors.forEach(composite::addSuppressed);
            throw composite;
        }
    }

    /**
     * Updates resources.
     *
     * @param resources The resources to update.
     * @param <T>       The type of the resources.
     */
    @SafeVarargs
    public final <T extends HasMetadata> void updateResource(T... resources) {
        for (T resource : resources) {
            LoggerUtils.logResource("Updating", resource);
            ResourceType<T> type = findResourceType(resource);
            if (type != null) {
                type.update(resource);
            } else {
                kubeClient().getClient().resource(resource).update();
            }
        }
    }

    /**
     * Method for replacing the resource with retries.
     * It uses {@link #replaceResource(HasMetadata, Consumer)} in try-catch block and in case
     * that the exception is thrown, it checks if we got conflict exception (meaning that we
     * should re-apply the changes on updated resource).
     * Otherwise, the {@link RuntimeException} is thrown (as we are not in conflict and there
     * is something else).
     * This encapsulates {@link #replaceResourceWithRetries(HasMetadata, Consumer, int)}
     * where the default number of retries is 3.
     *
     * @param resource The resource that should be updated.
     * @param editor   Editor containing all changes that should be propagated to resource
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void replaceResourceWithRetries(T resource, Consumer<T> editor) {
        replaceResourceWithRetries(resource, editor, 3);
    }

    /**
     * Method for replacing the resource with retries.
     * It uses {@link #replaceResource(HasMetadata, Consumer)} in try-catch block and in case
     * that the exception is thrown, it checks if we got conflict exception (meaning that we
     * should re-apply the changes on updated resource).
     * Otherwise, the {@link RuntimeException} is thrown (as we are not in conflict and there
     * is something else).
     * This is retried for number of times specified in {@param retries}.
     *
     * @param resource The resource that should be updated.
     * @param editor   Editor containing all changes that should be propagated to resource
     * @param retries  Number of retries with which we should try to replace the resource
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void replaceResourceWithRetries(T resource, Consumer<T> editor, int retries) {
        int attempt = 0;
        while (true) {
            try {
                replaceResource(resource, editor);
                return;
            } catch (CompletionException ce) {
                Throwable cause = ce.getCause();
                if (isNotConflict(cause) || ++attempt >= retries) {
                    throw (cause instanceof RuntimeException re) ? re : new RuntimeException(cause);
                }
            } catch (KubernetesClientException kce) {
                if (isNotConflict(kce) || ++attempt >= retries) {
                    throw kce;
                }
            }
        }
    }

    /**
     * Checks if the {@link Throwable} is NOT a conflict (HTTP 409) from the Kubernetes API,
     * meaning the error should be propagated immediately rather than retried.
     *
     * @param t throwable thrown during operation
     * @return {@code true} if the error is not a 409 conflict and should be thrown,
     *         {@code false} if it is a 409 conflict and the operation can be retried
     */
    private static boolean isNotConflict(Throwable t) {
        return !(t instanceof KubernetesClientException kce && kce.getCode() == 409);
    }

    /**
     * Based on {@param resource} and {@param editor} replaces the current resource.
     * In case that the {@link ResourceType} is not found, the default client is used.
     *
     * @param resource The resource that should be updated.
     * @param editor   Editor containing all changes that should be propagated to resource
     * @param <T>      The type of the resource.
     */
    public <T extends HasMetadata> void replaceResource(T resource, Consumer<T> editor) {
        ResourceType<T> type = findResourceType(resource);
        if (type != null) {
            type.replace(resource, editor);
        } else {
            T current = kubeClient().getClient().resource(resource).get();
            editor.accept(current);
            kubeClient().getClient().resource(current).update();
        }
    }

    // ---------------------------  Wait condition -----------------------------

    /**
     * Waits for a resource condition to be fulfilled.
     *
     * @param resource  The resource to wait for.
     * @param condition The condition to fulfill.
     * @param <T>       The type of the resource.
     * @return True if the condition is fulfilled, false otherwise.
     */
    public <T extends HasMetadata> boolean waitResourceCondition(T resource, ResourceCondition<T> condition) {
        return waitResourceCondition(resource, condition, KubeTestConstants.GLOBAL_TIMEOUT);
    }

    /**
     * Waits for a resource condition to be fulfilled.
     *
     * @param resource        The resource to wait for.
     * @param condition       The condition to fulfill.
     * @param <T>             The type of the resource.
     * @param resourceTimeout Timeout for resource condition
     * @return True if the condition is fulfilled, false otherwise.
     */
    public <T extends HasMetadata> boolean waitResourceCondition(
        T resource, ResourceCondition<T> condition, long resourceTimeout) {
        return waitResourceCondition(resource, condition, resourceTimeout,
            () -> kubeClient().getClient().resource(resource).get());
    }

    /**
     * Waits for a resource condition to be fulfilled.
     *
     * @param resource         The resource to wait for.
     * @param condition        The condition to fulfill.
     * @param <T>              The type of the resource.
     * @param resourceTimeout  Timeout for resource condition
     * @param resourceSupplier Supplier with method for obtaining the resource - using client, or a different way.
     * @return True if the condition is fulfilled, false otherwise.
     */
    public <T extends HasMetadata> boolean waitResourceCondition(
        T resource, ResourceCondition<T> condition, long resourceTimeout, Supplier<T> resourceSupplier) {
        assertNotNull(resource);
        assertNotNull(resource.getMetadata());
        assertNotNull(resource.getMetadata().getName());
        boolean[] ready = new boolean[1];
        Wait.until(String.format("Resource condition: %s to be fulfilled for resource %s/%s",
                condition.conditionName(), resource.getKind(), resource.getMetadata().getName()),
            KubeTestConstants.GLOBAL_POLL_INTERVAL_MEDIUM, resourceTimeout, () -> {
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

    /* --------------------------  DELETE ALL RESOURCES ----------------------- */

    /**
     * Deletes all stored resources.
     */
    public void deleteResources() {
        deleteResources(true);
    }

    /**
     * Deletes all stored resources.
     * The method continues deleting remaining resources even if individual deletions fail.
     * After all resources are attempted, a composite exception is thrown if any deletions failed.
     *
     * @param async sets async or sequential deletion
     */
    public void deleteResources(boolean async) {
        String ctxId = this.contextId;
        String testName = getTestContext().getDisplayName();
        Map<String, Stack<ResourceItem<?>>> byTest = STORED_RESOURCES.get(ctxId);
        if (byTest == null || byTest.get(testName) == null || byTest.get(testName).isEmpty()) {
            LOGGER.info("No resources to delete for [{}]/{}", ctxId, testName);
            return;
        }
        LoggerUtils.logSeparator();
        LOGGER.info("Deleting all resources for [{}]/{}", ctxId, testName);
        Stack<ResourceItem<?>> stack = byTest.get(testName);
        List<CompletableFuture<Void>> waiters = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();
        try {
            while (!stack.isEmpty()) {
                ResourceItem<?> item = stack.pop();
                CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
                    Semaphore sem = OPERATION_SEMAPHORE.get();
                    sem.acquireUninterruptibly();
                    try {
                        item.throwableRunner().run();
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage(), e);
                    } finally {
                        sem.release();
                    }
                }, EXECUTOR);
                if (async) {
                    waiters.add(cf);
                } else {
                    String resNs = item.resource() != null
                        && item.resource().getMetadata() != null
                        ? item.resource().getMetadata().getNamespace() : null;
                    String resName = item.resource() != null
                        && item.resource().getMetadata() != null
                        ? item.resource().getMetadata().getName() : null;
                    try {
                        cf.get(KubeTestConstants.GLOBAL_TIMEOUT, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        LOGGER.error("Timeout waiting for deletion of resource {}/{}",
                            resNs, resName, e);
                        errors.add(e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.error("Interrupted during deletion of resource {}/{}",
                            resNs, resName, e);
                        errors.add(e);
                    } catch (ExecutionException e) {
                        LOGGER.error("Exception during deletion of resource {}/{}",
                            resNs, resName, e);
                        errors.add(e);
                    }
                }
            }
            collectAsyncErrors(waiters, errors);
        } finally {
            byTest.remove(testName);
            if (byTest.isEmpty()) {
                STORED_RESOURCES.remove(ctxId);
            }
            LoggerUtils.logSeparator();
        }
        if (!errors.isEmpty()) {
            RuntimeException composite = new RuntimeException(
                "Failed to delete " + errors.size() + " resource(s)");
            errors.forEach(composite::addSuppressed);
            throw composite;
        }
    }

    /**
     * Collects errors from async deletion futures without throwing.
     *
     * @param waiters List of {@link CompletableFuture} from async deletions.
     * @param errors  List to collect any exceptions into.
     */
    /* test */ void collectAsyncErrors(List<CompletableFuture<Void>> waiters,
                                       List<Exception> errors) {
        if (waiters.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(waiters.toArray(new CompletableFuture[0]))
                .get(KubeTestConstants.GLOBAL_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            collectIndividualFutureErrors(waiters, errors);
            if (errors.isEmpty()) {
                errors.add(e);
            }
        } catch (TimeoutException | ExecutionException e) {
            collectIndividualFutureErrors(waiters, errors);
            if (errors.isEmpty()) {
                errors.add(e);
            }
        }
    }

    /**
     * Iterates individual futures and collects exceptions from any that completed exceptionally.
     *
     * @param waiters List of completed or timed-out futures.
     * @param errors  List to collect exceptions into.
     */
    private void collectIndividualFutureErrors(List<CompletableFuture<Void>> waiters,
                                               List<Exception> errors) {
        for (CompletableFuture<Void> future : waiters) {
            if (future.isCompletedExceptionally()) {
                try {
                    future.getNow(null);
                } catch (Exception individual) {
                    errors.add(individual);
                }
            }
        }
    }

    /**
     * Invokes create callbacks for the given resource, catching any exceptions to prevent
     * a failing callback from aborting creation of remaining resources.
     *
     * @param resource The resource whose create callbacks should be invoked.
     * @param <T>      The type of the resource.
     */
    /* test */ <T extends HasMetadata> void invokeCreateCallbacksSafely(T resource) {
        for (Consumer<HasMetadata> cb : GLOBAL_CREATE_CALLBACKS) {
            try {
                cb.accept(resource);
            } catch (Exception e) {
                LOGGER.warn("Create callback failed for {}/{}: {}",
                    resource.getKind(), resource.getMetadata().getName(),
                    e.getMessage(), e);
            }
        }
    }

    /**
     * Returns a null-safe description of a resource item for logging.
     *
     * @param item The resource item to describe.
     * @return A string like "namespace/name" or "&lt;unknown resource&gt;".
     */
    /* test */ static String resourceDescription(ResourceItem<?> item) {
        if (item.resource() == null || item.resource().getMetadata() == null) {
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

    /**
     * Return ResourceType implementation if it is specified in resourceTypes based on kind
     *
     * @param resource HasMetadata resource to find
     * @param <T>      The type of the resource.
     * @return {@link ResourceType}
     */
    @SuppressWarnings("unchecked")
    private <T extends HasMetadata> ResourceType<T> findResourceType(T resource) {
        ResourceType<?>[] types = GLOBAL_RESOURCE_TYPES.get();
        for (ResourceType<?> rt : types) {
            if (rt.getKind().equals(resource.getKind())) {
                return (ResourceType<T>) rt;
            }
        }
        return null;
    }

    private <T extends HasMetadata> boolean isResourceWithReadiness(T resource) {
        return resource instanceof Deployment ||
            resource instanceof io.fabric8.kubernetes.api.model.extensions.Deployment ||
            resource instanceof ReplicaSet ||
            resource instanceof Pod ||
            resource instanceof ReplicationController ||
            resource instanceof Endpoints ||
            resource instanceof Node ||
            resource instanceof StatefulSet;
    }

    private void writeResourceAsYaml(HasMetadata res) {
        synchronized (CREATION_LOCK) {
            File dir = Paths.get(globalStoreYamlPath).resolve("test-files").resolve(this.contextId)
                .resolve(getTestContext().getRequiredTestClass().getName())
                .toFile();
            if (getTestContext().getTestMethod().isPresent()) {
                dir = dir.toPath().resolve(getTestContext().getRequiredTestMethod().getName()).toFile();
            } else {
                dir = dir.toPath().resolve("before-all").toFile();
            }
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("Cannot create dir " + dir);
            }
            String yaml = Serialization.asYaml(res);
            try {
                Files.writeString(dir.toPath().resolve(res.getKind() + "-" +
                    (res.getMetadata().getNamespace() == null ? "" : res.getMetadata().getNamespace() + "-") +
                    res.getMetadata().getName() + ".yaml"), yaml, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    /* test */ <T extends HasMetadata> void decideDeleteWaitAsync(
        List<CompletableFuture<Void>> waiters, boolean async, T res) {
        CompletableFuture<Void> cf;
        if (async) {
            cf = CompletableFuture.runAsync(() -> {
                Semaphore sem = OPERATION_SEMAPHORE.get();
                sem.acquireUninterruptibly();
                try {
                    assertTrue(waitResourceCondition(res, ResourceCondition.deletion()),
                        "Timed out deleting " + res.getKind() + "/" + res.getMetadata().getName());
                } finally {
                    sem.release();
                }
            }, EXECUTOR);
        } else {
            cf = CompletableFuture.runAsync(() ->
                assertTrue(waitResourceCondition(res, ResourceCondition.deletion()),
                    "Timed out deleting " + res.getKind() + "/" + res.getMetadata().getName()), EXECUTOR);
        }
        if (async) {
            waiters.add(cf);
        } else {
            try {
                cf.get(KubeTestConstants.GLOBAL_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                LOGGER.error("Timeout waiting for deletion of resource {}/{}",
                    res.getMetadata().getNamespace(),
                    res.getMetadata().getName(),
                    e
                );
                throw new IllegalStateException(e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e.getMessage(), e);
            } catch (ExecutionException e) {
                LOGGER.error("Exception during wait for resource {}/{} to be deleted",
                    res.getMetadata().getNamespace(),
                    res.getMetadata().getName(),
                    e
                );
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }
}
