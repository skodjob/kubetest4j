/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.executor;

/**
 * Represents the result of an execution.
 *
 * @param returnCode ecode of execution
 * @param out        standard output
 * @param err        standard error output
 */
public record ExecResult(int returnCode, String out, String err) {

    /**
     * Checks if the execution was successful.
     *
     * @return {@code true} if the execution was successful, {@code false} otherwise.
     */
    public boolean exitStatus() {
        return returnCode == 0;
    }

    /**
     * Returns a string representation of the ExecResult.
     *
     * @return A string representation of the ExecResult.
     */
    @Override
    public String toString() {
        return "ExecResult{" + "returnCode=" + returnCode +
            ", stdOut='" + out + '\'' +
            ", stdErr='" + err + '\'' +
            '}';
    }
}
