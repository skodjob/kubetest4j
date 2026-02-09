/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients.cmdClient;

public class TestableCmdKubeClient extends BaseCmdKubeClient<TestableCmdKubeClient> {

    private final String commandName;

    public TestableCmdKubeClient(String commandName, String config) {
        super(config, 0);
        this.commandName = commandName;
    }

    public TestableCmdKubeClient(String commandName) {
        this(commandName, null); // Default no config
    }

    @Override
    public String defaultOlmNamespace() {
        return "";
    }

    @Override
    public KubeCmdClient<TestableCmdKubeClient> inNamespace(String namespace) {
        return null;
    }

    @Override
    public KubeCmdClient<TestableCmdKubeClient> withTimeout(int timeout) {
        return null;
    }

    @Override
    public KubeCmdClient<TestableCmdKubeClient> withTimeout(long timeout) {
        return null;
    }

    @Override
    public String getCurrentNamespace() {
        return "";
    }

    @Override
    public String cmd() {
        return commandName;
    }

    @Override
    public String getUsername() {
        return "";
    }

    @Override
    public void cordon(String nodeName) {

    }

    @Override
    public void uncordon(String nodeName) {

    }

    @Override
    public void drain(String nodeName, boolean ignoreDaemonSets, boolean disableEviction, long timeoutInSeconds) {

    }

    // Setter for namespace for testing purposes
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}