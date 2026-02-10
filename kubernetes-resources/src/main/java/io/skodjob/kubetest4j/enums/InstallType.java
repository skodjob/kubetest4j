/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.enums;

/**
 * Enum class containing names of installation methods for deployments (and operators)
 */
public enum InstallType {
    /**
     * Yaml installation type - installation using YAML files
     */
    Yaml,
    /**
     * Helm installation type - installation using Helm charts
     */
    Helm,
    /**
     * OLM installation type - installation using container catalog on the OperatorHub
     */
    Olm,
    /**
     * Unknown installation type - returned in case that the installation type provided is not
     * one of the above
     */
    Unknown;

    /**
     * Returns enum based on String
     *
     * @param text installation method in String
     * @return one of the enums or {@link #Unknown} in case of not supported install type
     */
    public static InstallType fromString(String text) {
        for (InstallType installType : InstallType.values()) {
            if (installType.toString().equalsIgnoreCase(text)) {
                return installType;
            }
        }
        return Unknown;
    }
}
