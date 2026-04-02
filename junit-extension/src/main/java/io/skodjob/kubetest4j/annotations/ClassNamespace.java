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
 * Annotation to declare a class-level Kubernetes namespace for testing.
 * <p>
 * The namespace is created in {@code beforeAll} and deleted in {@code afterAll},
 * providing a namespace shared across all test methods in the class.
 * If the namespace already exists on the cluster, it is used as-is and
 * <b>not deleted</b> during cleanup (namespace protection).
 * <p>
 * This annotation must be placed on <b>static</b> fields of type
 * {@code io.fabric8.kubernetes.api.model.Namespace}.
 * <p>
 * Usage as field injection:
 * <pre>
 * &#64;KubernetesTest
 * class MyTest {
 *     &#64;ClassNamespace(name = "backend", labels = {"env=test"})
 *     static Namespace backendNs;
 *
 *     &#64;Test
 *     void testSomething() {
 *         // backendNs is the same namespace for all tests in this class
 *     }
 * }
 * </pre>
 * <p>
 * Multi-context support:
 * <pre>
 * &#64;ClassNamespace(name = "stg-app", kubeContext = "staging")
 * static Namespace stagingNs;
 * </pre>
 *
 * @see MethodNamespace for per-test-method namespace isolation
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ClassNamespace {
    /**
     * The name of the namespace to create or use.
     *
     * @return the namespace name
     */
    String name();

    /**
     * The kubeContext in which to create this namespace.
     * If not specified, uses the default kubeContext.
     *
     * @return cluster kubeContext name
     */
    String kubeContext() default "";

    /**
     * Labels to apply to the created namespace.
     * Format: {@code "key=value"} pairs.
     * Ignored if the namespace already exists (namespace protection).
     *
     * @return array of label key=value pairs
     */
    String[] labels() default {};

    /**
     * Annotations to apply to the created namespace.
     * Format: {@code "key=value"} pairs.
     * Ignored if the namespace already exists (namespace protection).
     *
     * @return array of annotation key=value pairs
     */
    String[] annotations() default {};
}
