/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.resources.NamespaceType;

/**
 * Abstract base class demonstrating {@code @KubernetesTest} inheritance.
 * <p>
 * Because {@code @KubernetesTest} is {@code @Inherited}, child classes automatically
 * inherit this configuration without needing to re-declare the annotation.
 * <p>
 * Child classes can override {@code @KubernetesTest} to change specific parameters
 * (e.g., cleanup strategy) without losing the {@code resourceTypes} declared here —
 * the extension walks the class hierarchy to resolve resourceTypes.
 */
@KubernetesTest(
    cleanup = CleanupStrategy.AUTOMATIC,
    resourceTypes = {NamespaceType.class}
)
public abstract class AbstractKubernetesIT {
}
