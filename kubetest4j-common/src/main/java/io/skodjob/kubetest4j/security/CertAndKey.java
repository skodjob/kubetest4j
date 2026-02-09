/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.security;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * Record for certificate and key in memory
 *
 * @param certificate certificate
 * @param privateKey  key
 */
public record CertAndKey(X509Certificate certificate, PrivateKey privateKey) {

    /**
     * Returns certificate
     *
     * @return certificate
     */
    public X509Certificate getCertificate() {
        return certificate;
    }

    /**
     * Returns public key
     *
     * @return public key
     */
    public PublicKey getPublicKey() {
        return certificate.getPublicKey();
    }

    /**
     * Returns private key
     *
     * @return private key
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
