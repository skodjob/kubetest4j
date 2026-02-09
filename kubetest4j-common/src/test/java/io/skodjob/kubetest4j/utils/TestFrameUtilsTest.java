/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestVisualSeparator
class TestFrameUtilsTest {

    @Test
    void testGetFileFromResourceAsStreamSuccess() {
        // Test with a file that should exist in the test resources
        InputStream inputStream = TestFrameUtils.getFileFromResourceAsStream("resources.yaml");
        assertNotNull(inputStream);
    }

    @Test
    void testGetFileFromResourceAsStreamNotFound() {
        // Test with a file that doesn't exist
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            TestFrameUtils.getFileFromResourceAsStream("non-existent-file.yaml"));
        assertTrue(exception.getMessage().contains("file not found! non-existent-file.yaml"));
    }

    @Test
    void testConfigFromYamlString() {
        String yamlContent = """
            name: test
            value: 123
            """;

        TestConfig config = TestFrameUtils.configFromYaml(yamlContent, TestConfig.class);
        assertNotNull(config);
        assertEquals("test", config.getName());
        assertEquals(123, config.getValue());
    }

    @Test
    void testConfigFromYamlStringInvalidFormat() {
        String invalidYaml = "invalid: yaml: content: [";

        assertThrows(RuntimeException.class, () ->
            TestFrameUtils.configFromYaml(invalidYaml, TestConfig.class));
    }

    @Test
    void testConfigFromYamlFile(@TempDir File tempDir) throws IOException {
        // Create a temporary YAML file
        File yamlFile = new File(tempDir, "test.yaml");
        try (FileWriter writer = new FileWriter(yamlFile)) {
            writer.write("name: fileTest\nvalue: 456");
        }

        TestConfig config = TestFrameUtils.configFromYaml(yamlFile, TestConfig.class);
        assertNotNull(config);
        assertEquals("fileTest", config.getName());
        assertEquals(456, config.getValue());
    }

    @Test
    void testConfigFromYamlFileNotFound() {
        File nonExistentFile = new File("non-existent.yaml");

        assertThrows(RuntimeException.class, () ->
            TestFrameUtils.configFromYaml(nonExistentFile, TestConfig.class));
    }

    @Test
    void testRunUntilPassSuccess() {
        Callable<String> successfulCallable = () -> "success";

        String result = TestFrameUtils.runUntilPass(3, successfulCallable);
        assertEquals("success", result);
    }

    @Test
    void testRunUntilPassEventualSuccess() {
        // Callable that fails twice then succeeds
        Callable<String> eventuallySuccessfulCallable = new Callable<String>() {
            private int attempts = 0;

            @Override
            public String call() throws Exception {
                attempts++;
                if (attempts < 3) {
                    throw new Exception("Not ready yet");
                }
                return "finally success";
            }
        };

        String result = TestFrameUtils.runUntilPass(5, eventuallySuccessfulCallable);
        assertEquals("finally success", result);
    }

    @Test
    void testRunUntilPassAlwaysFails() {
        Callable<String> alwaysFailsCallable = () -> {
            throw new Exception("Always fails");
        };

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            TestFrameUtils.runUntilPass(2, alwaysFailsCallable));
        assertTrue(exception.getMessage().contains("Callable did not pass in 2 attempts"));
    }

    @Test
    void testRunUntilPassWithError() {
        Callable<String> errorCallable = () -> {
            throw new Error("Fatal error");
        };

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            TestFrameUtils.runUntilPass(1, errorCallable));
        assertTrue(exception.getMessage().contains("Callable did not pass in 1 attempts"));
    }

    @Test
    void testRunUntilPassWithInterruptedException() throws InterruptedException {
        Callable<String> interruptedCallable = () -> {
            Thread.currentThread().interrupt();
            Thread.sleep(100); // This will throw InterruptedException
            return "never reached";
        };

        assertThrows(RuntimeException.class, () ->
            TestFrameUtils.runUntilPass(1, interruptedCallable));
    }

    // Test class for YAML parsing
    static class TestConfig {
        private String name;
        private int value;

        TestConfig() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}