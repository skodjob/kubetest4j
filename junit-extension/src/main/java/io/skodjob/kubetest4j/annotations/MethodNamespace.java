/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject a per-test Kubernetes namespace into test instance fields or parameters.
 * Unlike {@link ClassNamespace}, which provides class-level namespaces on static fields,
 * this annotation creates a unique namespace per test method and deletes it after the test completes.
 * <p>
 * The namespace is created in {@code beforeEach} and stays on KRM's resource stack.
 * KRM's LIFO deletion order ensures resources are cleaned up before the namespace itself.
 * This follows the same pattern as JUnit's {@code @TempDir} annotation.
 * <p>
 * The generated namespace name follows the format: {@code <prefix>-<method>-<index>},
 * truncated to 63 characters (Kubernetes namespace name limit).
 * <p>
 * Usage as field injection:
 * <pre>
 * &#64;KubernetesTest
 * class MyTest {
 *     &#64;ClassNamespace(name = "class-ns")
 *     static Namespace classNs;  // same for all test methods
 *
 *     &#64;MethodNamespace(prefix = "worker")
 *     Namespace workerNs;  // unique per test method
 *
 *     &#64;Test
 *     void testSomething() {
 *         // workerNs = "worker-testsomething-0"
 *     }
 * }
 * </pre>
 * <p>
 * Usage as parameter injection:
 * <pre>
 * &#64;KubernetesTest
 * class MyTest {
 *     &#64;Test
 *     void testSomething(&#64;MethodNamespace(prefix = "param") Namespace ns) {
 *         // ns = "param-testsomething-0"
 *     }
 * }
 * </pre>
 * <p>
 * Thread safety: Each test method execution gets its own unique namespace, making this
 * safe for parallel test execution.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MethodNamespace {
    /**
     * Prefix for the generated namespace name.
     * The final name will be {@code <prefix>-<methodName>-<index>}.
     *
     * @return the namespace name prefix
     */
    String prefix() default "test";

    /**
     * The kubeContext from which to create this namespace.
     * If not specified, uses the default kubeContext.
     *
     * @return cluster kubeContext name
     */
    String kubeContext() default "";

    /**
     * Labels to apply to the created namespace.
     * Format: {@code "key=value"} pairs.
     *
     * @return array of label key=value pairs
     */
    String[] labels() default {};

    /**
     * Annotations to apply to the created namespace.
     * Format: {@code "key=value"} pairs.
     *
     * @return array of annotation key=value pairs
     */
    String[] annotations() default {};
}