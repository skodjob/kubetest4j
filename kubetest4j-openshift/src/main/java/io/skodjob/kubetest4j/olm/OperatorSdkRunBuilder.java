/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.olm;

/**
 * Builder class for the {@link OperatorSdkRun}
 */
public class OperatorSdkRunBuilder {

    private String namespace;
    private String timeout;
    private String installMode;
    private String indexImage;
    private String kubeconfig;
    private String bundleImage;
    private boolean skipTlsVerify;


    /**
     * Constructor for creating {@link OperatorSdkRunBuilder} with parameters from
     * current instance of {@link OperatorSdkRun}.
     *
     * @param operatorSdkRun current instance of {@link OperatorSdkRun}
     */
    public OperatorSdkRunBuilder(OperatorSdkRun operatorSdkRun) {
        this.namespace = operatorSdkRun.namespace;
        this.timeout = operatorSdkRun.timeout;
        this.installMode = operatorSdkRun.installMode;
        this.indexImage = operatorSdkRun.indexImage;
        this.kubeconfig = operatorSdkRun.kubeconfig;
        this.bundleImage = operatorSdkRun.bundleImage;
        this.skipTlsVerify = operatorSdkRun.skipTlsVerify;
    }

    /**
     * Empty constructor, we can use the "with" methods to build the LogCollector's configuration
     */
    public OperatorSdkRunBuilder() {
        // empty constructor
    }

    /**
     * Method for setting the namespace, where the bundle container will be deployed
     *
     * @param namespace where the bundle container will be deployed
     * @return {@link OperatorSdkRunBuilder} object
     */
    public OperatorSdkRunBuilder withNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    /**
     * Method for setting the timeout - string dictating the maximum time that run can run.
     * The command will return an error if the timeout is exceeded.
     *
     * @param timeout string dictating the maximum time that run
     * @return {@link OperatorSdkRunBuilder} object
     */
    public OperatorSdkRunBuilder withTimeout(String timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Method for setting the index-image for run command
     *
     * @param indexImage specifies an index image in which to inject the given bundle
     * @return {@link OperatorSdkRunBuilder} object
     */
    public OperatorSdkRunBuilder withIndexImage(String indexImage) {
        this.indexImage = indexImage;
        return this;
    }

    /**
     * Method for setting the install-mode for run command
     *
     * @param installMode (AllNamespace|OwnNamespace|SingleNamespace=)
     * @return {@link OperatorSdkRunBuilder} object
     */
    public OperatorSdkRunBuilder withInstallMode(String installMode) {
        this.installMode = installMode;
        return this;
    }

    /**
     * Method for setting the kubeconfig for run command
     *
     * @param kubeconfig the local path to a kubeconfig
     * @return {@link OperatorSdkRunBuilder} object
     */
    public OperatorSdkRunBuilder withKubeConfig(String kubeconfig) {
        this.kubeconfig = kubeconfig;
        return this;
    }

    /**
     * Method for setting the bundle-image for run command
     *
     * @param bundleImage specifies the Operator bundle image
     * @return {@link OperatorSdkRunBuilder} object
     */
    public OperatorSdkRunBuilder withBundleImage(String bundleImage) {
        this.bundleImage = bundleImage;
        return this;
    }

    /**
     * Method for setting the skip TLS certificate verification for container image registries while pulling bundles
     *
     * @param skipTlsVerify set skip tls verify
     * @return {@link OperatorSdkRunBuilder} object
     */
    public OperatorSdkRunBuilder withSkipTlsVerify(boolean skipTlsVerify) {
        this.skipTlsVerify = skipTlsVerify;
        return this;
    }

    /**
     * Get namespace
     *
     * @return namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get timeout
     *
     * @return timeout
     */
    public String getTimeout() {
        return timeout;
    }

    /**
     * Get installMode
     *
     * @return installMode
     */
    public String getInstallMode() {
        return installMode;
    }

    /**
     * Get indexImage
     *
     * @return indexImage
     */
    public String getIndexImage() {
        return indexImage;
    }

    /**
     * Get kubeconfig
     *
     * @return kubeconfig
     */
    public String getKubeconfig() {
        return kubeconfig;
    }

    /**
     * Get bundleImage
     *
     * @return bundleImage
     */
    public String getBundleImage() {
        return bundleImage;
    }

    /**
     * Get skipTlsVerify
     *
     * @return skipTlsVerify
     */
    public boolean getSkipTlsVerify() {
        return skipTlsVerify;
    }

    /**
     * Method for building the {@link OperatorSdkRun} object
     *
     * @return {@link OperatorSdkRun} configured by the specified parameters
     */
    public OperatorSdkRun build() {
        return new OperatorSdkRun(this);
    }
}
