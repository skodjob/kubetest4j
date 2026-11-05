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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
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

    static final Map<String, Map<String, Deque<ResourceBatch>>> STORED_RESOURCES =
        new ConcurrentHashMap<>();

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

    // ───────────────────────  CONTEXT  ─────────────────────────

    /**
     * Returns the JUnit test context bound to the current thread.
     *
     * @return the bound test context
     * @throws IllegalStateException if no test context is bound
     */
    private ExtensionContext requireTestContext() {
        ExtensionContext ctx = testContextSupplier.get();
        if (ctx == null) {
            throw new IllegalStateException("No JUnit test context is bound to thread '"
                + Thread.currentThread().getName() + "' for context '" + manager.contextId()
                + "'. Annotate the test class with @ResourceManager or @KubernetesTest, "
                + "or call KubeResourceManager.setTestContext(...) on this thread.");
        }
        return ctx;
    }

    /**
     * Returns the key under which resources of a test are tracked: the JUnit unique id,
     * which stays distinct when tests share a display name (for example the same method
     * name in two classes running in parallel). Falls back to the display name when the
     * unique id is {@code null}, which happens with mocked contexts.
     *
     * @param ctx the JUnit extension context
     * @return the tracking key
     */
    static String trackingKey(ExtensionContext ctx) {
        String uniqueId = ctx.getUniqueId();
        return uniqueId != null ? uniqueId : ctx.getDisplayName();
    }

    // ───────────────────────  PUSH / REMOVE  ───────────────────

    <T extends HasMetadata> void pushToStack(T resource) {
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
        STORED_RESOURCES
            .computeIfAbsent(manager.contextId(), c -> new ConcurrentHashMap<>())
            .computeIfAbsent(trackingKey(requireTestContext()),
                t -> new ConcurrentLinkedDeque<>())
            .addLast(batch);
    }

    <T extends HasMetadata> void removeFromStack(T resource) {
        Map<String, Deque<ResourceBatch>> byTest = STORED_RESOURCES.get(manager.contextId());
        if (byTest == null) {
            return;
        }
        ExtensionContext ctx = testContextSupplier.get();
        if (ctx == null) {
            return;
        }
        Deque<ResourceBatch> batches = byTest.get(trackingKey(ctx));
        if (batches == null) {
            return;
        }
        for (ResourceBatch batch : batches) {
            batch.removeIf(item -> item.resource() != null
                && item.resource().getKind().equals(resource.getKind())
                && Objects.equals(
                    item.resource().getMetadata().getNamespace(),
                    resource.getMetadata().getNamespace())
                && item.resource().getMetadata().getName()
                    .equals(resource.getMetadata().getName()));
        }
        batches.removeIf(ResourceBatch::isEmpty);
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
        String test = trackingKey(requireTestContext());
        return Optional.ofNullable(STORED_RESOURCES.get(manager.contextId()))
            .map(m -> m.get(test))
            .map(batches -> {
                List<HasMetadata> resources = new ArrayList<>();
                for (ResourceBatch batch : batches) {
                    for (ResourceItem<?> item : batch.items()) {
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
            LOGGER.atLevel(logLevel).log("Context [{}]", ctxId);
            byTest.forEach((test, batches) -> {
                LOGGER.atLevel(logLevel).log("  Test: {}", test);
                int batchIndex = 0;
                for (ResourceBatch batch : batches) {
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
        ExtensionContext ctx = requireTestContext();
        LOGGER.atLevel(logLevel).log(
            "Resources in [{}]/{}", manager.contextId(), ctx.getDisplayName());
        Optional.ofNullable(STORED_RESOURCES.get(manager.contextId()))
            .map(m -> m.get(trackingKey(ctx)))
            .ifPresent(batches -> {
                int batchIndex = 0;
                for (ResourceBatch batch : batches) {
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

    Deque<ResourceBatch> getBatches() {
        String testName = trackingKey(requireTestContext());
        Map<String, Deque<ResourceBatch>> byTest = STORED_RESOURCES.get(manager.contextId());
        if (byTest == null) {
            return null;
        }
        return byTest.get(testName);
    }

    void cleanupAfterDelete() {
        String testName = trackingKey(requireTestContext());
        Map<String, Deque<ResourceBatch>> byTest = STORED_RESOURCES.get(manager.contextId());
        if (byTest != null) {
            byTest.remove(testName);
        }
    }
}
