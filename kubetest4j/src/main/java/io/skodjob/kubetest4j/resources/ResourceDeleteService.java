/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.interfaces.ResourceType;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import io.skodjob.kubetest4j.wait.WaitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Handles resource deletion and bulk cleanup orchestration.
 *
 * <p>Package-private — accessed only through {@link KubeResourceManager}.
 */
final class ResourceDeleteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceDeleteService.class);

    private final KubeResourceManager manager;

    ResourceDeleteService(KubeResourceManager manager) {
        this.manager = manager;
    }

    // ───────────────────────  SINGLE RESOURCE DELETE  ──────────

    @SafeVarargs
    final <T extends HasMetadata> void deleteResource(
        boolean async, boolean waitForDeletion, T... resources) {
        List<CompletableFuture<Void>> waiters = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();
        for (T resource : resources) {
            LoggerUtils.logResource("Deleting", resource);
            try {
                issueDeletion(resource);

                manager.tracker().removeFromStack(resource);

                if (waitForDeletion) {
                    decideDeleteWaitAsync(waiters, async, resource);
                }

                for (Consumer<HasMetadata> cb
                    : KubeResourceManager.deleteCallbacks()) {
                    try {
                        cb.accept(resource);
                    } catch (Exception cbEx) {
                        LOGGER.warn(
                            "Delete callback failed for {}/{}: {}",
                            resource.getKind(),
                            resource.getMetadata().getName(),
                            cbEx.getMessage(), cbEx);
                    }
                }
            } catch (Exception e) {
                LOGGER.error(
                    "Deletion of {}/{} failed with the following error: {}",
                    resource.getKind(),
                    resource.getMetadata().getName(),
                    e.getMessage(), e);
                errors.add(e);
            }
        }
        collectAsyncErrors(waiters, errors);
        if (!errors.isEmpty()) {
            RuntimeException composite = new RuntimeException(
                "Failed to delete " + errors.size() + " resource(s)");
            errors.forEach(composite::addSuppressed);
            throw composite;
        }
    }

    // ───────────────────────  BULK CLEANUP  ────────────────────

    void deleteResources(boolean async) {
        ResourceTrackerService tracker = manager.tracker();
        Deque<ResourceBatch> batches = tracker.getBatches();
        if (batches == null || batches.isEmpty()) {
            LOGGER.info("No resources to delete for [{}]/{}",
                manager.contextId(),
                manager.getTestContext().getDisplayName());
            return;
        }
        LoggerUtils.logSeparator();
        LOGGER.info("Deleting all resources for [{}]/{}",
            manager.contextId(),
            manager.getTestContext().getDisplayName());
        List<Exception> errors = new ArrayList<>();
        try {
            int batchIndex = batches.size();
            while (!batches.isEmpty()) {
                ResourceBatch batch = batches.pollLast();
                batchIndex--;
                LOGGER.info("Deleting batch #{} ({} items)",
                    batchIndex, batch.size());
                deleteBatch(batch, async, errors);
                LOGGER.info("Batch #{} deletion completed", batchIndex);
            }
        } finally {
            tracker.cleanupAfterDelete();
            LoggerUtils.logSeparator();
        }
        if (!errors.isEmpty()) {
            RuntimeException composite = new RuntimeException(
                "Failed to delete " + errors.size() + " resource(s)");
            errors.forEach(composite::addSuppressed);
            throw composite;
        }
    }

    private void deleteBatch(ResourceBatch batch, boolean async,
                             List<Exception> errors) {
        List<CompletableFuture<Void>> waiters = new ArrayList<>();
        for (ResourceItem<?> item : batch.items()) {
            CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
                Semaphore sem =
                    KubeResourceManager.operationSemaphore().get();
                sem.acquireUninterruptibly();
                try {
                    item.throwableRunner().run();
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                } finally {
                    sem.release();
                }
            }, KubeResourceManager.executor());
            if (async) {
                waiters.add(cf);
            } else {
                waitSingleFuture(cf, item, errors);
            }
        }
        collectAsyncErrors(waiters, errors);
    }

    private void waitSingleFuture(CompletableFuture<Void> cf,
                                  ResourceItem<?> item,
                                  List<Exception> errors) {
        String resNs = item.resource() != null
            && item.resource().getMetadata() != null
            ? item.resource().getMetadata().getNamespace() : null;
        String resName = item.resource() != null
            && item.resource().getMetadata() != null
            ? item.resource().getMetadata().getName() : null;
        try {
            cf.get(KubeTestConstants.GLOBAL_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOGGER.error(
                "Timeout waiting for deletion of resource {}/{}",
                resNs, resName, e);
            errors.add(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error(
                "Interrupted during deletion of resource {}/{}",
                resNs, resName, e);
            errors.add(e);
        } catch (ExecutionException e) {
            LOGGER.error(
                "Exception during deletion of resource {}/{}",
                resNs, resName, e);
            errors.add(e);
        }
    }

    // ───────────────────────  ASYNC ERROR COLLECTION  ──────────

    void collectAsyncErrors(List<CompletableFuture<Void>> waiters,
                            List<Exception> errors) {
        if (waiters.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(
                waiters.toArray(new CompletableFuture[0]))
                .join();
        } catch (CompletionException e) {
            collectIndividualFutureErrors(waiters, errors);
        }
    }

    private void collectIndividualFutureErrors(
        List<CompletableFuture<Void>> waiters, List<Exception> errors) {
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

    // ───────────────────────  DELETE HELPERS  ────────────────────

    <T extends HasMetadata> void issueDeletion(T resource) {
        ResourceType<T> type = manager.findResourceType(resource);
        if (type == null) {
            manager.kubeClient().getClient()
                .resource(resource).delete();
        } else {
            type.delete(resource);
        }
    }

    // ───────────────────────  DELETE WAIT  ─────────────────────

    <T extends HasMetadata> void decideDeleteWaitAsync(
        List<CompletableFuture<Void>> waiters, boolean async, T res) {
        CompletableFuture<Void> cf;
        if (async) {
            cf = CompletableFuture.runAsync(() -> {
                Semaphore sem =
                    KubeResourceManager.operationSemaphore().get();
                sem.acquireUninterruptibly();
                try {
                    waitForDeletionWithRetry(res);
                } finally {
                    sem.release();
                }
            }, KubeResourceManager.executor());
        } else {
            cf = CompletableFuture.runAsync(
                () -> waitForDeletionWithRetry(res),
                KubeResourceManager.executor());
        }
        if (async) {
            waiters.add(cf);
        } else {
            try {
                cf.get(KubeTestConstants.GLOBAL_TIMEOUT,
                    TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                LOGGER.error(
                    "Timeout waiting for deletion of resource {}/{}",
                    res.getMetadata().getNamespace(),
                    res.getMetadata().getName(), e);
                throw new IllegalStateException(e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e.getMessage(), e);
            } catch (ExecutionException e) {
                LOGGER.error(
                    "Exception during wait for resource {}/{} to be deleted",
                    res.getMetadata().getNamespace(),
                    res.getMetadata().getName(), e);
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    private <T extends HasMetadata> void waitForDeletionWithRetry(T resource) {
        waitForDeletionWithRetry(resource,
            KubeTestConstants.GLOBAL_TIMEOUT,
            KubeTestConstants.GLOBAL_POLL_INTERVAL_MEDIUM,
            KubeTestConstants.DELETE_RETRY_INTERVAL);
    }

    <T extends HasMetadata> void waitForDeletionWithRetry(
        T resource, long timeout, long pollInterval, long retryInterval) {
        long deadline = System.currentTimeMillis() + timeout;
        long lastDeleteTime = System.currentTimeMillis();

        while (System.currentTimeMillis() < deadline) {
            T current = manager.kubeClient().getClient()
                .resource(resource).get();
            if (current == null) {
                return;
            }

            if (System.currentTimeMillis() - lastDeleteTime
                >= retryInterval) {
                LOGGER.info("Resource {}/{} still exists — re-issuing DELETE",
                    resource.getKind(),
                    resource.getMetadata().getName());
                try {
                    issueDeletion(resource);
                } catch (Exception e) {
                    LOGGER.debug(
                        "Re-delete of {}/{} failed (may already be gone): {}",
                        resource.getKind(),
                        resource.getMetadata().getName(),
                        e.getMessage());
                }
                lastDeleteTime = System.currentTimeMillis();
            }

            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        throw new WaitException(String.format(
            "Timed out waiting for deletion of %s/%s after %d ms",
            resource.getKind(), resource.getMetadata().getName(),
            timeout));
    }
}
