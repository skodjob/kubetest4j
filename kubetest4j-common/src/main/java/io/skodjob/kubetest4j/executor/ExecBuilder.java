/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.executor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.model.EnvVar;

/**
 * Builder class for creating and executing commands.
 */
public class ExecBuilder {

    private String input;
    private List<String> command;
    private Set<EnvVar> envVars;
    private int timeout;
    private boolean logToOutput;
    private boolean throwErrors;

    /**
     * Constructor
     */
    public ExecBuilder() {
        // empty constructor
    }

    /**
     * Sets the command to execute.
     *
     * @param command The command to execute.
     * @return The ExecBuilder instance.
     */
    public ExecBuilder withCommand(List<String> command) {
        this.command = command;
        return this;
    }

    /**
     * Sets the command to execute.
     *
     * @param cmd The command to execute.
     * @return The ExecBuilder instance.
     */
    public ExecBuilder withCommand(String... cmd) {
        this.command = Arrays.asList(cmd);
        return this;
    }

    /**
     * Sets the environment variables.
     *
     * @param envVars The environment variables.
     * @return The ExecBuilder instance.
     */
    public ExecBuilder withEnvVars(Set<EnvVar> envVars) {
        this.envVars = envVars;
        return this;
    }

    /**
     * Sets the input for the command.
     *
     * @param input The input for the command.
     * @return The ExecBuilder instance.
     */
    public ExecBuilder withInput(String input) {
        this.input = input;
        return this;
    }

    /**
     * Sets whether to log the output of the command.
     *
     * @param logToOutput Whether to log the output.
     * @return The ExecBuilder instance.
     */
    public ExecBuilder logToOutput(boolean logToOutput) {
        this.logToOutput = logToOutput;
        return this;
    }

    /**
     * Sets whether to throw errors if the command fails.
     *
     * @param throwErrors Whether to throw errors.
     * @return The ExecBuilder instance.
     */
    public ExecBuilder throwErrors(boolean throwErrors) {
        this.throwErrors = throwErrors;
        return this;
    }

    /**
     * Sets the timeout for the command.
     *
     * @param timeout The timeout for the command.
     * @return The ExecBuilder instance.
     */
    public ExecBuilder timeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Executes the command with the provided configuration.
     *
     * @return The execution result.
     */
    public ExecResult exec() {
        return Exec.exec(input, command, envVars, timeout, logToOutput, throwErrors);
    }
}
