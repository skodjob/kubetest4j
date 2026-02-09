/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.security;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestVisualSeparator
class CertAndKeyBuilderTest {
    static final String ROOT_CA = "C=COM, L=Boston, O=Example, CN=ExampleRootCA";
    static final String INTERMEDIATE_CA = "C=COM, L=Boston, O=Example, CN=ExampleIntermediateCA";
    static final String END_SUBJECT = "C=COM, L=Boston, O=Example, CN=end-app.example.io";
    static final String APP_SUBJECT = "C=COM, L=Boston, O=Example, CN=app.example.io";

    static final String COMPARE_ROOT_DN = "CN=ExampleRootCA,O=Example,L=Boston,C=COM";
    static final String COMPARE_INTERMEDIATE_DN = "CN=ExampleIntermediateCA,O=Example,L=Boston,C=COM";

    @Test
    void testGenerateCerts() {
        CertAndKey ca = CertAndKeyBuilder.rootCaCertBuilder()
            .withIssuerDn(ROOT_CA)
            .withSubjectDn(ROOT_CA)
            .build();

        assertEquals(COMPARE_ROOT_DN, ca.certificate().getIssuerX500Principal().getName());
        assertDoesNotThrow(() -> ca.certificate().checkValidity());

        CertAndKey intermediateCa = CertAndKeyBuilder.intermediateCaCertBuilder(ca)
            .withIssuerDn(INTERMEDIATE_CA)
            .withSubjectDn(INTERMEDIATE_CA)
            .build();

        assertEquals(COMPARE_INTERMEDIATE_DN, intermediateCa.certificate().getIssuerX500Principal().getName());
        assertDoesNotThrow(() -> intermediateCa.certificate().checkValidity());

        CertAndKey appCert = CertAndKeyBuilder.appCaCertBuilder(ca)
            .withSubjectDn(APP_SUBJECT)
            .build();

        assertEquals(COMPARE_ROOT_DN, appCert.certificate().getIssuerX500Principal().getName());
        assertDoesNotThrow(() -> appCert.certificate().checkValidity());

        CertAndKey endAppCert = CertAndKeyBuilder.endEntityCertBuilder(intermediateCa)
            .withSubjectDn(END_SUBJECT)
            .withSanDnsName("*.example.io")
            .build();

        assertEquals(COMPARE_INTERMEDIATE_DN, endAppCert.certificate().getIssuerX500Principal().getName());
        assertDoesNotThrow(() -> endAppCert.certificate().checkValidity());

        // check cert signing
        assertDoesNotThrow(() -> appCert.certificate().verify(ca.getPublicKey()));
        assertDoesNotThrow(() -> endAppCert.certificate().verify(intermediateCa.getPublicKey()));
        assertDoesNotThrow(() -> intermediateCa.certificate().verify(ca.getPublicKey()));
    }
}
