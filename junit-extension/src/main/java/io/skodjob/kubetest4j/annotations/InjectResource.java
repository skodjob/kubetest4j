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
 * Annotation to inject Kubernetes resources loaded from YAML files.
 * Resources will be automatically loaded, applied to the cluster, and made
 * available to the test. Resources are subject to the cleanup strategy
 * specified in the test class.
 * Usage:
 * <pre>
 * &#64;KubernetesTest
 * class MyTest {
 *     &#64;InjectResource("deployment.yaml")
 *     Deployment deployment;
 *
 *     &#64;InjectResource(value = "service.yaml", type = Service.class)
 *     Service service;
 *
 *     &#64;Test
 *     void testDeployment() {
 *         // deployment and service are already applied to the cluster
 *         assertNotNull(deployment);
 *     }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectResource {

    /**
     * Path to the YAML file containing the resource definition.
     * Can be a classpath resource or an absolute/relative file path.
     *
     * @return resource file path
     */
    String value();

    /**
     * Expected type of the resource.
     * If not specified, the field/parameter type will be used.
     *
     * @return resource type class
     */
    Class<?> type() default Object.class;

    /**
     * Specific resource metadata.name to inject from a multi-resource manifest.
     * If empty, the first matching resource by type is used.
     *
     * @return resource metadata.name
     */
    String name() default "";

    /**
     * Whether to wait for the resource to be ready after creation.
     *
     * @return true to wait for readiness, false otherwise
     */
    boolean waitForReady() default true;

    /**
     * The kube context in which to create this resource, named after the suffix of its env vars
     * ({@code staging} for {@code KUBECONFIG_STAGING}). Case-insensitive. Empty or {@code primary} means
     * the default kube context. The manager of the kube context is created on demand, so no namespace
     * annotation is needed. Injection fails at once if no env vars are defined for the kube context.
     *
     * @return kube context id
     */
    String kubeContext() default "";
}
