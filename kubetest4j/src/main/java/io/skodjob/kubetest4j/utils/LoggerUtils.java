/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import java.util.Collections;

import io.fabric8.kubernetes.api.model.HasMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Utility methods for logging.
 */
public final class LoggerUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerUtils.class);
    static final String SEPARATOR_CHAR = "#";

    /**
     * Pattern for logging resource information without namespace.
     */
    public static final String RESOURCE_LOGGER_PATTERN = "{} {}/{}";

    /**
     * Pattern for logging resource information with namespace.
     */
    public static final String RESOURCE_WITH_NAMESPACE_LOGGER_PATTERN = "{} {}/{} in {}";

    private LoggerUtils() {
        // All static methods
    }

    /**
     * Logs a separator line using the default separator character and length.
     */
    public static void logSeparator() {
        logSeparator(SEPARATOR_CHAR, 76);
    }

    /**
     * Logs a separator line with a custom delimiter character and length.
     *
     * @param delimiterChar The delimiter character.
     * @param length        The length of the separator line.
     */
    public static void logSeparator(String delimiterChar, int length) {
        LOGGER.info(String.join("", Collections.nCopies(length, delimiterChar)));
    }

    /**
     * Log resource with correct format
     *
     * @param operation operation with resource
     * @param resource  resource
     * @param <T>       The type of the resources.
     */
    public static <T extends HasMetadata> void logResource(String operation, T resource) {
        logResource(operation, Level.INFO, resource);
    }

    /**
     * Log resource with correct format
     *
     * @param operation operation with resource
     * @param logLevel  log level
     * @param resource  resource
     * @param <T>       The type of the resources.
     */
    public static <T extends HasMetadata> void logResource(String operation, Level logLevel, T resource) {
        if (resource.getMetadata().getNamespace() == null) {
            LOGGER.atLevel(logLevel).log(LoggerUtils.RESOURCE_LOGGER_PATTERN,
                operation, resource.getKind(),
                resource.getMetadata().getName());
        } else {
            LOGGER.atLevel(logLevel).log(LoggerUtils.RESOURCE_WITH_NAMESPACE_LOGGER_PATTERN,
                operation,
                resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace());
        }
    }
}
