/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.exceptions;

/**
 * Exception thrown when metrics collection is technically successful but the data retrieved is incomplete
 * or missing expected content. This can occur due to various reasons such as connectivity issues,
 * misconfiguration, or partial failures in the data scraping process.
 *
 * <p>This exception is specific to scenarios where the metrics are expected but are not fully
 * retrieved, indicating a problem in the collection pipeline or source metrics not being
 * generated as expected.</p>
 *
 * @see MetricsCollectionException
 */
public class IncompleteMetricsException extends MetricsCollectionException {

    /**
     * Constructs a new IncompleteMetricsException with the specified detail message.
     * The message helps in understanding the context or reason for the data being incomplete.
     *
     * @param message the detail message. The detail message is saved for later retrieval
     *                by the {@link #getMessage()} method.
     */
    public IncompleteMetricsException(String message) {
        super(message);
    }
}