/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration holder for test setup.
 *
 * @param namespaces                  The Kubernetes namespaces to use for testing
 * @param cleanup                     The cleanup strategy for resources
 * @param storeYaml                   Whether to store YAML representations of resources
 * @param yamlStorePath               Directory path to store YAML files
 * @param namespaceLabels             Labels to apply to the test namespaces
 * @param namespaceAnnotations        Annotations to apply to the test namespaces
 * @param visualSeparatorChar         Character to use for visual separators
 * @param visualSeparatorLength       Length of visual separator lines
 * @param collectLogs                 Whether log collection is enabled
 * @param logCollectionStrategy       When to collect logs
 * @param logCollectionPath           Directory path for log collection
 * @param collectPreviousLogs         Whether to collect previous container logs
 * @param collectNamespacedResources  Namespaced resource types to collect
 * @param collectClusterWideResources Cluster-wide resource types to collect
 * @param additionalKubeContexts      Additional context configurations for multi-context support
 */
public record TestConfig(
    List<String> namespaces,
    CleanupStrategy cleanup,
    boolean storeYaml,
    String yamlStorePath,
    List<String> namespaceLabels,
    List<String> namespaceAnnotations,
    String visualSeparatorChar,
    int visualSeparatorLength,
    boolean collectLogs,
    LogCollectionStrategy logCollectionStrategy,
    String logCollectionPath,
    boolean collectPreviousLogs,
    List<String> collectNamespacedResources,
    List<String> collectClusterWideResources,
    List<AdditionalKubeContextConfig> additionalKubeContexts
) {

    /**
     * Configuration for an additional Kubernetes context.
     *
     * @param name                 the Kubernetes context name to use
     * @param namespaces           list of namespace names for this context
     * @param cleanup              cleanup strategy for this context
     * @param namespaceLabels      labels to apply to created namespaces
     * @param namespaceAnnotations annotations to apply to created namespaces
     */
    public record AdditionalKubeContextConfig(
        String name,
        List<String> namespaces,
        CleanupStrategy cleanup,
        List<String> namespaceLabels,
        List<String> namespaceAnnotations
    ) {

        /**
         * Creates an AdditionalKubeContextConfig from a KubernetesTest.AdditionalKubeContext annotation.
         *
         * @param context the AdditionalKubeContext annotation to convert
         * @return a new AdditionalKubeContextConfig instance with values from the annotation
         */
        public static AdditionalKubeContextConfig fromAnnotation(KubernetesTest.AdditionalKubeContext context) {
            return new AdditionalKubeContextConfig(
                context.name(),
                Arrays.asList(context.namespaces()),
                context.cleanup(),
                Arrays.asList(context.namespaceLabels()),
                Arrays.asList(context.namespaceAnnotations())
            );
        }
    }
}