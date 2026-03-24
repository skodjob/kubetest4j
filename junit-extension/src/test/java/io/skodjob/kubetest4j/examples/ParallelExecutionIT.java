/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.MethodNamespace;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class ParallelExecutionIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelExecutionIT.class);

    // Tracks which threads executed test methods to verify actual parallelism
    private final Set<String> threadNames = ConcurrentHashMap.newKeySet();

    // Latch to synchronize concurrent methods - ensures they overlap in time
    private final CountDownLatch allMethodsStarted = new CountDownLatch(3);

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testConcurrentResourceCreationInAlpha(
            @MethodNamespace(prefix = "parallel-alpha") Namespace ns) throws InterruptedException {
        threadNames.add(Thread.currentThread().getName());
        allMethodsStarted.countDown();
        allMethodsStarted.await(30, TimeUnit.SECONDS);

        String nsName = ns.getMetadata().getName();
        LOGGER.info("Creating resources in {} on thread {}", nsName, Thread.currentThread().getName());

        resourceManager.createResourceWithWait(
            new ConfigMapBuilder()
                .withNewMetadata()
                .withName("config-alpha")
                .withNamespace(nsName)
                .endMetadata()
                .addToData("source", "alpha")
                .build()
        );

        // Verify RM tracks only this test's non-namespace resources, not resources from parallel threads
        List<HasMetadata> trackedNonNs = nonNamespaceResources(resourceManager.getCurrentResources());
        LOGGER.info("Tracked non-namespace resources for alpha test: {}", resourceNames(trackedNonNs));

        assertEquals(1, trackedNonNs.size(),
            "RM should track exactly 1 non-namespace resource for this test, but found: "
                + resourceNames(trackedNonNs));
        assertEquals("config-alpha", trackedNonNs.get(0).getMetadata().getName());
        assertEquals(nsName, trackedNonNs.get(0).getMetadata().getNamespace());
    }

    @Test
    void testConcurrentResourceCreationInBravo(
            @MethodNamespace(prefix = "parallel-bravo") Namespace ns) throws InterruptedException {
        threadNames.add(Thread.currentThread().getName());
        allMethodsStarted.countDown();
        allMethodsStarted.await(30, TimeUnit.SECONDS);

        String nsName = ns.getMetadata().getName();
        LOGGER.info("Creating resources in {} on thread {}", nsName, Thread.currentThread().getName());

        // Create multiple resources to increase contention on shared resourceManager
        resourceManager.createResourceWithWait(
            new ConfigMapBuilder()
                .withNewMetadata()
                .withName("config-bravo-1")
                .withNamespace(nsName)
                .endMetadata()
                .addToData("source", "bravo-1")
                .build()
        );

        resourceManager.createResourceWithWait(
            new ConfigMapBuilder()
                .withNewMetadata()
                .withName("config-bravo-2")
                .withNamespace(nsName)
                .endMetadata()
                .addToData("source", "bravo-2")
                .build()
        );

        // Verify RM tracks only this test's non-namespace resources
        List<HasMetadata> trackedNonNs = nonNamespaceResources(resourceManager.getCurrentResources());
        LOGGER.info("Tracked non-namespace resources for bravo test: {}", resourceNames(trackedNonNs));

        assertEquals(2, trackedNonNs.size(),
            "RM should track exactly 2 non-namespace resources for this test, but found: "
                + resourceNames(trackedNonNs));

        List<String> names = resourceNames(trackedNonNs);
        assertTrue(names.contains("config-bravo-1"),
            "RM should track config-bravo-1");
        assertTrue(names.contains("config-bravo-2"),
            "RM should track config-bravo-2");
        assertTrue(names.stream().noneMatch(n -> n.contains("alpha") || n.contains("charlie")),
            "RM should not contain resources from other tests, but found: " + names);
    }

    @Test
    void testConcurrentResourceCreationInCharlie(
            @MethodNamespace(prefix = "parallel-charlie") Namespace ns) throws InterruptedException {
        threadNames.add(Thread.currentThread().getName());
        allMethodsStarted.countDown();
        allMethodsStarted.await(30, TimeUnit.SECONDS);

        String nsName = ns.getMetadata().getName();
        LOGGER.info("Creating resources in {} on thread {}", nsName, Thread.currentThread().getName());

        resourceManager.createResourceWithWait(
            new ConfigMapBuilder()
                .withNewMetadata()
                .withName("config-charlie")
                .withNamespace(nsName)
                .endMetadata()
                .addToData("source", "charlie")
                .build()
        );

        // Verify RM tracks only this test's non-namespace resource
        List<HasMetadata> trackedNonNs = nonNamespaceResources(resourceManager.getCurrentResources());
        LOGGER.info("Tracked non-namespace resources for charlie test: {}", resourceNames(trackedNonNs));

        assertEquals(1, trackedNonNs.size(),
            "RM should track exactly 1 non-namespace resource for this test, but found: "
                + resourceNames(trackedNonNs));
        assertEquals("config-charlie", trackedNonNs.get(0).getMetadata().getName());

        // Verify actual parallelism occurred across all methods
        LOGGER.info("Threads used across all parallel methods: {}", threadNames);
        assertTrue(threadNames.size() > 1,
            "Expected multiple threads but got: " + threadNames);
    }

    private List<HasMetadata> nonNamespaceResources(List<HasMetadata> resources) {
        return resources.stream()
            .filter(r -> !(r instanceof Namespace))
            .toList();
    }

    private List<String> resourceNames(List<HasMetadata> resources) {
        return resources.stream()
            .map(r -> r.getMetadata().getName())
            .toList();
    }
}
