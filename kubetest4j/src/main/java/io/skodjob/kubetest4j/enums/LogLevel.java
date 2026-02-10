/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.enums;

import org.slf4j.event.Level;

/**
 * Enum class capturing available log levels that can be used in Test-Frame.
 */
public enum LogLevel {
    /**
     * Debug log level
     */
    DEBUG,
    /**
     * Info log level
     */
    INFO,
    /**
     * Warn log level
     */
    WARN,
    /**
     * Error log level
     */
    ERROR,
    /**
     * Trace log level
     */
    TRACE;

    /**
     * Based on {@param level} returns corresponding slf4j's log level (as {@link Level}.
     *
     * @param level     Desired log level.
     *
     * @return  based on {@param level} returns corresponding slf4j's log level (as {@link Level}.
     */
    public static Level logLevelToLevel(LogLevel level) {
        return switch (level) {
            case DEBUG -> Level.DEBUG;
            case WARN -> Level.WARN;
            case ERROR -> Level.ERROR;
            case TRACE -> Level.TRACE;
            case INFO -> Level.INFO;  // Explicit case instead of default for better maintainability
        };
    }
}
