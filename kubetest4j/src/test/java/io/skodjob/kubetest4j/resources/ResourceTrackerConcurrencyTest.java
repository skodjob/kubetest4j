/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.interfaces.ThrowableRunner;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tracking and the cross-context cleanup drain, hit from several threads of one test at once, as Strimzi
 * does with worker threads and with parallel classes that share a method display name.
 */
@ResourceManager
@TestVisualSeparator
@Timeout(60)
class ResourceTrackerConcurrencyTest {

    private static final String QA = "qa";
    private static final String KUBETEST4J_LOGGER = "io.skodjob.kubetest4j";

    private Level previousLevel;

    @BeforeEach
    void quietCleanupLogs() {
        // every drain logs per batch; thousands of batches would flood the build output
        previousLevel = LogManager.getLogger(KUBETEST4J_LOGGER).getLevel();
        Configurator.setLevel(KUBETEST4J_LOGGER, Level.WARN);
    }

    @AfterEach
    void restoreCleanupLogs() {
        Configurator.setLevel(KUBETEST4J_LOGGER, previousLevel);
    }

    @Test
    void concurrentDrainsOfOneTestDeleteEachBatchOnce() throws Exception {
        int items = 2_000;
        Map<Integer, AtomicInteger> deletions = new ConcurrentHashMap<>();
        IntStream.range(0, items).forEach(id -> managerFor(id).pushToStack(new ResourceItem<>(() ->
            deletions.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet())));

        runConcurrently(4, () -> KubeResourceManager.get().deleteResourcesInAllKubeContexts(false));

        assertEquals(items, deletions.size());
        List<Integer> notOnce = deletions.entrySet().stream()
            .filter(deletion -> deletion.getValue().get() != 1)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
        assertTrue(notOnce.isEmpty(), () -> "not deleted exactly once: " + notOnce);
    }

    @Test
    void trackingDuringConcurrentDrainsLosesNothing() throws Exception {
        int items = 5_000;
        AtomicInteger deleted = new AtomicInteger();
        ExtensionContext test = KubeResourceManager.get().getTestContext();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Object> pusher = pool.submit(inTest(test, () -> {
                for (int i = 0; i < items; i++) {
                    KubeResourceManager.getForContext(QA).pushToStack(new ResourceItem<>(deleted::incrementAndGet));
                }
            }));
            Future<Object> drainer = pool.submit(inTest(test, () -> {
                while (!pusher.isDone()) {
                    KubeResourceManager.get().deleteResourcesInAllKubeContexts(false);
                    // An empty drain never blocks, and virtual threads are not time-sliced: without a
                    // yield, a single carrier thread could run this loop forever and starve the pusher.
                    Thread.yield();
                }
            }));
            pusher.get();
            drainer.get();
        }
        KubeResourceManager.get().deleteResourcesInAllKubeContexts(false);
        assertEquals(items, deleted.get());
    }

    @Test
    void concurrentPushesKeepEachStackInCreationOrder() throws Exception {
        int threads = 8;
        int perThread = 500;
        runConcurrently(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                KubeResourceManager.getForContext(QA).pushToStack(new ResourceItem<>(() -> { }));
            }
        });

        String test = KubeResourceManager.get().getTestContext().getDisplayName();
        List<Long> orders = ResourceTrackerService.stacksOf(test).get(QA).stream()
            .map(ResourceTrackerService.TrackedBatch::order)
            .toList();
        assertEquals(threads * perThread, orders.size());
        List<Integer> outOfOrder = IntStream.range(1, orders.size())
            .filter(i -> orders.get(i) <= orders.get(i - 1))
            .boxed()
            .toList();
        assertTrue(outOfOrder.isEmpty(), () -> "stack positions out of creation order: " + outOfOrder);
        KubeResourceManager.get().deleteResourcesInAllKubeContexts(true);
    }

    private static KubeResourceManager managerFor(int id) {
        return id % 2 == 0 ? KubeResourceManager.get() : KubeResourceManager.getForContext(QA);
    }

    /** Runs {@code work} on {@code threads} virtual threads of the current test, released at the same moment. */
    private static void runConcurrently(int threads, ThrowableRunner work) throws Exception {
        ExtensionContext test = KubeResourceManager.get().getTestContext();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> running = IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(inTest(test, () -> {
                    start.await();
                    work.run();
                })))
                .toList();
            start.countDown();
            for (Future<Object> thread : running) {
                thread.get();
            }
        }
    }

    /** Binds the test context on the worker thread, as Strimzi's worker threads do, then runs {@code work}. */
    private static Callable<Object> inTest(ExtensionContext test, ThrowableRunner work) {
        return () -> {
            KubeResourceManager.get().setTestContext(test);
            work.run();
            return null;
        };
    }
}
