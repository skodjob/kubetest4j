/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.security;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestVisualSeparator
class SecurityUtilsTest {
    static final String ROOT_CA = "C=CZ, L=Prague, O=TestExample, CN=TestRootCA";
    static final String INTERMEDIATE_CA = "C=CZ, L=Prague, O=TestExample, CN=TestIntermediateCA";
    static final String END_SUBJECT = "C=CZ, L=Prague, O=TestExample, CN=end-app.test.io";
    static final String APP_SUBJECT = "C=CZ, L=Prague, O=TestExample, CN=app.test.io";

    CertAndKey ca;
    CertAndKey intermediateCa;
    CertAndKey appCert;
    CertAndKey endAppCert;

    @BeforeAll
    void setup() {
        ca = CertAndKeyBuilder.rootCaCertBuilder()
            .withIssuerDn(ROOT_CA)
            .withSubjectDn(ROOT_CA)
            .build();

        intermediateCa = CertAndKeyBuilder.intermediateCaCertBuilder(ca)
            .withIssuerDn(INTERMEDIATE_CA)
            .withSubjectDn(INTERMEDIATE_CA)
            .build();

        appCert = CertAndKeyBuilder.appCaCertBuilder(ca)
            .withSubjectDn(APP_SUBJECT)
            .build();

        endAppCert = CertAndKeyBuilder.endEntityCertBuilder(intermediateCa)
            .withSubjectDn(END_SUBJECT)
            .withSanDnsName("*.test.io")
            .build();
    }

    @Test
    void testExportCertsToPem() throws IOException {
        CertAndKeyFiles all = SecurityUtils.exportToPemFiles(ca, intermediateCa, appCert);

        String content = Files.readString(Paths.get(all.getCertPath()));
        assertNotEquals("", content);
    }

    @Test
    void testExportDataToCa() throws IOException {
        File caCert = SecurityUtils.exportCaDataToFile(ca.getPublicKey().toString(), "ca", ".crt");

        String content = Files.readString(caCert.toPath());
        assertNotEquals("", content);
    }

    @Test
    void testExportToPemFilesEmptyArray() {
        assertThrows(IllegalArgumentException.class, SecurityUtils::exportToPemFiles);
    }

    @Test
    void testExportToPemFilesSingleCert() throws IOException {
        CertAndKeyFiles result = SecurityUtils.exportToPemFiles(ca);

        assertNotNull(result);
        assertNotNull(result.getCertPath());
        assertNotNull(result.getKeyPath());

        assertTrue(new File(result.getCertPath()).exists());
        assertTrue(new File(result.getKeyPath()).exists());

        String certContent = Files.readString(new File(result.getCertPath()).toPath());
        String keyContent = Files.readString(new File(result.getKeyPath()).toPath());

        assertTrue(certContent.contains("BEGIN CERTIFICATE"));
        assertTrue(certContent.contains("END CERTIFICATE"));
        assertTrue(keyContent.contains("BEGIN RSA PRIVATE KEY"));
        assertTrue(keyContent.contains("END RSA PRIVATE KEY"));
    }

    @Test
    void testExportToPemFilesMultipleCerts() throws IOException {
        CertAndKeyFiles result = SecurityUtils.exportToPemFiles(ca, intermediateCa, appCert);

        assertNotNull(result);
        assertTrue(new File(result.getCertPath()).exists());
        assertTrue(new File(result.getKeyPath()).exists());

        String certContent = Files.readString(new File(result.getCertPath()).toPath());

        // Should contain all three certificates
        int beginCount = certContent.split("BEGIN CERTIFICATE").length - 1;
        int endCount = certContent.split("END CERTIFICATE").length - 1;

        assertEquals(3, beginCount);
        assertEquals(3, endCount);
    }

    @Test
    void testConvertPrivateKeyToPKCS8File() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        File pkcs8File = SecurityUtils.convertPrivateKeyToPKCS8File(ca.getPrivateKey());

        assertNotNull(pkcs8File);
        assertTrue(pkcs8File.exists());

