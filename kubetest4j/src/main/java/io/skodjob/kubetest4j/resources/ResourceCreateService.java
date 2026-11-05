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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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
     * <p>With {@code async == false} and {@code waitReady == true} resources are processed one
     * after another and processing stops at the first failure; in every other mode all
     * resources are processed concurrently. Resources that already existed (HTTP 409 on
     * create) and resources never attempted are not left tracked, so cleanup does not delete
     * objects this test did not create. Any failure is rethrown as an
     * {@link IllegalStateException} with the first cause; further causes are suppressed.
     */
    @SafeVarargs
    final <T extends HasMetadata> void createOrUpdateResource(
        boolean async, boolean waitReady, boolean allowUpdate, T... resources) {

        ResourceTrackerService tracker = manager.tracker();
        List<ResourceItem<T>> items = new ArrayList<>(resources.length);
        for (T resource : resources) {
            KubeResourceManager activeManager = KubeResourceManager
                .getForContext(manager.activeContextId());
            items.add(new ResourceItem<>(
                () -> activeManager.deleteResourceWithWait(resource),
                resource));
        }
        if (tracker.isExplicitBatchOpen()) {
            items.forEach(tracker::addToCurrentBatch);
        } else if (!items.isEmpty()) {
            tracker.pushBatchToDeque(new ResourceBatch(new ArrayList<ResourceItem<?>>(items)));
        }

        boolean sequential = !async && waitReady;
        List<CompletableFuture<Void>> promises = new ArrayList<>();
        for (int i = 0; i < resources.length; i++) {
            T resource = resources[i];
            ResourceType<T> type = manager.findResourceType(resource);
            if (manager.getStoreYamlPath() != null) {
                writeResourceAsYaml(resource);
            }
            CompletableFuture<Void> promise = type == null
                ? createOrUpdateSingle(waitReady, allowUpdate, resource)
                : createOrUpdateSingle(waitReady, allowUpdate, resource, type);
            promises.add(promise);
            if (sequential && joinFailure(promise) != null) {
                for (int j = i + 1; j < items.size(); j++) {
                    tracker.untrack(items.get(j));
                }
                break;
            }
        }

        List<Throwable> failures = new ArrayList<>();
        for (int i = 0; i < promises.size(); i++) {
            Throwable failure = joinFailure(promises.get(i));
            if (failure == null) {
                continue;
            }
            T resource = resources[i];
            LOGGER.error("Exception during create or wait for resource {}/{}",
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName(), failure);
            if (!allowUpdate && isConflict(failure)) {
                LOGGER.warn("{} {}/{} already exists; it was not created by this test "
                        + "and will not be deleted during cleanup",
                    resource.getKind(), resource.getMetadata().getNamespace(),
                    resource.getMetadata().getName());
                tracker.untrack(items.get(i));
            }
            failures.add(failure);
        }

        if (!failures.isEmpty()) {
            Throwable cause = failures.get(0);
            IllegalStateException error = new IllegalStateException(cause.getMessage(), cause);
            failures.stream().skip(1).forEach(error::addSuppressed);
            throw error;
        }
    }

    /**
     * Waits for the future and returns its unwrapped failure.
     *
     * @param promise the future to wait for
     * @return the failure cause, or {@code null} if the future completed normally
     */
    static Throwable joinFailure(CompletableFuture<Void> promise) {
        try {
            promise.join();
            return null;
        } catch (CompletionException | CancellationException e) {
            Throwable cause = e;
            while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
                cause = cause.getCause();
            }
            return cause;
        }
    }

    static boolean isConflict(Throwable failure) {
        return failure instanceof KubernetesClientException kce && kce.getCode() == 409;
    }

    private <T extends HasMetadata> CompletableFuture<Void> createOrUpdateSingle(
        boolean waitReady, boolean allowUpdate, T resource) {

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
                        defaultReadiness(manager, resource)),
                        "Timed out waiting for " + resource.getKind() + "/"
                            + resource.getMetadata().getName());
                } finally {
                    sem.release();
                }
            }), KubeResourceManager.executor());

        return promise;
    }

    private <T extends HasMetadata> CompletableFuture<Void> createOrUpdateSingle(
        boolean waitReady, boolean allowUpdate,
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

        return promise;
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

    /**
     * Readiness condition used when no {@link ResourceType} is registered for the kind:
     * the resource must exist and, for kinds Fabric8 can judge, report ready.
     *
     * @param manager  the manager whose client checks readiness
     * @param resource the resource being waited for
     * @param <T>      resource type
     * @return condition named "ready"
     */
    static <T extends HasMetadata> ResourceCondition<T> defaultReadiness(
        KubeResourceManager manager, T resource) {
        return new ResourceCondition<>(current -> current != null
            && (!isResourceWithReadiness(resource)
                || manager.kubeClient().getClient().resource(resource).isReady()),
            "ready");
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
