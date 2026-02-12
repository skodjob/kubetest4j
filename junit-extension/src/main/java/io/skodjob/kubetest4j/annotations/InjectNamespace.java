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
 * Annotation to inject a specific Kubernetes namespace object into test fields or parameters.
 * This injects a single Namespace object for the specified namespace name.
 * <p>
 * The namespace must be defined in the @KubernetesTest annotation's namespaces array.
 * For created namespaces: injects them after creation
 * For existing namespaces: retrieves them from the cluster and injects them
 * <p>
 * Usage:
 * <pre>
 * &#64;KubernetesTest(namespaces = {"app", "monitoring"})
 * class MyTest {
 *     &#64;InjectNamespace(name = "app")
 *     Namespace appNamespace;
 *
 *     &#64;InjectNamespace(name = "monitoring")
 *     Namespace monitoringNamespace;
 *
 *     &#64;Test
 *     void testNamespace() {
 *         assertEquals("app", appNamespace.getMetadata().getName());
 *         assertEquals("monitoring", monitoringNamespace.getMetadata().getName());
 *     }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectNamespace {
    /**
     * The name of the namespace to inject.
     * Must match one of the namespaces defined in the @KubernetesTest annotation.
     *
     * @return the namespace name
     */
    String name();

    /**
     * The kubeContext from which to inject this namespace.
     * If not specified, uses the default kubeContext.
     *
     * @return cluster kubeContext name
     */
    String kubeContext() default "";
}