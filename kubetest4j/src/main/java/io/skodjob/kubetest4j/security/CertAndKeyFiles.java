/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.security;

import java.io.File;

/**
 * Record for cert and key files on disk
 *
 * @param certFile cert file
 * @param keyFile  key file
 */
public record CertAndKeyFiles(File certFile, File keyFile) {

    /**
     * Returns path of cert file
     *
     * @return string path
     */
    public String getCertPath() {
        return certFile.getPath();
    }

    /**
     * Returns path of key file
     *
     * @return string path
     */
    public String getKeyPath() {
        return keyFile.getPath();
    }
}
