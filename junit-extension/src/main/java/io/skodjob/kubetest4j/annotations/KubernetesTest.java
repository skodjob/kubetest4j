/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.annotations;

import io.skodjob.kubetest4j.KubernetesTestExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Main annotation to enable Kubernetes test framework features for JUnit 6.
 * This annotation automatically sets up the test environment, manages resources,
 * and provides dependency injection for Kubernetes clients.
 * <p>
 * Namespaces are declared using {@link ClassNamespace} (class-level, static fields)
 * and {@link MethodNamespace} (per-test-method, instance fields/parameters) annotations
 * directly on test fields, rather than in this annotation.
 * <p>
 * Usage:
 * <pre>
 * &#64;KubernetesTest
 * class MyKubernetesTest {
 *     &#64;ClassNamespace(name = "my-test-ns")
 *     static Namespace testNs;
 *
 *     &#64;InjectKubeClient
 *     KubeClient client;
 *
 *     &#64;Test
 *     void testPodCreation() {
 *         // Your test logic here
 *     }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(KubernetesTestExtension.class)
public @interface KubernetesTest {

    /**
     * When to clean up resources created during the test.
     *
     * @return cleanup strategy
     */
    CleanupStrategy cleanup() default CleanupStrategy.AUTOMATIC;

    /**
     * Whether to store YAML representations of created resources to disk.
     *
     * @return true to store YAML files, false otherwise
     */
    boolean storeYaml() default false;

    /**
     * Directory path to store YAML files when storeYaml is enabled.
     *
     * @return YAML storage directory
     */
    String yamlStorePath() default "";

    /**
     * Character to use for visual test separators.
     *
     * @return separator character
     */
    String visualSeparatorChar() default "#";

    /**
     * Length of visual test separator lines.
     *
     * @return separator line length
     */
    int visualSeparatorLength() default 76;

    // ===============================
    // Log Collection Configuration
    // ===============================

    /**
     * Whether to enable log collection for this test.
     *
     * @return true to enable log collection, false otherwise
     */
    boolean collectLogs() default false;

    /**
     * When to collect logs during test execution.
     *
     * @return log collection strategy
     */
    LogCollectionStrategy logCollectionStrategy() default LogCollectionStrategy.ON_FAILURE;

    /**
     * Directory path where logs should be collected.
     * If empty, defaults to "target/test-logs".
     *
     * @return log collection directory
     */
    String logCollectionPath() default "";

    /**
     * Whether to collect previous container logs (for crashed containers).
     *
     * @return true to collect previous logs, false otherwise
     */
    boolean collectPreviousLogs() default false;

    /**
     * Namespaced resource types to collect YAML descriptions for.
     * Common examples: "pods", "services", "configmaps", "secrets", "deployments"
     *
     * @return array of resource types
     */
    String[] collectNamespacedResources() default {"pods", "services", "configmaps", "secrets"};

    /**
     * Cluster-wide resource types to collect YAML descriptions for.
     * Common examples: "nodes", "persistentvolumes", "storageclasses"
     *
     * @return array of cluster-wide resource types
     */
    String[] collectClusterWideResources() default {};
}
