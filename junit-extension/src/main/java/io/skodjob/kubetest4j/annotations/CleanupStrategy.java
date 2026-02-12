/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.annotations;

/**
 * Strategy for cleaning up Kubernetes resources after tests.
 */
public enum CleanupStrategy {
    /**
     * Automatically clean up resources using KubeResourceManager.
     * Resources are deleted both after each test method and after all tests complete.
     */
    AUTOMATIC,

    /**
     * Never clean up resources automatically.
     * Resources must be cleaned up manually or via direct KubeResourceManager calls.
     */
    MANUAL
}
