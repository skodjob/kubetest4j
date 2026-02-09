/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for TestFrame.
 */
@SuppressWarnings({"checkstyle:ClassFanOutComplexity"})
public final class TestFrameUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFrameUtils.class);

    /**
     * Default timeout for asynchronous tests.
     */
    public static final int DEFAULT_TIMEOUT_DURATION = 30;

    /**
     * Default timeout unit for asynchronous tests.
     */
    public static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;

    private TestFrameUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Retrieves a file from the classpath as an input stream.
     *
     * @param fileName The name of the file.
     * @return An input stream for the file.
     * @throws IllegalArgumentException if the file is not found.
     */
    public static InputStream getFileFromResourceAsStream(String fileName) {
        // The class loader that loaded the class
        ClassLoader classLoader = TestFrameUtils.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(fileName);

        // the stream holding the file content
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return inputStream;
        }
    }

    /**
     * Parses YAML configuration into an object.
     *
     * @param yamlFile The YAML file content.
     * @param c        The class of the object to parse into.
     * @param <T>      The type of the object.
     * @return The parsed object.
     * @throws IllegalArgumentException if the YAML is invalid.
     * @throws RuntimeException         if an I/O error occurs.
     */
    public static <T> T configFromYaml(String yamlFile, Class<T> c) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.readValue(yamlFile, c);
        } catch (InvalidFormatException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses YAML configuration into an object.
     *
     * @param yamlFile The YAML file.
     * @param c        The class of the object to parse into.
     * @param <T>      The type of the object.
     * @return The parsed object.
     * @throws IllegalArgumentException if the YAML is invalid.
     * @throws RuntimeException         if an I/O error occurs.
     */
    public static <T> T configFromYaml(File yamlFile, Class<T> c) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.readValue(yamlFile, c);
        } catch (InvalidFormatException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs a callable function until it passes or the maximum number of retries is reached.
     *
     * @param retry The maximum number of retries.
     * @param fn    The callable function.
     * @param <T>   The return type of the callable.
     * @return The value from the first successful call to the callable.
     * @throws IllegalStateException if the callable does not pass after all retries.
     */
    public static <T> T runUntilPass(int retry, Callable<T> fn) {
        for (int i = 0; i < retry; i++) {
            try {
                LOGGER.debug("Running Callable, attempt: {}", i);
                return fn.call();
            } catch (Exception | Error ex) {
                LOGGER.warn("Callable failed: {}", ex.getMessage());
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException(String.format("Callable did not pass in %s attempts", retry));
    }
}
