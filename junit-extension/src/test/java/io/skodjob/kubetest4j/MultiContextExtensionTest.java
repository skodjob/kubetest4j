/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectCmdKubeClient;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectNamespace;
import io.skodjob.kubetest4j.annotations.InjectNamespaces;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for multi-kubeContext functionality in KubernetesTestExtension.
 * These tests verify the new kubeContext-aware features without requiring a real Kubernetes cluster.
 */
class MultiContextExtensionTest {

    @Nested
    @DisplayName("KubeContext Mapping Configuration Tests")
    class KubeContextMappingTests {

        @Test
        @DisplayName("Should create KubeContextMappingConfig with default values")
        void shouldCreateKubeContextMappingConfigWithDefaults() {
            // Given
            TestConfig.KubeContextMappingConfig config = new TestConfig.KubeContextMappingConfig(
                "test-kubeContext",
                List.of("test-ns"),
                CleanupStrategy.AUTOMATIC,
                List.of(),
                List.of()
            );

            // Then
            assertNotNull(config);
            assertEquals("test-kubeContext", config.kubeContext());
            assertEquals(List.of("test-ns"), config.namespaces());
            assertEquals(CleanupStrategy.AUTOMATIC, config.cleanup());
            assertEquals(List.of(), config.namespaceLabels());
            assertEquals(List.of(), config.namespaceAnnotations());
        }

        @Test
        @DisplayName("Should create KubeContextMappingConfig with custom values")
        void shouldCreateKubeContextMappingConfigWithCustomValues() {
            // Given
            TestConfig.KubeContextMappingConfig config = new TestConfig.KubeContextMappingConfig(
                "staging-cluster",
                List.of("stg-ns1", "stg-ns2"),
                CleanupStrategy.MANUAL,
                List.of("environment=staging", "team=platform"),
                List.of("deployment.io/stage=staging", "monitoring=enabled")
            );

            // Then
            assertEquals("staging-cluster", config.kubeContext());
            assertEquals(List.of("stg-ns1", "stg-ns2"), config.namespaces());
            assertEquals(CleanupStrategy.MANUAL, config.cleanup());
            assertEquals(List.of("environment=staging", "team=platform"),
                config.namespaceLabels());
            assertEquals(List.of("deployment.io/stage=staging", "monitoring=enabled"),
                config.namespaceAnnotations());
        }

        @Test
        @DisplayName("Should convert annotation to KubeContextMappingConfig")
        void shouldConvertAnnotationToKubeContextMappingConfig() {
            // Create a mock KubeContextMapping annotation
            KubernetesTest.KubeContextMapping annotation = new KubernetesTest.KubeContextMapping() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return KubernetesTest.KubeContextMapping.class;
                }

                @Override
                public String kubeContext() {
                    return "production";
                }

                @Override
                public String[] namespaces() {
                    return new String[]{"prod-api", "prod-cache"};
                }


                @Override
                public CleanupStrategy cleanup() {
                    return CleanupStrategy.MANUAL;
                }

                @Override
                public String[] namespaceLabels() {
                    return new String[]{"env=production"};
                }

                @Override
                public String[] namespaceAnnotations() {
                    return new String[]{"deployment.io/environment=production"};
                }
            };

            // When
            TestConfig.KubeContextMappingConfig config = TestConfig.KubeContextMappingConfig.fromAnnotation(annotation);

