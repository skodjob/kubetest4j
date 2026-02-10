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
 * Annotation to inject a KubeClient instance into test methods or fields.
 * The injected client will be configured to use the appropriate cluster kubeContext
 * as specified by the {@link KubernetesTest} annotation.
 * Usage:
 * <pre>
 * &#64;KubernetesTest
 * class MyTest {
 *     &#64;InjectKubeClient
 *     KubeClient client;
 *
 *     &#64;Test
 *     void testWithClient(&#64;InjectKubeClient KubeClient client) {
 *         // Both field and parameter injection work
 *     }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectCmdKubeClient {

    /**
     * The kubeContext to use for this client.
     * If not specified, uses the kubeContext from the test class annotation.
     *
     * @return cluster kubeContext name
     */
    String context() default "";
}
