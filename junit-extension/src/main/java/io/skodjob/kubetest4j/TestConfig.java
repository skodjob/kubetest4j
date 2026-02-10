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
 * @param context                     The Kubernetes cluster kubeContext to use
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
 * @param kubeContextMappings         Context mappings for multi-cluster support
 */
public record TestConfig(
    List<String> namespaces,
    CleanupStrategy cleanup,
    String context,
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
    List<KubeContextMappingConfig> kubeContextMappings
) {

    /**
     * Configuration for a specific Kubernetes kubeContext mapping.
     *
     * @param kubeContext          the Kubernetes kubeContext name to use
     * @param namespaces           list of namespace names for this kubeContext
     * @param cleanup              cleanup strategy for this kubeContext
     * @param namespaceLabels      labels to apply to created namespaces
     * @param namespaceAnnotations annotations to apply to created namespaces
     */
    public record KubeContextMappingConfig(
        String kubeContext,
        List<String> namespaces,
        CleanupStrategy cleanup,
        List<String> namespaceLabels,
        List<String> namespaceAnnotations
    ) {

        /**
         * Creates a KubeContextMappingConfig from a KubernetesTest.KubeContextMapping annotation.
         *
         * @param mapping the KubeContextMapping annotation to convert
         * @return a new KubeContextMappingConfig instance with values from the annotation
         */
        public static KubeContextMappingConfig fromAnnotation(KubernetesTest.KubeContextMapping mapping) {
            return new KubeContextMappingConfig(
                mapping.kubeContext(),
                Arrays.asList(mapping.namespaces()),
                mapping.cleanup(),
                Arrays.asList(mapping.namespaceLabels()),
                Arrays.asList(mapping.namespaceAnnotations())
            );
        }
    }
}