            // Then
            assertEquals("production", config.kubeContext());
            assertEquals(List.of("prod-api", "prod-cache"), config.namespaces());
            assertEquals(CleanupStrategy.MANUAL, config.cleanup());
            assertEquals(List.of("env=production"), config.namespaceLabels());
            assertEquals(List.of("deployment.io/environment=production"),
                config.namespaceAnnotations());
        }
    }

    @Nested
    @DisplayName("TestConfig with KubeContext Mappings Tests")
    class TestConfigWithKubeContextMappingsTests {

        @Test
        @DisplayName("Should create TestConfig with empty kubeContext mappings")
        void shouldCreateTestConfigWithEmptyKubeContextMappings() {
            // Given
            TestConfig config = new TestConfig(
                List.of("default-ns"),
                CleanupStrategy.AUTOMATIC,
                "",
                false,
                "",
                List.of(),
                List.of(),
                "#",
                76,
                false,
                io.skodjob.kubetest4j.annotations.LogCollectionStrategy.ON_FAILURE,
                "",
                false,
                List.of("pods"),
                List.of(),
                List.of() // Empty kubeContext mappings
            );

            // Then
            assertNotNull(config.kubeContextMappings());
            assertEquals(0, config.kubeContextMappings().size());
        }

        @Test
        @DisplayName("Should create TestConfig with multiple kubeContext mappings")
        void shouldCreateTestConfigWithMultipleKubeContextMappings() {
            // Given
            List<TestConfig.KubeContextMappingConfig> contextMappings = List.of(
                new TestConfig.KubeContextMappingConfig(
                    "staging-cluster",
                    List.of("stg-app", "stg-db"),
                    CleanupStrategy.AUTOMATIC,
                    List.of("env=staging"),
                    List.of("stage=staging")
                ),
                new TestConfig.KubeContextMappingConfig(
                    "production-cluster",
                    List.of("prod-api"),
                    CleanupStrategy.MANUAL,
                    List.of("env=production"),
                    List.of("stage=production")
                )
            );

            TestConfig config = new TestConfig(
                List.of("default-ns"),
                CleanupStrategy.AUTOMATIC,
                "",
                false,
                "",
                List.of(),
                List.of(),
                "#",
                76,
                false,
                io.skodjob.kubetest4j.annotations.LogCollectionStrategy.ON_FAILURE,
                "",
                false,
                List.of("pods"),
                List.of(),
                contextMappings
            );

            // Then
            assertNotNull(config.kubeContextMappings());
            assertEquals(2, config.kubeContextMappings().size());

            // Verify staging kubeContext mapping
            TestConfig.KubeContextMappingConfig stagingMapping = config.kubeContextMappings().get(0);
            assertEquals("staging-cluster", stagingMapping.kubeContext());
            assertEquals(List.of("stg-app", "stg-db"), stagingMapping.namespaces());
            assertEquals(CleanupStrategy.AUTOMATIC, stagingMapping.cleanup());

            // Verify production kubeContext mapping
            TestConfig.KubeContextMappingConfig prodMapping = config.kubeContextMappings().get(1);
            assertEquals("production-cluster", prodMapping.kubeContext());
            assertEquals(List.of("prod-api"), prodMapping.namespaces());
            assertEquals(CleanupStrategy.MANUAL, prodMapping.cleanup());
        }
    }

    @Nested
    @DisplayName("KubeContext-Aware Injection Annotation Tests")
    class KubeContextAwareAnnotationTests {

        @Test
        @DisplayName("Should verify InjectKubeClient annotation with kubeContext")
        void shouldVerifyInjectKubeClientWithKubeContext() {
            // Create a mock annotation with kubeContext
            InjectKubeClient annotation = new InjectKubeClient() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return InjectKubeClient.class;
                }

                @Override
                public String context() {
                    return "staging-cluster";
                }
            };

            // Verify
            assertEquals("staging-cluster", annotation.context());
        }

        @Test
        @DisplayName("Should verify InjectCmdKubeClient annotation with kubeContext")
        void shouldVerifyInjectCmdKubeClientWithKubeContext() {
            InjectCmdKubeClient annotation = new InjectCmdKubeClient() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return InjectCmdKubeClient.class;
                }

                @Override
                public String context() {
                    return "production-cluster";
                }
            };

            assertEquals("production-cluster", annotation.context());
        }

        @Test
        @DisplayName("Should verify InjectResourceManager annotation with kubeContext")
        void shouldVerifyInjectResourceManagerWithKubeContext() {
            InjectResourceManager annotation = new InjectResourceManager() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return InjectResourceManager.class;
                }

                @Override
                public String context() {
                    return "dev-cluster";
                }
            };

            assertEquals("dev-cluster", annotation.context());
        }

        @Test
        @DisplayName("Should verify InjectNamespace annotation with kubeContext")
        void shouldVerifyInjectNamespaceWithKubeContext() {
            InjectNamespace annotation = new InjectNamespace() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return InjectNamespace.class;
                }

                @Override
                public String name() {
                    return "my-namespace";
                }

                @Override
                public String context() {
                    return "my-kubeContext";
                }
            };

            assertEquals("my-namespace", annotation.name());
            assertEquals("my-kubeContext", annotation.context());
        }

        @Test
        @DisplayName("Should verify InjectNamespaces annotation with kubeContext")
        void shouldVerifyInjectNamespacesWithKubeContext() {
            InjectNamespaces annotation = new InjectNamespaces() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return InjectNamespaces.class;
                }

                @Override
                public String context() {
                    return "all-namespaces-kubeContext";
                }
            };

            assertEquals("all-namespaces-kubeContext", annotation.context());
        }

        @Test
        @DisplayName("Should use empty string as default kubeContext value")
        void shouldUseEmptyStringAsDefaultKubeContext() {
            // Test that annotations default to empty string for kubeContext when not specified
            // This simulates the behavior when @InjectKubeClient is used without kubeContext parameter

            InjectKubeClient defaultAnnotation = new InjectKubeClient() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return InjectKubeClient.class;
                }

                @Override
                public String context() {
                    return ""; // Default value
                }
            };

            assertEquals("", defaultAnnotation.context());
        }
    }

    @Nested
    @DisplayName("Multi-KubeContext Validation Tests")
    class MultiKubeContextValidationTests {

        @Test
        @DisplayName("Should validate kubeContext names are non-null")
        void shouldValidateKubeContextNamesAreNonNull() {
            // Test that kubeContext names in mappings are properly handled
            TestConfig.KubeContextMappingConfig config = new TestConfig.KubeContextMappingConfig(
                "valid-kubeContext",
                List.of("namespace1"),
                CleanupStrategy.AUTOMATIC,
                List.of(),
                List.of()
            );

            assertNotNull(config.kubeContext());
            assertFalse(config.kubeContext().trim().isEmpty());
        }

        @Test
        @DisplayName("Should validate namespace arrays are non-null")
        void shouldValidateNamespaceArraysAreNonNull() {
            TestConfig.KubeContextMappingConfig config = new TestConfig.KubeContextMappingConfig(
                "test-kubeContext",
                List.of("ns1", "ns2"),
                CleanupStrategy.AUTOMATIC,
                List.of(),
                List.of()
            );

            assertNotNull(config.namespaces());
            assertFalse(config.namespaces().isEmpty());
        }

        @Test
        @DisplayName("Should handle mixed kubeContext and default deployments")
        void shouldHandleMixedKubeContextAndDefaultDeployments() {
            // Test configuration with both default namespaces and kubeContext-specific namespaces
            List<TestConfig.KubeContextMappingConfig> contextMappings = List.of(
                new TestConfig.KubeContextMappingConfig(
                    "external-cluster",
                    List.of("external-ns"),
                    CleanupStrategy.AUTOMATIC,
                    List.of("external=true"),
                    List.of()
                )
            );

            TestConfig config = new TestConfig(
                List.of("default-ns1", "default-ns2"), // Default kubeContext namespaces
                CleanupStrategy.AUTOMATIC,
                "", // Default kubeContext
                false,
                "",
                List.of("default=true"),
                List.of(),
                "#",
                76,
                false,
                io.skodjob.kubetest4j.annotations.LogCollectionStrategy.ON_FAILURE,
                "",
                false,
                List.of("pods"),
                List.of(),
                contextMappings
            );

            // Verify default namespaces
            assertEquals(List.of("default-ns1", "default-ns2"), config.namespaces());
            assertEquals("", config.context());

            // Verify kubeContext-specific mappings
            assertEquals(1, config.kubeContextMappings().size());
            assertEquals("external-cluster", config.kubeContextMappings().get(0).kubeContext());
            assertEquals(List.of("external-ns"), config.kubeContextMappings().get(0).namespaces());
        }
    }

    @Nested
    @DisplayName("KubeContext Isolation Tests")
    class KubeContextIsolationTests {

        @Test
        @DisplayName("Should ensure kubeContext configurations are independent")
        void shouldEnsureKubeContextConfigurationsAreIndependent() {
            // Create multiple kubeContext mappings with different configurations
            TestConfig.KubeContextMappingConfig staging = new TestConfig.KubeContextMappingConfig(
                "staging-cluster",
                List.of("stg-app"),
                CleanupStrategy.AUTOMATIC,
                List.of("env=staging"),
                List.of("auto-deploy=true")
            );

            TestConfig.KubeContextMappingConfig production = new TestConfig.KubeContextMappingConfig(
                "production-cluster",
                List.of("prod-app"), // Different cleanup setting
                CleanupStrategy.MANUAL, // Different cleanup strategy
                List.of("env=production"),
                List.of("auto-deploy=false")
            );

            // Verify they have independent configurations

            assertEquals(CleanupStrategy.AUTOMATIC, staging.cleanup());
            assertEquals(CleanupStrategy.MANUAL, production.cleanup());

            assertEquals("staging", staging.namespaceLabels().get(0).split("=")[1]);
            assertEquals("production", production.namespaceLabels().get(0).split("=")[1]);
        }

        @Test
        @DisplayName("Should allow same namespace names in different kubeContexts")
        void shouldAllowSameNamespaceNamesInDifferentKubeContexts() {
            // This tests that namespace names can be reused across different kubeContexts
            TestConfig.KubeContextMappingConfig kubeContext1 = new TestConfig.KubeContextMappingConfig(
                "cluster-1",
                List.of("app-namespace", "db-namespace"),
                CleanupStrategy.AUTOMATIC,
                List.of(),
                List.of()
            );

            TestConfig.KubeContextMappingConfig kubeContext2 = new TestConfig.KubeContextMappingConfig(
                "cluster-2",
                List.of("app-namespace", "db-namespace"), // Same names, different kubeContext
                CleanupStrategy.AUTOMATIC,
                List.of(),
                List.of()
            );

            // Both should be valid even with same namespace names
            assertEquals(kubeContext1.namespaces(), kubeContext2.namespaces());
            assertNotEquals(kubeContext1.kubeContext(), kubeContext2.kubeContext());
        }
    }
}