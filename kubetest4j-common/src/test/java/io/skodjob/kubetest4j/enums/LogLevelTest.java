/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.enums;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for LogLevel enum
 */
@TestVisualSeparator
class LogLevelTest {

    @Test
    void testLogLevelToLevelDebug() {
        Level result = LogLevel.logLevelToLevel(LogLevel.DEBUG);
        assertEquals(Level.DEBUG, result);
    }

    @Test
    void testLogLevelToLevelInfo() {
        Level result = LogLevel.logLevelToLevel(LogLevel.INFO);
        assertEquals(Level.INFO, result);
    }

    @Test
    void testLogLevelToLevelWarn() {
        Level result = LogLevel.logLevelToLevel(LogLevel.WARN);
        assertEquals(Level.WARN, result);
    }

    @Test
    void testLogLevelToLevelError() {
        Level result = LogLevel.logLevelToLevel(LogLevel.ERROR);
        assertEquals(Level.ERROR, result);
    }

    @Test
    void testLogLevelToLevelTrace() {
        Level result = LogLevel.logLevelToLevel(LogLevel.TRACE);
        assertEquals(Level.TRACE, result);
    }

    @Test
    void testAllLogLevelEnumValues() {
        // Test that all LogLevel enum values are properly mapped
        for (LogLevel logLevel : LogLevel.values()) {
            Level result = LogLevel.logLevelToLevel(logLevel);

            // Verify each mapping is correct
            switch (logLevel) {
                case DEBUG -> assertEquals(Level.DEBUG, result);
                case INFO -> assertEquals(Level.INFO, result);
                case WARN -> assertEquals(Level.WARN, result);
                case ERROR -> assertEquals(Level.ERROR, result);
                case TRACE -> assertEquals(Level.TRACE, result);
                default -> throw new IllegalStateException("Unexpected LogLevel: " + logLevel);
            }
        }
    }

    @Test
    void testLogLevelEnumCompletenessForJava21SwitchExpression() {
        // Verify that we have exactly 5 LogLevel values
        // This test will fail if new enum values are added without updating the switch expression
        LogLevel[] values = LogLevel.values();
        assertEquals(5, values.length,
            "LogLevel enum should have exactly 5 values. " +
                "If this fails, update the logLevelToLevel switch expression");

        // Verify each expected enum value exists
        assertEquals(LogLevel.DEBUG, LogLevel.valueOf("DEBUG"));
        assertEquals(LogLevel.INFO, LogLevel.valueOf("INFO"));
        assertEquals(LogLevel.WARN, LogLevel.valueOf("WARN"));
        assertEquals(LogLevel.ERROR, LogLevel.valueOf("ERROR"));
        assertEquals(LogLevel.TRACE, LogLevel.valueOf("TRACE"));
    }
}