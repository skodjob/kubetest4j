/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.installation;

/**
 * Interface containing methods that can be implemented in particular setup classes based on the installation type.
 */
public interface InstallationMethod {
    /**
     * Deploy deployment/operator method
     */
    void install();

    /**
     * Delete deployment/operator method
     */
    void delete();
}
