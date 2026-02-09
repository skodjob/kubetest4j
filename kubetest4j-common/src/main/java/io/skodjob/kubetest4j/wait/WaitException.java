/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.wait;

/**
 * An exception indicating a failure while waiting.
 */
public class WaitException extends RuntimeException {

    /**
     * Constructs a new WaitException with the specified detail message.
     *
     * @param message The detail message.
     */
    public WaitException(String message) {
        super(message);
    }
}
