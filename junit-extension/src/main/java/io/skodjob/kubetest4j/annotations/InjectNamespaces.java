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
 * Annotation to inject Kubernetes namespace objects into test fields or parameters.
 * This injects a Map where the key is the namespace name and
 * the value is the Namespace object, corresponding to the namespaces defined
 * in the @KubernetesTest annotation.
 * For created namespaces: injects them after creation
 * For existing namespaces: retrieves them from the cluster and injects them
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectNamespaces {
    /**
     * The kubeContext from which to inject namespaces.
     * If not specified, uses the default kubeContext.
     *
     * @return cluster kubeContext name
     */
    String context() default "";
}