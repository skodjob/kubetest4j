/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.resources.ResourceTrackerService.TrackedBatch;
import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the tracker keeps and forgets when a cleanup ends, checked on the tracker state itself.
 */
@ResourceManager
@TestVisualSeparator
class ResourceTrackerServiceTest {

    private static final String QA = "qa";

    @Test
    void forgetKeepsBatchTrackedAfterTheDrain() {
        KubeResourceManager qa = KubeResourceManager.getForContext(QA);
        qa.pushToStack(noop());
        Map<String, Deque<TrackedBatch>> drained = ResourceTrackerService.stacksOf(testName());
        drained.get(QA).pollLast();
        // tracked between the end of the drain and forget()
        qa.pushToStack(noop());

        ResourceTrackerService.forget(testName(), drained);

        assertEquals(1, trackedInQa());
    }

    @Test
    void cleanupInAllKubeContextsForgetsEmptyStacks() {
        KubeResourceManager.getForContext(QA).pushToStack(noop());
        // leaves an empty stack behind, as removeFromStack does after a namespace is deleted
        ResourceTrackerService.stacksOf(testName()).get(QA).pollLast();

        KubeResourceManager.get().deleteResourcesInAllKubeContexts(true);

        Map<String, Deque<TrackedBatch>> left = ResourceTrackerService.stacksOf(testName());
        assertTrue(left.isEmpty(), () -> "still tracked: " + left.keySet());
    }

    @Test
    void singleContextCleanupKeepsBatchTrackedAfterTheDrain() {
        KubeResourceManager qa = KubeResourceManager.getForContext(QA);
        qa.pushToStack(noop());
        Deque<TrackedBatch> drained = qa.tracker().getBatches();
        drained.pollLast();
        // tracked between the end of the drain and the cleanup of the stack
        qa.pushToStack(noop());

        qa.tracker().cleanupAfterDelete(drained);

        assertEquals(1, trackedInQa());
    }

    @Test
    void singleContextCleanupForgetsDrainedStack() {
        KubeResourceManager qa = KubeResourceManager.getForContext(QA);
        qa.pushToStack(noop());

        qa.deleteResources(true);

        assertTrue(ResourceTrackerService.stacksOf(testName()).isEmpty());
    }

    private static String testName() {
        return KubeResourceManager.get().getTestContext().getDisplayName();
    }

    private static int trackedInQa() {
        return Optional.ofNullable(ResourceTrackerService.stacksOf(testName()).get(QA)).map(Deque::size).orElse(0);
    }

    private static ResourceItem<?> noop() {
        return new ResourceItem<>(() -> { });
    }
}
