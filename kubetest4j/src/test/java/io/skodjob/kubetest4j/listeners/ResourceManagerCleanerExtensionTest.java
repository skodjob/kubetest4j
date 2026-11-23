/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.listeners;

import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.resources.ResourceItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Runs a small {@link ResourceManager} probe class through the JUnit engine and checks that automatic
 * cleanup covers every kube context the test used.
 *
 * <p>The probe is a static nested class, so Surefire never runs it on its own. This class has no
 * extension that binds a test context, because the probe rebinds the test context of the thread it runs on.
 */
@TestVisualSeparator
class ResourceManagerCleanerExtensionTest {

    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @BeforeEach
    void clearEvents() {
        EVENTS.clear();
    }

    /**
     * {@code @ResourceManager} never unbinds the test context, so the finished probe would stay bound to this thread.
     */
    @AfterEach
    void unbindProbeTestContext() {
        KubeResourceManager.get().cleanTestContext();
    }

    @ResourceManager
    static class TwoContextsProbe {
        @Test
        void createsInTwoKubeContexts() {
            KubeResourceManager.getForContext("qa").pushToStack(new ResourceItem<>(() -> EVENTS.add("qa")));
            KubeResourceManager.get().pushToStack(new ResourceItem<>(() -> EVENTS.add("primary")));
        }
    }

    @ResourceManager
    static class OpenBatchProbe {
        @Test
        void leavesBatchOpenInOtherKubeContext() {
            KubeResourceManager qa = KubeResourceManager.getForContext("qa");
            qa.startBatch();
            qa.pushToStack(new ResourceItem<>(() -> EVENTS.add("qa-open-batch")));
        }
    }

    @Test
    void resourceManagerCleansEveryKubeContext() {
        runProbe(TwoContextsProbe.class);
        assertEquals(List.of("primary", "qa"), EVENTS);
    }

    @Test
    void openBatchInOtherKubeContextIsFlushedAndCleaned() {
        runProbe(OpenBatchProbe.class);
        assertEquals(List.of("qa-open-batch"), EVENTS);
    }

    /**
     * Runs a probe class and checks that its single test passed and no container failed. A failure in
     * {@code afterAll} (class-level cleanup) fails the class container, not the test.
     */
    private static void runProbe(Class<?> probe) {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter").selectors(selectClass(probe)).execute();
        results.testEvents().assertStatistics(stats -> stats.succeeded(1).failed(0));
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
    }
}
