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
import io.fabric8.kubernetes.client.utils.Serialization;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.interfaces.ResourceType;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handles resource creation and update execution with async orchestration.
 *
 * <p>Package-private — accessed only through {@link KubeResourceManager}.
 */
final class ResourceCreateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceCreateService.class);

    private final KubeResourceManager manager;

    ResourceCreateService(KubeResourceManager manager) {
        this.manager = manager;
    }

    // ───────────────────────  CORE CREATE/UPDATE  ──────────────

    /**
     * Creates (or updates) resources and tracks them for cleanup.
     *
     * <p>Resources with only {@code metadata.generateName} are created from a copy with a generated
     * name; the caller's object is not modified. They are always created, never updated, even by the
     * create-or-update variants. A resource with neither {@code metadata.name} nor
     * {@code metadata.generateName} is rejected with an {@link IllegalArgumentException} before
     * anything is tracked or sent to the API server.
     */
    @SafeVarargs
    final <T extends HasMetadata> void createOrUpdateResource(
        boolean async, boolean waitReady, boolean allowUpdate, T... resources) {

        // Resolve names before anything is tracked: cleanup must delete by name, never the whole collection.
        List<Planned<T>> planned = Arrays.stream(resources)
            .map(resource -> plan(resource, allowUpdate))
            .toList();

        ResourceTrackerService tracker = manager.tracker();
        List<ResourceItem<?>> items = planned.stream().<ResourceItem<?>>map(Planned::item).toList();
        if (tracker.isExplicitBatchOpen()) {
            items.forEach(tracker::addToCurrentBatch);
        } else if (!items.isEmpty()) {
            tracker.pushBatchToDeque(new ResourceBatch(items));
        }

        // A loop, not a stream: start() has side effects and blocks per resource in sync mode.
        List<CompletableFuture<Void>> promises = new ArrayList<>(planned.size());
        for (Planned<T> next : planned) {
            promises.add(start(next, async, waitReady));
        }

        try {
            CompletableFuture.allOf(
                promises.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            LOGGER.error(
                "Exception during wait for resources to be ready", cause);
            throw new IllegalStateException(cause.getMessage(), cause);
        }
    }

    /**
     * One resource of a create call.
     *
     * @param resource  the object sent to the API server (a named copy for {@code generateName} input)
     * @param item      the item that tracks the resource for cleanup
     * @param mayUpdate whether an existing object with the same name may be updated
     * @param <T>       the resource type
     */
    private record Planned<T extends HasMetadata>(T resource, ResourceItem<T> item, boolean mayUpdate) {
    }

    private <T extends HasMetadata> Planned<T> plan(T resource, boolean allowUpdate) {
        T named = ResourceNames.withResolvedName(resource);
        KubeResourceManager activeManager = KubeResourceManager.getForContext(manager.activeContextId());
        ResourceItem<T> item = new ResourceItem<>(() -> activeManager.deleteResourceWithWait(named), named);
        // A generated name is new by definition: an existing object with that name (a collision)
        // belongs to someone else, so a generated resource is only ever created, never updated.
        return new Planned<>(named, item, allowUpdate && named == resource);
    }

    private <T extends HasMetadata> CompletableFuture<Void> start(Planned<T> planned, boolean async,
                                                                  boolean waitReady) {
        T resource = planned.resource();
        ResourceType<T> type = manager.findResourceType(resource);
        if (manager.getStoreYamlPath() != null) {
            writeResourceAsYaml(resource);
        }
        return type == null
            ? createOrUpdateSingle(async, waitReady, planned.mayUpdate(), resource)
            : createOrUpdateSingle(async, waitReady, planned.mayUpdate(), resource, type);
    }

    private <T extends HasMetadata> CompletableFuture<Void> createOrUpdateSingle(
        boolean async, boolean waitReady, boolean allowUpdate, T resource) {

        KubeResourceManager.ThreadContext threadContext =
            KubeResourceManager.captureThreadContext();
        CompletableFuture<Void> promise = CompletableFuture.runAsync(
            KubeResourceManager.withThreadContext(threadContext, () -> {
                Semaphore sem = KubeResourceManager.operationSemaphore().get();
                sem.acquireUninterruptibly();
                try {
                    if (allowUpdate && manager.kubeClient().getClient()
                        .resource(resource).get() != null) {
                        LoggerUtils.logResource("Updating", resource);
                        manager.kubeClient().getClient()
                            .resource(resource).update();
                    } else {
                        LoggerUtils.logResource("Creating", resource);
                        manager.kubeClient().getClient()
                            .resource(resource).create();
                    }
                } finally {
                    sem.release();
                }
                invokeCreateCallbacksSafely(resource);
            }), KubeResourceManager.executor());

        if (!waitReady) {
            return promise;
        }

        promise = promise.thenRunAsync(
            KubeResourceManager.withThreadContext(threadContext, () -> {
                Semaphore sem = KubeResourceManager.operationSemaphore().get();
                sem.acquireUninterruptibly();
                try {
                    assertTrue(manager.waitResourceCondition(resource,
                        new ResourceCondition<>(p -> {
                            if (isResourceWithReadiness(resource)) {
                                return manager.kubeClient().getClient()
                                    .resource(resource).isReady();
                            }
                            return manager.kubeClient().getClient()
                                .resource(resource) != null;
                        }, "ready")),
                        "Timed out waiting for " + resource.getKind() + "/"
                            + resource.getMetadata().getName());
                } finally {
                    sem.release();
                }
            }), KubeResourceManager.executor());

        return joinIfSync(async, promise, resource);
    }

    private <T extends HasMetadata> CompletableFuture<Void> createOrUpdateSingle(
        boolean async, boolean waitReady, boolean allowUpdate,
        T resource, ResourceType<T> type) {

        KubeResourceManager.ThreadContext threadContext =
            KubeResourceManager.captureThreadContext();
        CompletableFuture<Void> promise = CompletableFuture.runAsync(
            KubeResourceManager.withThreadContext(threadContext, () -> {
                Semaphore sem = KubeResourceManager.operationSemaphore().get();
                sem.acquireUninterruptibly();
                try {
                    if (allowUpdate && manager.kubeClient().getClient()
                        .resource(resource).get() != null) {
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
            }), KubeResourceManager.executor());

        if (!waitReady) {
            return promise;
        }

        long timeout = Objects.requireNonNullElse(
            type.getTimeoutForResourceReadiness(),
            KubeTestConstants.GLOBAL_TIMEOUT_MEDIUM);

        promise = promise.thenRunAsync(
            KubeResourceManager.withThreadContext(threadContext, () -> {
                Semaphore sem = KubeResourceManager.operationSemaphore().get();
                sem.acquireUninterruptibly();
                try {
                    assertTrue(manager.waitResourceCondition(resource,
                        ResourceCondition.readiness(type), timeout),
                        "Timed out waiting for " + resource.getKind() + "/"
                            + resource.getMetadata().getName());
                } finally {
                    sem.release();
                }
            }), KubeResourceManager.executor());

        return joinIfSync(async, promise, resource);
    }

    private <T extends HasMetadata> CompletableFuture<Void> joinIfSync(
        boolean async, CompletableFuture<Void> promise, T resource) {
        if (async) {
            return promise;
        }
        promise.whenComplete((nothing, error) -> {
            if (error != null) {
                LOGGER.error(
                    "Exception during wait for resource {}/{} to be ready",
                    resource.getMetadata().getNamespace(),
                    resource.getMetadata().getName(), error);
            }
        }).join();
        return CompletableFuture.completedFuture(null);
    }

    // ───────────────────────  HELPERS  ─────────────────────────

    <T extends HasMetadata> void invokeCreateCallbacksSafely(T resource) {
        for (Consumer<HasMetadata> cb : KubeResourceManager.createCallbacks()) {
            try {
                cb.accept(resource);
            } catch (Exception e) {
                LOGGER.warn("Create callback failed for {}/{}: {}",
                    resource.getKind(), resource.getMetadata().getName(),
                    e.getMessage(), e);
            }
        }
    }

    static <T extends HasMetadata> boolean isResourceWithReadiness(T resource) {
        return resource instanceof Deployment
            || resource instanceof
                io.fabric8.kubernetes.api.model.extensions.Deployment
            || resource instanceof ReplicaSet
            || resource instanceof Pod
            || resource instanceof ReplicationController
            || resource instanceof Endpoints
            || resource instanceof Node
            || resource instanceof StatefulSet;
    }

    void writeResourceAsYaml(HasMetadata res) {
        synchronized (KubeResourceManager.creationLock()) {
            File dir = Paths.get(manager.getStoreYamlPath())
                .resolve("test-files")
                .resolve(manager.contextId())
                .resolve(manager.getTestContext()
                    .getRequiredTestClass().getName())
                .toFile();
            if (manager.getTestContext().getTestMethod().isPresent()) {
                dir = dir.toPath().resolve(
                    manager.getTestContext()
                        .getRequiredTestMethod().getName()).toFile();
            } else {
                dir = dir.toPath().resolve("before-all").toFile();
            }
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("Cannot create dir " + dir);
            }
            String yaml = Serialization.asYaml(res);
            try {
                String ns = res.getMetadata().getNamespace();
                Files.writeString(
                    dir.toPath().resolve(
                        res.getKind() + "-"
                            + (ns == null ? "" : ns + "-")
                            + res.getMetadata().getName() + ".yaml"),
                    yaml, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

}
