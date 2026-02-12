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
 * Usage:
 * <pre>
 * &#64;KubernetesTest(namespace = "my-test-ns")
 * class MyKubernetesTest {
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
     * The Kubernetes namespaces to create for testing.
     * If not specified, a unique namespace will be generated.
     * Users must explicitly specify namespaces in their resource metadata.
     *
     * @return array of namespace names to create
     */
    String[] namespaces() default {};


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
     * Labels to apply to the test namespace.
     *
     * @return array of label key=value pairs
     */
    String[] namespaceLabels() default {};

    /**
     * Annotations to apply to the test namespace.
     *
     * @return array of annotation key=value pairs
     */
    String[] namespaceAnnotations() default {};

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


    // ===============================
    // Multi-KubeContext Support
    // ===============================

    /**
     * Additional Kubernetes contexts for multi-context testing.
     * Each additional context defines namespaces and configuration beyond the primary context.
     * The primary context is defined by the top-level annotation parameters.
     *
     * @return array of additional Kubernetes contexts
     */
    AdditionalKubeContext[] additionalKubeContexts() default {};

    /**
     * Defines an additional Kubernetes context configuration.
     * This is used alongside the primary context defined by the main annotation parameters.
     */
    @interface AdditionalKubeContext {
        /**
         * The name of the additional Kubernetes context.
         *
         * @return Kubernetes context name
         */
        String name();

        /**
         * Namespaces to create in this additional context.
         *
         * @return array of namespace names
         */
        String[] namespaces();

        /**
         * Cleanup strategy for this additional context.
         * Defaults to AUTOMATIC if not specified.
         *
         * @return cleanup strategy
         */
        CleanupStrategy cleanup() default CleanupStrategy.AUTOMATIC;

        /**
         * Labels to apply to namespaces in this additional context.
         * Format: "key=value" pairs.
         *
         * @return array of label key=value pairs
         */
        String[] namespaceLabels() default {};

        /**
         * Annotations to apply to namespaces in this additional context.
         * Format: "key=value" pairs.
         *
         * @return array of annotation key=value pairs
         */
        String[] namespaceAnnotations() default {};
    }
}