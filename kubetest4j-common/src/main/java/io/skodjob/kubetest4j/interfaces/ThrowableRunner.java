/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.interfaces;

/**
 * A functional interface that represents a block of code that can throw an exception.
 */
@FunctionalInterface
public interface ThrowableRunner {
    /**
     * Executes the block of code.
     *
     * @throws Exception if an error occurs while executing the code block.
     */
    void run() throws Exception;
}
