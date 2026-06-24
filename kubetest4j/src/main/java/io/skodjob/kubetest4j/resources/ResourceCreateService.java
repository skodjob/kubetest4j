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

    @SafeVarargs
    final <T extends HasMetadata> void createOrUpdateResource(
        boolean async, boolean waitReady, boolean allowUpdate, T... resources) {

        ResourceTrackerService tracker = manager.tracker();
        boolean explicitBatchOpen = tracker.isExplicitBatchOpen();
        List<ResourceItem<?>> implicitBatchItems = new ArrayList<>();

        for (T resource : resources) {
            KubeResourceManager activeManager = KubeResourceManager
                .getForContext(manager.activeContextId());
            ResourceItem<T> item = new ResourceItem<>(
                () -> activeManager.deleteResourceWithWait(resource),
                resource);
            if (explicitBatchOpen) {
                tracker.addToCurrentBatch(item);
            } else {
                implicitBatchItems.add(item);
            }
        }
        if (!explicitBatchOpen && !implicitBatchItems.isEmpty()) {
            tracker.pushBatchToDeque(new ResourceBatch(implicitBatchItems));
        }

        List<CompletableFuture<Void>> promises = new ArrayList<>();
        for (T resource : resources) {
            ResourceType<T> type = manager.findResourceType(resource);
            if (manager.getStoreYamlPath() != null) {
                writeResourceAsYaml(resource);
            }
            if (type == null) {
                promises.add(createOrUpdateSingle(
                    async, waitReady, allowUpdate, resource));
            } else {
                promises.add(createOrUpdateSingle(
                    async, waitReady, allowUpdate, resource, type));
            }
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

    private <T extends HasMetadata> CompletableFuture<Void> createOrUpdateSingle(
        boolean async, boolean waitReady, boolean allowUpdate, T resource) {

        CompletableFuture<Void> promise = CompletableFuture.runAsync(() -> {
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
        }, KubeResourceManager.executor());

        if (!waitReady) {
            return promise;
        }

        promise = promise.thenRunAsync(() -> {
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
        }, KubeResourceManager.executor());

        return joinIfSync(async, promise, resource);
    }

    private <T extends HasMetadata> CompletableFuture<Void> createOrUpdateSingle(
        boolean async, boolean waitReady, boolean allowUpdate,
        T resource, ResourceType<T> type) {

        CompletableFuture<Void> promise = CompletableFuture.runAsync(() -> {
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
        }, KubeResourceManager.executor());

        if (!waitReady) {
            return promise;
        }

        long timeout = Objects.requireNonNullElse(
            type.getTimeoutForResourceReadiness(),
            KubeTestConstants.GLOBAL_TIMEOUT_MEDIUM);

        promise = promise.thenRunAsync(() -> {
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
        }, KubeResourceManager.executor());

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
