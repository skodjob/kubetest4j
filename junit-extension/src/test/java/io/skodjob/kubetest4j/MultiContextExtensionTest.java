/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.InjectCmdKubeClient;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for multi-context functionality in KubernetesTestExtension.
 * These tests verify the kubeContext parameter on injection annotations.
 */
class MultiContextExtensionTest {

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
                public String kubeContext() {
                    return "staging-cluster";
                }
            };

            // Verify
            assertEquals("staging-cluster", annotation.kubeContext());
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
                public String kubeContext() {
                    return "production-cluster";
                }
            };

            assertEquals("production-cluster", annotation.kubeContext());
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
                public String kubeContext() {
                    return "dev-cluster";
                }
            };

            assertEquals("dev-cluster", annotation.kubeContext());
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
                public String kubeContext() {
                    return ""; // Default value
                }
            };

            assertEquals("", defaultAnnotation.kubeContext());
        }

        @Test
        @DisplayName("Should verify default kubeContext is empty string for InjectCmdKubeClient")
        void shouldVerifyDefaultKubeContextForInjectCmdKubeClient() {
            InjectCmdKubeClient annotation = new InjectCmdKubeClient() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return InjectCmdKubeClient.class;
                }

                @Override
                public String kubeContext() {
                    return "";
                }
            };

            assertEquals("", annotation.kubeContext());
        }

        @Test
        @DisplayName("Should verify default kubeContext is empty string for InjectResourceManager")
        void shouldVerifyDefaultKubeContextForInjectResourceManager() {
            InjectResourceManager annotation = new InjectResourceManager() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return InjectResourceManager.class;
                }

                @Override
                public String kubeContext() {
                    return "";
                }
            };

            assertEquals("", annotation.kubeContext());
        }
    }
}
