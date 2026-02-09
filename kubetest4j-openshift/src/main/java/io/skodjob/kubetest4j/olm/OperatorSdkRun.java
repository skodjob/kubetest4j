/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.olm;

import io.skodjob.kubetest4j.executor.Exec;
import io.skodjob.kubetest4j.executor.ExecResult;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runner for operator-sdk run command
 */
public class OperatorSdkRun {

    protected String namespace;
    protected String timeout;
    protected String installMode;
    protected String indexImage;
    protected String kubeconfig;
    protected String bundleImage;
    protected boolean skipTlsVerify;

    private static final String OPTION_INDEX_IMAGE = "--index-image";
    private static final String OPTION_NAMESPACE = "--namespace";
    private static final String OPTION_KUBECONFIG = "--kubeconfig";
    private static final String OPTION_TIMEOUT = "--timeout";
    private static final String OPTION_INSTALL_MODE = "--install-mode";
    private static final String OPTION_SKIP_TLS_VERIFY = "--skip-tls-verify";
    private static final String CMD = "operator-sdk";
    private static final String RUN = "run";
    private static final String BUNDLE = "bundle";

    /**
     * Constructor of the runner
     *
     * @param operatorSdkRunBuilder operator-sdk run command configuration
     */
    public OperatorSdkRun(OperatorSdkRunBuilder operatorSdkRunBuilder) {
        if (operatorSdkRunBuilder.getNamespace() == null) {
            throw new InvalidParameterException("Namespace is a mandatory parameter for OperatorSdkRun!");
        }

        if (operatorSdkRunBuilder.getBundleImage() == null) {
            throw new InvalidParameterException("BundleImage is a mandatory parameter for OperatorSdkRun!");
        }

        if (operatorSdkRunBuilder.getInstallMode() == null) {
            throw new InvalidParameterException("InstallMode is a mandatory parameter for OperatorSdkRun!");
        }

        this.namespace = operatorSdkRunBuilder.getNamespace();
        this.bundleImage = operatorSdkRunBuilder.getBundleImage();
        this.installMode = operatorSdkRunBuilder.getInstallMode();
        this.timeout = operatorSdkRunBuilder.getTimeout();
        this.indexImage = operatorSdkRunBuilder.getIndexImage();
        this.kubeconfig = operatorSdkRunBuilder.getKubeconfig();
        this.skipTlsVerify = operatorSdkRunBuilder.getSkipTlsVerify();
    }

    /**
     * Run the built command via Executor and return it result
     *
     * @return ExecResult with data from the executor
     */
    public ExecResult run() {
        List<String> command = new ArrayList<>(Arrays.asList(CMD, RUN, BUNDLE, bundleImage));

        command.add(OPTION_NAMESPACE);
        command.add(namespace);
        command.add(OPTION_INSTALL_MODE);
        command.add(installMode);

        if (indexImage != null) {
            command.add(OPTION_INDEX_IMAGE);
            command.add(indexImage);
        }

        if (timeout != null) {
            command.add(OPTION_TIMEOUT);
            command.add(timeout);
        }

        if (kubeconfig != null) {
            command.add(OPTION_KUBECONFIG);
            command.add(kubeconfig);
        }

        if (skipTlsVerify) {
            command.add(OPTION_SKIP_TLS_VERIFY);
        }

        return Exec.exec(command);
    }
}
