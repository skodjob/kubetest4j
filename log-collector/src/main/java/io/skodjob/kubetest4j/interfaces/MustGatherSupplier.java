/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.interfaces;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Must gather supplier which should be called during test exception
 */
public interface MustGatherSupplier {

    /**
     * Save kubernetes state
     * Use your own LogCollector configuration
     *
     * @param context junit5 extension context
     */
    void saveKubernetesState(ExtensionContext context);
}
