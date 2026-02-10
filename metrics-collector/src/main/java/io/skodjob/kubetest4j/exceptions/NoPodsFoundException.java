/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.exceptions;

/**
 * Exception thrown when no Kubernetes pods are found matching the specified criteria during the metrics collection
 * process.
 * This could occur when label selectors do not match any pods, or if pods are not available in the specified namespace.
 *
 * <p>This exception indicates a possible misconfiguration or an operational issue that requires attention,
 * such as ensuring that the intended pods are deployed and correctly labeled according to the expectations of
 * the metrics collection setup.</p>
 */
public class NoPodsFoundException extends MetricsCollectionException {

    /**
     * Constructs a new NoPodsFoundException with the specified detail message.
     * The message is used to convey the specific reason why no pods were found, facilitating troubleshooting
     * and corrective actions.
     *
     * @param message the detailed message explaining why no pods were found. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     */
    public NoPodsFoundException(String message) {
        super(message);
    }
}
