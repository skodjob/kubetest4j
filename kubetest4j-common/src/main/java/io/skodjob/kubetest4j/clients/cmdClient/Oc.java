/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients.cmdClient;

import java.util.List;
import java.util.Map;

import io.skodjob.kubetest4j.executor.Exec;

/**
 * A {@link KubeCmdClient} implementation wrapping {@code oc}.
 */
public class Oc extends BaseCmdKubeClient<Oc> {

    /**
     * The command name for oc.
     */
    private static final String OC = "oc";

    /**
     * Constructs a new Oc instance.
     */
    public Oc() {
        this(null);
    }

    /**
     * Constructs a new Oc instance with the specified configuration.
     *
     * @param config The configuration to use.
     */
    public Oc(String config) {
        super(config, 0);
    }

    private Oc(String futureNamespace, String config, int timeout) {
        super(config, timeout);
        namespace = futureNamespace;
    }

    /**
     * Gets the default OLM (Operator Lifecycle Manager) namespace.
     *
     * @return The default OLM namespace.
     */
    @Override
    public String defaultOlmNamespace() {
        return "openshift-marketplace";
    }

    /**
     * Sets the namespace for the Oc instance.
     *
     * @param namespace The namespace to set.
     * @return A new Oc instance with the specified namespace.
     */
    @Override
    public Oc inNamespace(String namespace) {
        return new Oc(namespace, config, timeout);
    }

    /**
     * Sets the timeout for later operations.
     *
     * @param timeout timeout in ms for execution of command.
     * @return This kube client.
     */
    @Override
    public Oc withTimeout(int timeout) {
        return new Oc(namespace, config, timeout);
    }

    /**
     * Sets the timeout for later operations.
     *
     * @param timeout timeout in ms for execution of command.
     * @return This kube client.
     */
    @Override
    public Oc withTimeout(long timeout) {
        return new Oc(namespace, config, (int) timeout);
    }

    /**
     * Gets the current namespace of the Oc instance.
     *
     * @return The current namespace.
     */
    @Override
    public String getCurrentNamespace() {
        return namespace;
    }

    /**
     * Creates a new namespace.
     *
     * @param name The name of the namespace to create.
     * @return The Oc instance.
     */
    @Override
    public Oc createNamespace(String name) {
        try (Context context = defaultContext()) {
            Exec.exec(timeout, cmd(), "new-project", name);
        }
        return this;
    }

    /**
     * Creates a new application.
     *
     * @param template The template file.
     * @param params   The parameters for the template.
     * @return The Oc instance.
     */
    public Oc newApp(String template, Map<String, String> params) {
        List<String> cmd = command("new-app", template);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            cmd.add("-p");
            cmd.add(entry.getKey() + "=" + entry.getValue());
        }

        Exec.exec(cmd, timeout);
        return this;
    }

    /**
     * Gets the command name for oc.
     *
     * @return The command name.
     */
    @Override
    public String cmd() {
        return OC;
    }

    /**
     * Gets the username.
     *
     * @return The username.
     */
    @Override
    public String getUsername() {
        return Exec.exec(command("whoami"), timeout).out();
    }

    /**
     * Set node unschedule
     *
     * @param nodeName name of node
     */
    @Override
    public void cordon(String nodeName) {
        Exec.exec(command("adm", "cordon", nodeName), timeout);
    }

    /**
     * Set node schedule
     *
     * @param nodeName name of node
     */
    @Override
    public void uncordon(String nodeName) {
        Exec.exec(command("adm", "uncordon", nodeName), timeout);
    }

    /**
     * Drain node
     *
     * @param nodeName         name of the node
     * @param ignoreDaemonSets ignore DaemonSet-managed pods
     * @param disableEviction  force drain to use delete, even if eviction is supported.
     *                         This will bypass checking PodDisruptionBudgets, use with caution.
     * @param timeoutInSeconds the length of time to wait before giving up, zero means infinite
     */
    @Override
    public void drain(String nodeName, boolean ignoreDaemonSets, boolean disableEviction, long timeoutInSeconds) {
        Exec.exec(command("adm", "drain", nodeName,
            "--ignore-daemonsets", String.valueOf(ignoreDaemonSets),
            "--disable-eviction", String.valueOf(disableEviction),
            "--timeout", timeoutInSeconds + "s"), timeout);
    }
}
