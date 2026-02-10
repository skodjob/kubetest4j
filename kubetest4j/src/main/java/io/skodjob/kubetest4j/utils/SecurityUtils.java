/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.skodjob.kubetest4j.security.CertAndKey;
import io.skodjob.kubetest4j.security.CertAndKeyBuilder;
import io.skodjob.kubetest4j.security.CertAndKeyFiles;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashSet;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utils for manipulating with certs
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // empty constructor
    }

    /**
     * Export in-memory cert and key into pem files
     *
     * @param certs im memory certs
     * @return exported files
     */
    public static CertAndKeyFiles exportToPemFiles(CertAndKey... certs) {
        if (certs.length == 0) {
            throw new IllegalArgumentException("List of certificates should has at least one element");
        }
        try {
            File keyFile = exportPrivateKeyToPemFile(certs[0].getPrivateKey());
            File certFile = exportCertsToPemFile(certs);
            return new CertAndKeyFiles(certFile, keyFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts private key into PKCS8File
     *
     * @param privatekey private key
     * @return exported file on disk
     * @throws NoSuchAlgorithmException bad algorithm
     * @throws InvalidKeySpecException  bad key
     * @throws IOException              io exception
     */
    public static File convertPrivateKeyToPKCS8File(PrivateKey privatekey)
        throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        byte[] encoded = privatekey.getEncoded();
        final PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(encoded);

        final ASN1Encodable asn1Encodable = privateKeyInfo.parsePrivateKey();
        final byte[] privateKeyPKCS8Formatted = asn1Encodable.toASN1Primitive().getEncoded(ASN1Encoding.DER);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyPKCS8Formatted);

        KeyFactory kf = KeyFactory.getInstance(CertAndKeyBuilder.KEY_PAIR_ALGORITHM);
        PrivateKey privateKey = kf.generatePrivate(keySpec);
        return exportPrivateKeyToPemFile(privateKey);
    }

    /**
     * Exports private key into pem file
     *
     * @param privateKey private key
     * @return exported em file on disk
     * @throws IOException io exception
     */
    private static File exportPrivateKeyToPemFile(PrivateKey privateKey) throws IOException {
        File keyFile = Files.createTempFile("key-", ".key").toFile();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(keyFile, UTF_8))) {
            pemWriter.writeObject(privateKey);
            pemWriter.flush();
        }
        return keyFile;
    }

    /**
     * Exports in memory certificates into single pem file
     *
     * @param certs certificates
     * @return file with all certificates
     * @throws IOException io exception
     */
    private static File exportCertsToPemFile(CertAndKey... certs) throws IOException {
        File certFile = Files.createTempFile("crt-", ".crt").toFile();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(certFile, UTF_8))) {
            for (CertAndKey certAndKey : certs) {
                pemWriter.writeObject(certAndKey.getCertificate());
            }
            pemWriter.flush();
        }
        return certFile;
    }

    /**
     * This method exports Certificate Authority (CA) data to a temporary file for cases in which mentioned data is
     * necessary in form of file - for use in applications like OpenSSL. The primary purpose is to save CA files,
     * such as certificates and private keys (e.g., ca.key and ca.cert), into temporary files.
     * These files are essential when you need to provide CA data to other applications, such as OpenSSL,
     * for signing user Certificate Signing Requests (CSRs).
     *
     * @param caData The Certificate Authority data to be saved to the temporary file.
     * @param prefix The prefix for the temporary file's name.
     * @param suffix The suffix for the temporary file's name.
     * @return A File object representing the temporary file containing the CA data.
     * @throws RuntimeException If an IOException occurs while creating a file or writing into the temporary file
     *                          given the critical role these operations play in ensuring proper functionality.
     */
    public static File exportCaDataToFile(String caData, String prefix, String suffix) {
        try {
            File tempFile = Files.createTempFile(prefix + "-", suffix).toFile();

            try (FileWriter fileWriter = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
                fileWriter.write(caData);
                fileWriter.flush();
            }

            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check if principal1 contains all dns of principal2 in dn
     *
     * @param principal1 principal
     * @param principal2 principal
     * @return true of principal 1 contains all dn of principal 2
     */
    public static boolean containsAllDN(String principal1, String principal2) {
        try {
            return new HashSet<>(new LdapName(principal1).getRdns()).containsAll(new LdapName(principal2).getRdns());
        } catch (InvalidNameException e) {
            e.printStackTrace();
        }
        return false;
    }
}
