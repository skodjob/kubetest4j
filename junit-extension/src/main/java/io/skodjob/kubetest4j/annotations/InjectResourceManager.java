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
     * The kubeContext to use for this resource manager.
     * If not specified, uses the kubeContext from the test class annotation.
     *
     * @return cluster kubeContext name
     */
    String context() default "";
}