        String content = Files.readString(pkcs8File.toPath());
        assertTrue(content.contains("BEGIN RSA PRIVATE KEY"));
        assertTrue(content.contains("END RSA PRIVATE KEY"));
    }

    @Test
    void testExportCaDataToFile() throws IOException {
        String testData = """
            Test CA Certificate Data
            -----BEGIN CERTIFICATE-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA
            -----END CERTIFICATE-----""";
        String prefix = "test-ca";
        String suffix = ".crt";

        File resultFile = SecurityUtils.exportCaDataToFile(testData, prefix, suffix);

        assertNotNull(resultFile);
        assertTrue(resultFile.exists());
        assertTrue(resultFile.getName().startsWith(prefix + "-"));
        assertTrue(resultFile.getName().endsWith(suffix));

        String content = Files.readString(resultFile.toPath());
        assertEquals(testData, content);
    }

    @Test
    void testExportCaDataToFileWithEmptyData() throws IOException {
        String emptyData = "";
        String prefix = "empty";
        String suffix = ".txt";

        File resultFile = SecurityUtils.exportCaDataToFile(emptyData, prefix, suffix);

        assertNotNull(resultFile);
        assertTrue(resultFile.exists());

        String content = Files.readString(resultFile.toPath());
        assertEquals("", content);
    }

    @Test
    void testContainsAllDNTrue() {
        String principal1 = "C=CZ, L=Prague, O=TestExample, CN=TestRootCA";
        String principal2 = "CN=TestRootCA, O=TestExample";

        assertTrue(SecurityUtils.containsAllDN(principal1, principal2));
    }

    @Test
    void testContainsAllDNFalse() {
        String principal1 = "C=CZ, L=Prague, O=TestExample, CN=TestRootCA";
        String principal2 = "CN=DifferentCA, O=DifferentOrg";

        assertFalse(SecurityUtils.containsAllDN(principal1, principal2));
    }

    @Test
    void testContainsAllDNPartialMatch() {
        String principal1 = "C=CZ, L=Prague, O=TestExample, CN=TestRootCA";
        String principal2 = "CN=TestRootCA, O=DifferentOrg";

        assertFalse(SecurityUtils.containsAllDN(principal1, principal2));
    }

    @Test
    void testContainsAllDNSameOrder() {
        String principal1 = "CN=TestRootCA, O=TestExample, L=Prague, C=CZ";
        String principal2 = "CN=TestRootCA, O=TestExample, L=Prague, C=CZ";

        assertTrue(SecurityUtils.containsAllDN(principal1, principal2));
    }

    @Test
    void testContainsAllDNDifferentOrder() {
        String principal1 = "C=CZ, L=Prague, O=TestExample, CN=TestRootCA";
        String principal2 = "CN=TestRootCA, C=CZ";

        assertTrue(SecurityUtils.containsAllDN(principal1, principal2));
    }

    @Test
    void testContainsAllDNInvalidPrincipal1() {
        String invalidPrincipal1 = "InvalidDN=Value";
        String principal2 = "CN=TestRootCA";

        assertFalse(SecurityUtils.containsAllDN(invalidPrincipal1, principal2));
    }

    @Test
    void testContainsAllDNInvalidPrincipal2() {
        String principal1 = "CN=TestRootCA, O=TestExample";
        String invalidPrincipal2 = "InvalidDN=Value";

        assertFalse(SecurityUtils.containsAllDN(principal1, invalidPrincipal2));
    }

    @Test
    void testContainsAllDNBothInvalid() {
        String invalidPrincipal1 = "InvalidDN1=Value";
        String invalidPrincipal2 = "InvalidDN2=Value";

        assertFalse(SecurityUtils.containsAllDN(invalidPrincipal1, invalidPrincipal2));
    }

    @Test
    void testContainsAllDNEmptyPrincipal2() {
        String principal1 = "CN=TestRootCA, O=TestExample";
        String emptyPrincipal2 = "";

        assertTrue(SecurityUtils.containsAllDN(principal1, emptyPrincipal2));
    }

    @Test
    void testContainsAllDNEmptyPrincipal1() {
        String emptyPrincipal1 = "";
        String principal2 = "CN=TestRootCA";

        assertFalse(SecurityUtils.containsAllDN(emptyPrincipal1, principal2));
    }

    @Test
    void testExportCaDataToFileWithSpecialCharacters() throws IOException {
        String dataWithSpecialChars = "Test data with special chars: äöüß@#$%^&*()";
        String prefix = "special";
        String suffix = ".txt";

        File resultFile = SecurityUtils.exportCaDataToFile(dataWithSpecialChars, prefix, suffix);

        assertNotNull(resultFile);
        assertTrue(resultFile.exists());

        String content = Files.readString(resultFile.toPath());
        assertEquals(dataWithSpecialChars, content);
    }

    @Test
    void testExportToPemFilesWithEndEntityCert() throws IOException {
        CertAndKeyFiles result = SecurityUtils.exportToPemFiles(endAppCert);

        assertNotNull(result);
        assertTrue(new File(result.getCertPath()).exists());
        assertTrue(new File(result.getKeyPath()).exists());

        String certContent = Files.readString(new File(result.getCertPath()).toPath());
        assertTrue(certContent.contains("BEGIN CERTIFICATE"));
        assertTrue(certContent.contains("END CERTIFICATE"));
    }
}