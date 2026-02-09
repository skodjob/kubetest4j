/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.exceptions;

/**
 * Represents exceptions that can occur during the collection of metrics from Kubernetes components.
 * This exception is used as a base class for more specific metrics collection related exceptions,
 * providing a general catch-all for issues encountered during the metrics collection process.
 *
 * <p>Using this as a base exception allows callers to either handle general metric collection errors
 * broadly, or more specific errors individually, depending on the level of detail needed in handling.</p>
 */
public class MetricsCollectionException extends RuntimeException {

    /**
     * Constructs a new MetricsCollectionException with the specified detail message.
     * The message is used to provide more details about the error condition and is typically presented
     * in logs or error messages shown to a user.
     *
     * @param message the detailed message that explains the cause of the exception.
     *                The detail message is saved for later retrieval by the {@link #getMessage()} method.
     */
    public MetricsCollectionException(String message) {
        super(message);
    }

    /**
     * Constructs a new MetricsCollectionException with the specified detail message and cause.
     * This constructor is useful in situations where the exception is a result of another underlying exception
     * (e.g., a failed network call or parsing error). The cause helps in tracing back to the root problem that
     * triggered this exception.
     *
     * @param message the detailed message that explains the cause of the exception. The detail message is saved
     *                for later retrieval by the {@link #getMessage()} method.
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method). A null value
     *                is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public MetricsCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}