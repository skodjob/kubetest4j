/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.resources.ResourceItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Runs small {@link KubernetesTest} probe classes through the JUnit engine and checks that automatic
 * cleanup covers every kube context the test used.
 *
 * <p>The probes are static nested classes, so Surefire never runs them on their own. This class has no
 * extension that binds a test context, because the probes rebind the test context of the thread they run on.
 */
@TestVisualSeparator
class MultiContextCleanupTest {

    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @BeforeEach
    void clearEvents() {
        EVENTS.clear();
    }

    @KubernetesTest
    static class TwoContextsProbe {
        @Test
        void createsInTwoKubeContexts() {
            KubeResourceManager.getForContext("staging")
                .pushToStack(new ResourceItem<>(() -> EVENTS.add("staging")));
            KubeResourceManager.get().pushToStack(new ResourceItem<>(() -> EVENTS.add("primary")));
        }
    }

    @KubernetesTest
    static class ClassLevelProbe {
        @BeforeAll
        static void beforeAll() {
            KubeResourceManager.getForContext("staging")
                .pushToStack(new ResourceItem<>(() -> EVENTS.add("staging-class")));
        }

        @Test
        void test() {
            // the class-level item is what this probe checks
        }
    }

    @KubernetesTest
    static class OpenBatchProbe {
        @Test
        void leavesBatchOpenInOtherKubeContext() {
            KubeResourceManager staging = KubeResourceManager.getForContext("staging");
            staging.startBatch();
            staging.pushToStack(new ResourceItem<>(() -> EVENTS.add("staging-open-batch")));
        }
    }

    @KubernetesTest
    static class InjectWithoutNamespaceProbe {
        @InjectResourceManager(kubeContext = "STAGING")
        KubeResourceManager staging;

        @Test
        void injected() {
            EVENTS.add(staging == KubeResourceManager.getForContext("staging") ? "same-manager" : "other");
        }
    }

    @KubernetesTest
    static class UnknownKubeContextProbe {
        @InjectResourceManager(kubeContext = "stagng")
        KubeResourceManager typo;

        @Test
        void injected() {
            // never runs: injection fails in beforeEach
        }
    }

    @Test
    void methodLevelItemsInEveryKubeContextAreCleanedInAfterEach() {
        runProbe(TwoContextsProbe.class);
        assertEquals(List.of("primary", "staging"), EVENTS);
    }

    @Test
    void classLevelItemsInOtherKubeContextAreCleanedInAfterAll() {
        runProbe(ClassLevelProbe.class);
        assertEquals(List.of("staging-class"), EVENTS);
    }

    @Test
    void openBatchInOtherKubeContextIsFlushedAndCleanedInAfterEach() {
        runProbe(OpenBatchProbe.class);
        assertEquals(List.of("staging-open-batch"), EVENTS);
    }

    @Test
    void injectsManagerForKubeContextWithoutNamespaceAnnotation() {
        runProbe(InjectWithoutNamespaceProbe.class);
        assertEquals(List.of("same-manager"), EVENTS);
    }

    @Test
    void unknownKubeContextFailsInjection() {
        Events failed = EngineTestKit.engine("junit-jupiter").selectors(selectClass(UnknownKubeContextProbe.class))
            .execute().testEvents().failed();
        assertEquals(1, failed.count());
        Throwable cause = failed.list().getFirst().getRequiredPayload(TestExecutionResult.class)
            .getThrowable().orElseThrow().getCause();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().startsWith("Unknown kubeContext 'stagng'"));
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
