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
 * Annotation to inject a KubeResourceManager instance into test methods or fields.
 * The injected resource manager will be pre-configured with the test kubeContext
 * and cleanup strategy.
 * Usage:
 * <pre>
 * &#64;KubernetesTest
 * class MyTest {
 *     &#64;InjectResourceManager
 *     KubeResourceManager resourceManager;
 *
 *     &#64;Test
 *     void testWithResourceManager() {
 *         resourceManager.createResourceWithWait(myDeployment);
 *         // Resources will be automatically cleaned up
 *     }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectResourceManager {

    /**
     * The kube context of this resource manager, named after the suffix of its env vars ({@code staging}
     * for {@code KUBECONFIG_STAGING}). Case-insensitive. Empty or {@code primary} means the default kube
     * context. The manager is created on demand, so no namespace annotation is needed. Injection fails at
     * once if no env vars are defined for the kube context.
     *
     * @return kube context id
     */
    String kubeContext() default "";
}
