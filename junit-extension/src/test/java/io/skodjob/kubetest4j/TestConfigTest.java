/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TestConfig record.
 * Tests the data structure and validation of configuration values.
 */
class TestConfigTest {

    @Test
    @DisplayName("Should create TestConfig with minimal configuration")
    void shouldCreateTestConfigWithMinimalConfiguration() {
        // Given
        TestConfig config = new TestConfig(
            CleanupStrategy.AUTOMATIC,
            false,
            "",
            "#",
            76,
            false,
            LogCollectionStrategy.ON_FAILURE,
            "",
            false,
            List.of("pods"),
            List.of()
        );

        // Then
        assertNotNull(config);
        assertEquals(CleanupStrategy.AUTOMATIC, config.cleanup());
        assertFalse(config.storeYaml());
        assertEquals("", config.yamlStorePath());
        assertEquals("#", config.visualSeparatorChar());
        assertEquals(76, config.visualSeparatorLength());
        assertFalse(config.collectLogs());
        assertEquals(LogCollectionStrategy.ON_FAILURE, config.logCollectionStrategy());
        assertEquals("", config.logCollectionPath());
        assertFalse(config.collectPreviousLogs());
        assertEquals(List.of("pods"), config.collectNamespacedResources());
        assertEquals(0, config.collectClusterWideResources().size());
    }

    @Test
    @DisplayName("Should create TestConfig with full configuration")
    void shouldCreateTestConfigWithFullConfiguration() {
        // Given
        List<String> namespacedResources = List.of("pods", "services", "configmaps", "secrets", "deployments");
        List<String> clusterWideResources = List.of("nodes", "persistentvolumes", "storageclasses");

        TestConfig config = new TestConfig(
            CleanupStrategy.MANUAL,
            true,
            "/opt/yamls",
            "=",
            120,
            true,
            LogCollectionStrategy.AFTER_EACH,
            "/var/log/tests",
            true,
            namespacedResources,
            clusterWideResources
        );

        // Then
        assertEquals(CleanupStrategy.MANUAL, config.cleanup());
        assertTrue(config.storeYaml());
        assertEquals("/opt/yamls", config.yamlStorePath());
        assertEquals("=", config.visualSeparatorChar());
        assertEquals(120, config.visualSeparatorLength());
        assertTrue(config.collectLogs());
        assertEquals(LogCollectionStrategy.AFTER_EACH, config.logCollectionStrategy());
        assertEquals("/var/log/tests", config.logCollectionPath());
        assertTrue(config.collectPreviousLogs());
        assertEquals(namespacedResources, config.collectNamespacedResources());
        assertEquals(clusterWideResources, config.collectClusterWideResources());
    }

    @Test
    @DisplayName("Should handle empty lists correctly")
    void shouldHandleEmptyListsCorrectly() {
        // Given
        TestConfig config = new TestConfig(
            CleanupStrategy.AUTOMATIC,
            false,
            "",
            "#",
            76,
            false,
            LogCollectionStrategy.ON_FAILURE,
            "",
            false,
            List.of(),
            List.of()
        );

        // Then
        assertNotNull(config.collectNamespacedResources());
        assertEquals(0, config.collectNamespacedResources().size());
        assertNotNull(config.collectClusterWideResources());
        assertEquals(0, config.collectClusterWideResources().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"#", "=", "-", "*", "~"})
    @DisplayName("Should accept various visual separator characters")
    void shouldAcceptVariousVisualSeparatorCharacters(String separatorChar) {
        // Given/When
        TestConfig config = new TestConfig(
            CleanupStrategy.AUTOMATIC,
            false,
            "",
            separatorChar,
            76,
            false,
            LogCollectionStrategy.ON_FAILURE,
            "",
            false,
            List.of(),
            List.of()
        );

        // Then
        assertEquals(separatorChar, config.visualSeparatorChar());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 76, 100, 120, 200})
    @DisplayName("Should accept various visual separator lengths")
    void shouldAcceptVariousVisualSeparatorLengths(int length) {
        // Given/When
        TestConfig config = new TestConfig(
            CleanupStrategy.AUTOMATIC,
            false,
            "",
            "#",
            length,
            false,
            LogCollectionStrategy.ON_FAILURE,
            "",
            false,
            List.of(),
            List.of()
        );

        // Then
        assertEquals(length, config.visualSeparatorLength());
    }

    @Test
    @DisplayName("Should support all cleanup strategies")
    void shouldSupportAllCleanupStrategies() {
        for (CleanupStrategy strategy : CleanupStrategy.values()) {
            // Given/When
            TestConfig config = new TestConfig(
                strategy,
                false,
                "",
                "#",
                76,
                false,
                LogCollectionStrategy.ON_FAILURE,
                "",
                false,
                List.of(),
                List.of()
            );

            // Then
            assertEquals(strategy, config.cleanup());
        }
    }

    @Test
    @DisplayName("Should support all log collection strategies")
    void shouldSupportAllLogCollectionStrategies() {
        for (LogCollectionStrategy strategy : LogCollectionStrategy.values()) {
            // Given/When
            TestConfig config = new TestConfig(
                CleanupStrategy.AUTOMATIC,
                false,
                "",
                "#",
                76,
                false,
                strategy,
                "",
                false,
                List.of(),
                List.of()
            );

            // Then
            assertEquals(strategy, config.logCollectionStrategy());
        }
    }

    @Test
    @DisplayName("Should validate boolean flag combinations")
    void shouldValidateBooleanFlagCombinations() {
        // Test all combinations of boolean flags
        boolean[] values = {true, false};

        for (boolean storeYaml : values) {
            for (boolean collectLogs : values) {
                for (boolean collectPreviousLogs : values) {
                    // Given/When
                    TestConfig config = new TestConfig(
                        CleanupStrategy.AUTOMATIC,
                        storeYaml,
                        "",
                        "#",
                        76,
                        collectLogs,
                        LogCollectionStrategy.ON_FAILURE,
                        "",
                        collectPreviousLogs,
                        List.of(),
                        List.of()
                    );

                    // Then
                    assertEquals(storeYaml, config.storeYaml());
                    assertEquals(collectLogs, config.collectLogs());
                    assertEquals(collectPreviousLogs, config.collectPreviousLogs());
                }
            }
        }
    }
}
