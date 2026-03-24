/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.LogCollectionStrategy;

import java.util.List;

/**
 * Configuration holder for test setup.
 *
 * @param cleanup                     The cleanup strategy for resources
 * @param storeYaml                   Whether to store YAML representations of resources
 * @param yamlStorePath               Directory path to store YAML files
 * @param visualSeparatorChar         Character to use for visual separators
 * @param visualSeparatorLength       Length of visual separator lines
 * @param collectLogs                 Whether log collection is enabled
 * @param logCollectionStrategy       When to collect logs
 * @param logCollectionPath           Directory path for log collection
 * @param collectPreviousLogs         Whether to collect previous container logs
 * @param collectNamespacedResources  Namespaced resource types to collect
 * @param collectClusterWideResources Cluster-wide resource types to collect
 */
public record TestConfig(
    CleanupStrategy cleanup,
    boolean storeYaml,
    String yamlStorePath,
    String visualSeparatorChar,
    int visualSeparatorLength,
    boolean collectLogs,
    LogCollectionStrategy logCollectionStrategy,
    String logCollectionPath,
    boolean collectPreviousLogs,
    List<String> collectNamespacedResources,
    List<String> collectClusterWideResources
) {
}
