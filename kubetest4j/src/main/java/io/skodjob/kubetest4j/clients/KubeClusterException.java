/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.skodjob.kubetest4j.clients;

import io.skodjob.kubetest4j.executor.ExecResult;

/**
 * Custom exception class for handling exceptions related to Kubernetes cluster operations.
 * This exception class encapsulates the result of a failed execution attempt, providing
 * detailed information about the error that occurred during Kubernetes cluster operations.
 */
public class KubeClusterException extends RuntimeException {
    /**
     * results
     */
    public final ExecResult result;

    /**
     * Constructs a new KubeClusterException with the specified detail message and execution result.
     *
     * @param result The execution result that led to the exception. Contains details about the failure.
     * @param s      The detail message. The detail message is saved for later retrieval by the getMessage() method.
     */
    public KubeClusterException(ExecResult result, String s) {
        super(s);
        this.result = result;
    }

    /**
     * Constructs a new KubeClusterException with the specified cause.
     *
     * @param cause The cause (which is saved for later retrieval by the getCause() method).
     *              (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public KubeClusterException(Throwable cause) {
        super(cause);
        this.result = null;
    }

    /**
     * Exception class indicating that a requested resource was not found in the Kubernetes cluster.
     */
    public static class NotFound extends KubeClusterException {

        /**
         * Constructs a new NotFound exception with the specified execution result and detail message.
         *
         * @param result The execution result that led to the exception.
         * @param s      The detail message.
         */
        public NotFound(ExecResult result, String s) {
            super(result, s);
        }
    }

    /**
     * Exception class indicating that the resource to be created already exists in the Kubernetes cluster.
     */
    public static class AlreadyExists extends KubeClusterException {

        /**
         * Constructs a new AlreadyExists exception with the specified execution result and detail message.
         *
         * @param result The execution result that led to the exception.
         * @param s      The detail message.
         */
        public AlreadyExists(ExecResult result, String s) {
            super(result, s);
        }
    }

    /**
     * Exception class indicating that the resource provided to the Kubernetes cluster is invalid.
     */
    public static class InvalidResource extends KubeClusterException {

        /**
         * Constructs a new InvalidResource exception with the specified execution result and detail message.
         *
         * @param result The execution result that led to the exception.
         * @param s      The detail message.
         */
        public InvalidResource(ExecResult result, String s) {
            super(result, s);
        }
    }
}
