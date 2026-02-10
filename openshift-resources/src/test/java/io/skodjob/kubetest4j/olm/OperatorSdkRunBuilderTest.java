/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.olm;

import org.junit.jupiter.api.Test;

import java.security.InvalidParameterException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OperatorSdkRunBuilderTest {

    @Test
    void testBuilder() {
        OperatorSdkRun operatorSdkRun = new OperatorSdkRunBuilder()
            .withTimeout("2m")
            .withNamespace("namespace-1")
            .withBundleImage("my-bundle-image")
            .withIndexImage("my-index-image")
            .withKubeConfig("path-to-kubeconfig")
            .withInstallMode("automatic")
            .withSkipTlsVerify(true)
            .build();

        assertNotNull(operatorSdkRun);

        assertEquals("my-bundle-image", operatorSdkRun.bundleImage);
        assertEquals("2m", operatorSdkRun.timeout);
        assertEquals("my-index-image", operatorSdkRun.indexImage);
        assertEquals("namespace-1", operatorSdkRun.namespace);
        assertEquals("automatic", operatorSdkRun.installMode);
        assertEquals("path-to-kubeconfig", operatorSdkRun.kubeconfig);
        assertTrue(operatorSdkRun.skipTlsVerify);
    }

    @Test
    void testMissingNamespace() {
        OperatorSdkRunBuilder operatorSdkRunBuilder = new OperatorSdkRunBuilder()
            .withTimeout("2m")
            .withBundleImage("my-bundle-image")
            .withIndexImage("my-index-image")
            .withKubeConfig("path-to-kubeconfig")
            .withInstallMode("automatic");

        InvalidParameterException thrown = assertThrows(InvalidParameterException.class, operatorSdkRunBuilder::build);

        assertEquals("Namespace is a mandatory parameter for OperatorSdkRun!", thrown.getMessage());
    }

    @Test
    void testMissingBundleImage() {
        OperatorSdkRunBuilder operatorSdkRunBuilder = new OperatorSdkRunBuilder()
            .withTimeout("2m")
            .withNamespace("namespace-1")
            .withIndexImage("my-index-image")
            .withKubeConfig("path-to-kubeconfig")
            .withInstallMode("automatic");

        InvalidParameterException thrown = assertThrows(InvalidParameterException.class, operatorSdkRunBuilder::build);

        assertEquals("BundleImage is a mandatory parameter for OperatorSdkRun!", thrown.getMessage());
    }

    @Test
    void testWrongInstallMode() {
        OperatorSdkRunBuilder operatorSdkRunBuilder = new OperatorSdkRunBuilder()
            .withTimeout("2m")
            .withNamespace("namespace-1")
            .withIndexImage("my-index-image")
            .withBundleImage("my-bundle-image")
            .withKubeConfig("path-to-kubeconfig");

        InvalidParameterException thrown = assertThrows(InvalidParameterException.class, operatorSdkRunBuilder::build);

        assertEquals("InstallMode is a mandatory parameter for OperatorSdkRun!", thrown.getMessage());
    }
}
