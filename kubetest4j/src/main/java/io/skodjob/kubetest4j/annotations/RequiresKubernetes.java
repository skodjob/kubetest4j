/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.annotations;

import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.conditions.KubernetesCondition;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to conditionally run tests only when a Kubernetes cluster is available and accessible.
 * Tests/classes annotated with this will be skipped if no active Kubernetes cluster connection exists.
 * <p>
 * This is useful for:
 * - CI/CD environments without cluster access
 * - Local development without running cluster
 * - Conditional execution based on environment
 * <p>
 * Usage:
 * <pre>
 * &#64;RequiresKubernetes
 * class MyKubernetesTest {
 *     &#64;Test
 *     void testKubernetesFeature() {
 *         // Only runs if cluster is available
 *     }
 * }
 *
 * class MyMixedTest {
 *     &#64;Test
 *     void testLocalFeature() {
 *         // Always runs
 *     }
 *
 *     &#64;Test
 *     &#64;RequiresKubernetes
 *     void testKubernetesFeature() {
 *         // Only runs if cluster is available
 *     }
 * }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(KubernetesCondition.class)
public @interface RequiresKubernetes {

    /**
     * Custom reason for skipping when cluster is not available.
     * Default message will be used if not specified.
     *
     * @return custom skip reason
     */
    String skipReason() default "";

    /**
     * Kubernetes context to check connectivity for.
     * If empty, uses the default/current context.
     *
     * @return context name to check
     */
    String kubeContext() default KubeTestConstants.DEFAULT_CONTEXT_NAME;

    /**
     * Timeout in milliseconds for cluster connectivity check.
     * Default is 5 seconds.
     *
     * @return timeout in milliseconds
     */
    long timeoutMs() default 5000L;

    /**
     * Whether to perform a full cluster readiness check or just basic connectivity.
     * - true: Check if cluster nodes are ready, API server responsive
     * - false: Just check if API server is reachable
     *
     * @return true for full readiness check, false for basic connectivity
     */
    boolean checkClusterHealth() default true;
}
