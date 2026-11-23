/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tracks Kubernetes resources in LIFO batches per context and test.
 * Handles batch lifecycle (implicit and explicit) and resource inspection.
 *
 * <p>Package-private — accessed only through {@link KubeResourceManager}.
 */
final class ResourceTrackerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceTrackerService.class);

    /**
     * A batch on a stack, with the global order in which it landed there. Cleanup across kube contexts
     * deletes the batch with the highest order first.
     *
     * @param order position in the global creation order across every kube context
     * @param batch the tracked batch
     */
    record TrackedBatch(long order, ResourceBatch batch) {
    }

    static final Map<String, Map<String, Deque<TrackedBatch>>> STORED_RESOURCES =
        new ConcurrentHashMap<>();

    private static final AtomicLong NEXT_ORDER = new AtomicLong();

    private static final ThreadLocal<Map<String, List<ResourceItem<?>>>> CURRENT_BATCH =
        new ThreadLocal<>();

    static void reset() {
        STORED_RESOURCES.clear();
        CURRENT_BATCH.remove();
    }

    private final KubeResourceManager manager;
    private final Supplier<ExtensionContext> testContextSupplier;
    private final Function<HasMetadata, ResourceItem<?>> itemFactory;

    /**
     * @param manager             the owning KubeResourceManager instance
     * @param testContextSupplier supplies the current JUnit ExtensionContext
     * @param itemFactory         creates a ResourceItem (with delete lambda) from a resource
     */
    ResourceTrackerService(KubeResourceManager manager,
                    Supplier<ExtensionContext> testContextSupplier,
                    Function<HasMetadata, ResourceItem<?>> itemFactory) {
        this.manager = manager;
        this.testContextSupplier = testContextSupplier;
        this.itemFactory = itemFactory;
    }

    // ───────────────────────  PUSH / REMOVE  ───────────────────

    <T extends HasMetadata> void pushToStack(T resource) {
        ResourceNames.requireName(resource, "track");
        ResourceItem<?> item = itemFactory.apply(resource);
        List<ResourceItem<?>> batch = getOpenBatch();
        if (batch != null) {
            batch.add(item);
        } else {
            pushBatchToDeque(new ResourceBatch(item));
        }
    }

    void pushToStack(ResourceItem<?> item) {
        List<ResourceItem<?>> batch = getOpenBatch();
        if (batch != null) {
            batch.add(item);
        } else {
            pushBatchToDeque(new ResourceBatch(item));
        }
    }

    private List<ResourceItem<?>> getOpenBatch() {
        Map<String, List<ResourceItem<?>>> map = CURRENT_BATCH.get();
        return map != null ? map.get(manager.contextId()) : null;
    }

    void pushBatchToDeque(ResourceBatch batch) {
        // One compute: the order is taken and the batch appended atomically, so a stack stays in
        // creation order and forget() cannot drop the stack between the lookup and the append.
        STORED_RESOURCES
            .computeIfAbsent(manager.contextId(), c -> new ConcurrentHashMap<>())
            .compute(testContextSupplier.get().getDisplayName(), (test, stack) -> {
                Deque<TrackedBatch> target = stack != null ? stack : new ConcurrentLinkedDeque<>();
                target.addLast(new TrackedBatch(NEXT_ORDER.incrementAndGet(), batch));
                return target;
            });
    }

    <T extends HasMetadata> void removeFromStack(T resource) {
        Map<String, Deque<TrackedBatch>> byTest = STORED_RESOURCES.get(manager.contextId());
        if (byTest == null) {
            return;
        }
        ExtensionContext ctx = testContextSupplier.get();
        if (ctx == null) {
            return;
        }
        Deque<TrackedBatch> batches = byTest.get(ctx.getDisplayName());
        if (batches == null) {
            return;
        }
        for (TrackedBatch tracked : batches) {
            tracked.batch().removeIf(item -> item.resource() != null
                && item.resource().getMetadata() != null
                && item.resource().getKind().equals(resource.getKind())
                && Objects.equals(
                    item.resource().getMetadata().getNamespace(),
                    resource.getMetadata().getNamespace())
                && Objects.equals(
                    item.resource().getMetadata().getName(),
                    resource.getMetadata().getName()));
        }
        batches.removeIf(tracked -> tracked.batch().isEmpty());
    }

    // ───────────────────────  BATCH API  ───────────────────────

    boolean isExplicitBatchOpen() {
        return getOpenBatch() != null;
    }

    void addToCurrentBatch(ResourceItem<?> item) {
        List<ResourceItem<?>> batch = getOpenBatch();
        if (batch == null) {
            throw new IllegalStateException(
                "No batch is open on this thread for context " + manager.contextId());
        }
        batch.add(item);
    }

    void startBatch() {
        Map<String, List<ResourceItem<?>>> map = CURRENT_BATCH.get();
        if (map != null && map.containsKey(manager.contextId())) {
            throw new IllegalStateException(
                "A batch is already open on this thread for context "
                    + manager.contextId());
        }
        if (map == null) {
            map = new ConcurrentHashMap<>();
            CURRENT_BATCH.set(map);
        }
        map.put(manager.contextId(), new ArrayList<>());
    }

    void endBatch() {
        Map<String, List<ResourceItem<?>>> map = CURRENT_BATCH.get();
        if (map == null || !map.containsKey(manager.contextId())) {
            throw new IllegalStateException(
                "No batch is open on this thread for context " + manager.contextId());
        }
        List<ResourceItem<?>> items = map.remove(manager.contextId());
        if (map.isEmpty()) {
            CURRENT_BATCH.remove();
        }
        if (!items.isEmpty()) {
            pushBatchToDeque(new ResourceBatch(items));
        }
    }

    AutoCloseable openBatch() {
        startBatch();
        return this::endBatch;
    }

    /**
     * Returns the ids of every kube context that has an explicit batch open on this thread.
     *
     * @return the kube context ids, empty if no batch is open
     */
    static List<String> kubeContextsWithOpenBatch() {
        return Optional.ofNullable(CURRENT_BATCH.get())
            .<List<String>>map(open -> List.copyOf(open.keySet()))
            .orElse(List.of());
    }

    void clearCurrentBatch() {
        Map<String, List<ResourceItem<?>>> map = CURRENT_BATCH.get();
        if (map == null) {
            return;
        }
        List<ResourceItem<?>> items = map.remove(manager.contextId());
        if (map.isEmpty()) {
            CURRENT_BATCH.remove();
        }
        if (items != null && !items.isEmpty()) {
            pushBatchToDeque(new ResourceBatch(items));
        }
    }

    // ───────────────────────  INSPECTION  ──────────────────────

    List<HasMetadata> getCurrentResources() {
        String test = testContextSupplier.get().getDisplayName();
        return Optional.ofNullable(STORED_RESOURCES.get(manager.contextId()))
            .map(m -> m.get(test))
            .map(batches -> {
                List<HasMetadata> resources = new ArrayList<>();
                for (TrackedBatch tracked : batches) {
                    for (ResourceItem<?> item : tracked.batch().items()) {
                        if (item.resource() != null) {
                            resources.add(item.resource());
                        }
                    }
                }
                return Collections.unmodifiableList(resources);
            })
            .orElse(Collections.emptyList());
    }

    void printAllResources(Level logLevel) {
        LOGGER.atLevel(logLevel).log(
            "Printing all managed resources across all contexts");
        STORED_RESOURCES.forEach((ctxId, byTest) -> {
            List<Map.Entry<String, Deque<TrackedBatch>>> tests = byTest.entrySet().stream()
                .filter(test -> !test.getValue().isEmpty())
                .toList();
            if (tests.isEmpty()) {
                return;
            }
            LOGGER.atLevel(logLevel).log("Context [{}]", ctxId);
            tests.forEach(test -> {
                LOGGER.atLevel(logLevel).log("  Test: {}", test.getKey());
                int batchIndex = 0;
                for (TrackedBatch tracked : test.getValue()) {
                    ResourceBatch batch = tracked.batch();
                    LOGGER.atLevel(logLevel).log(
                        "Batch #{} ({} items)", batchIndex, batch.size());
                    for (ResourceItem<?> item : batch.items()) {
                        Optional.ofNullable(item.resource())
                            .ifPresent(r -> LoggerUtils.logResource(
                                "Managed resource:", logLevel, r));
                    }
                    batchIndex++;
                }
            });
        });
    }

    void printCurrentResources(Level logLevel) {
        String test = testContextSupplier.get().getDisplayName();
        LOGGER.atLevel(logLevel).log(
            "Resources in [{}]/{}", manager.contextId(), test);
        Optional.ofNullable(STORED_RESOURCES.get(manager.contextId()))
            .map(m -> m.get(test))
            .ifPresent(batches -> {
                int batchIndex = 0;
                for (TrackedBatch tracked : batches) {
                    ResourceBatch batch = tracked.batch();
                    LOGGER.atLevel(logLevel).log(
                        "Batch #{} ({} items)", batchIndex, batch.size());
                    for (ResourceItem<?> item : batch.items()) {
                        Optional.ofNullable(item.resource()).ifPresent(r ->
                            LoggerUtils.logResource(
                                "Managed resource:", logLevel, r));
                    }
                    batchIndex++;
                }
            });
    }

    // ───────────────────────  CLEANUP ACCESSORS  ───────────────

    Deque<TrackedBatch> getBatches() {
        String testName = testContextSupplier.get().getDisplayName();
        Map<String, Deque<TrackedBatch>> byTest = STORED_RESOURCES.get(manager.contextId());
        if (byTest == null) {
            return null;
        }
        return byTest.get(testName);
    }

    /**
     * Returns the stack of the given test in every kube context that has one, empty stacks included.
     *
     * @param testName the display name the test is tracked under
     * @return kube context id mapped to the live stack of the test
     */
    static Map<String, Deque<TrackedBatch>> stacksOf(String testName) {
        Map<String, Deque<TrackedBatch>> stacks = new HashMap<>();
        STORED_RESOURCES.forEach((contextId, byTest) ->
            Optional.ofNullable(byTest.get(testName)).ifPresent(stack -> stacks.put(contextId, stack)));
        return stacks;
    }

    /**
     * Stops tracking the given stacks of a test. A stack is removed only while it is still the tracked
     * stack and empty, so a batch pushed in the meantime stays tracked.
     *
     * @param testName the display name the test is tracked under
     * @param stacks   the stacks returned by {@link #stacksOf(String)}
     */
    static void forget(String testName, Map<String, Deque<TrackedBatch>> stacks) {
        stacks.forEach((contextId, stack) -> Optional.ofNullable(STORED_RESOURCES.get(contextId))
            .ifPresent(byTest -> byTest.computeIfPresent(testName,
                (test, tracked) -> tracked == stack && tracked.isEmpty() ? null : tracked)));
    }

    /**
     * Stops tracking the drained stack of the current test in this manager's kube context, unless a
     * batch was pushed to it after the drain finished.
     *
     * @param drained the stack returned by {@link #getBatches()} and drained by the caller
     */
    void cleanupAfterDelete(Deque<TrackedBatch> drained) {
        forget(testContextSupplier.get().getDisplayName(), Map.of(manager.contextId(), drained));
    }
}
