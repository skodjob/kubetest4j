/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.annotations;

import io.skodjob.kubetest4j.listeners.TestVisualSeparatorExtension;
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
 * It uses the {@link TestVisualSeparatorExtension}
 */
@Target(ElementType.TYPE)
@Retention(RUNTIME)
@Inherited
@ExtendWith(TestVisualSeparatorExtension.class)
public @interface TestVisualSeparator {
    /**
     * Sets separator char
     *
     * @return visual separator char
     */
    String separator() default "#";

    /**
     * Sets separator length
     *
     * @return length of visual separator line
     */
    int lineLength() default 76;
}
