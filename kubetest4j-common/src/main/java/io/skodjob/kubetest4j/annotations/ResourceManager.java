/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.annotations;

import io.skodjob.kubetest4j.listeners.ResourceManagerCleanerExtension;
import io.skodjob.kubetest4j.listeners.ResourceManagerExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to manage resources in JUnit tests.
 * It is applied at the class level.
 * <p>
 * It uses the {@link ResourceManagerExtension}
 * to set up and clean up resources before and after each test.
 */
@Target(ElementType.TYPE)
@Retention(RUNTIME)
@Inherited
@ExtendWith(ResourceManagerExtension.class)
@ExtendWith(ResourceManagerCleanerExtension.class)
public @interface ResourceManager {
    /**
     * Enables cleaner extension for resource manager
     *
     * @return enable/disable cleaner
     */
    boolean cleanResources() default true;

    /**
     * Enables async deletion of resources
     *
     * @return enable/disable async cleaning
     */
    boolean asyncDeletion() default true;
}
