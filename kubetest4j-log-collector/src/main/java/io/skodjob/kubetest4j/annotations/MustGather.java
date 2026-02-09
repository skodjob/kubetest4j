/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.annotations;

import io.skodjob.kubetest4j.interfaces.MustGatherSupplier;
import io.skodjob.kubetest4j.listeners.TestExceptionCallbackListener;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to run must gather in case test or prepare or clean phase fail
 * It is applied at the class level.
 * <p>
 * It uses the {@link TestExceptionCallbackListener}
 * to set up and clean up resources before and after each test.
 */
@Target(ElementType.TYPE)
@Retention(RUNTIME)
@Inherited
@ExtendWith(TestExceptionCallbackListener.class)
public @interface MustGather {
    /**
     * Set must gather supplier with log collector configuration
     *
     * @return MustGatherSupplier implementation
     */
    Class<? extends MustGatherSupplier> config() default MustGatherSupplier.class;
}
