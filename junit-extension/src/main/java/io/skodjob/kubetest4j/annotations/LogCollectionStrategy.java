/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.annotations;

/**
 * Enumeration defining when log collection should occur during test execution.
 */
public enum LogCollectionStrategy {

    /**
     * Collect logs only when a test fails.
     * This is the most common strategy for debugging test failures.
     */
    ON_FAILURE,

    /**
     * Collect logs after each test method completes, regardless of success/failure.
     * Useful for comprehensive debugging and test analysis.
     */
    AFTER_EACH
}
