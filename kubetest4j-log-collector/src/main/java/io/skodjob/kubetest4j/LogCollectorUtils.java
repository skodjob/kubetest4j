/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import java.util.Locale;

/**
 * Helper class containing methods used in {@link LogCollector}
 */
public class LogCollectorUtils {
    // YAML type of file
    private static final String YAML_TYPE = "yaml";
    // log type of file
    private static final String LOG_TYPE = "log";

    private LogCollectorUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Method returning name of the YAML file for particular resource
     *
     * @param resourceName name of the resource
     * @return name of the YAML file for particular resource
     */
    public static String getYamlFileNameForResource(String resourceName) {
        return getFileNameForResourceAndType(resourceName, YAML_TYPE);
    }

    /**
     * Method returning name of the log file for particular resource
     *
     * @param resourceName name of the resource
     * @return name of the log file for particular resource
     */
    public static String getLogFileNameForResource(String resourceName) {
        return getFileNameForResourceAndType(resourceName, LOG_TYPE);
    }

    /**
     * Method returning name of the log file for Pod and its container
     *
     * @param podName       name of the Pod
     * @param containerName name of the Container
     * @return name of the log file for Pod and its container
     */
    public static String getLogFileNameForPodContainer(String podName, String containerName) {
        return getFileNameForResourceAndType(
            String.join("-", CollectorConstants.LOGS, CollectorConstants.POD,
                podName, CollectorConstants.CONTAINER, containerName),
            LOG_TYPE
        );
    }

    /**
     * Method returning name of the log file for previous Pod and its container
     *
     * @param podName       name of the Pod
     * @param containerName name of the Container
     * @return name of the log file for previous Pod and its container
     */
    public static String getLogFileNameForPreviousPodContainer(String podName, String containerName) {
        return getFileNameForResourceAndType(
                String.join("-", CollectorConstants.LOGS, CollectorConstants.POD,
                        podName, CollectorConstants.CONTAINER, containerName, CollectorConstants.PREVIOUS),
                LOG_TYPE
        );
    }

    /**
     * Method returning name of the log file for Pod (and its description)
     *
     * @param podName name of the Pod
     * @return name of the log file for Pod (and its description)
     */
    public static String getLogFileNameForPodDescription(String podName) {
        return getFileNameForResourceAndType(
            String.join("-", CollectorConstants.DESCRIBE, CollectorConstants.POD, podName),
            LOG_TYPE
        );
    }

    /**
     * Method returning name of the file with correct file type specified under {@param fileType}
     *
     * @param resourceName name of the resource
     * @param fileType     type of file (YAML, log)
     * @return name of the file with a specified type and for particular resource
     */
    private static String getFileNameForResourceAndType(String resourceName, String fileType) {
        return String.join(".", resourceName.toLowerCase(Locale.ROOT), fileType);
    }

    /**
     * Method returning full path from {@param rootFolderPath} to the {@param namespaceName} dir
     *
     * @param folderPath    root folder path where the sub-dirs will be created into
     * @param namespaceName name of the Namespace for which the folder will be created
     * @return full path from {@param rootFolderPath} to the {@param namespaceName} dir
     */
    public static String getFullDirPathWithNamespace(String folderPath, String namespaceName) {
        return String.join("/", folderPath, namespaceName);
    }

    /**
     * Method returning full path from {@param namespaceFolderPath} to the {@param resourceType} dir.
     * Example: /tmp/logs/my-namespace/secret
     *
     * @param namespaceFolderPath Namespace folder path where the sub-dirs will be created into
     * @param resourceType        type of resource for which the folder will be created
     * @return full path from {@param namespaceFolderPath} to the {@param resourceType} dir
     */
    public static String getNamespaceFullDirPathForResourceType(
        String namespaceFolderPath,
        String resourceType
    ) {
        return String.join(
            "/",
            namespaceFolderPath,
            resourceType.toLowerCase(Locale.ROOT)
        );
    }

    /**
     * Method returning full path to the file in the logs directory.
     * Example: /tmp/logs/my-namespace/secret/my-secret.yaml
     *
     * @param folderPath full path where the file should be created
     * @param fileName   name of the file
     * @return full path to the file in the logs directory
     */
    public static String getFullPathForFolderPathAndFileName(String folderPath, String fileName) {
        return String.join("/", folderPath, fileName);
    }

    /**
     * Method returning full path from specified {@param rootPath} into the {@param folderPath}.
     * This is used in cases that we have root path specified in LogCollector, but for the new log collection, we
     * want to collect everything in different sub-dirs (and the Namespace sub-dirs are not enough).
     *
     * @param rootPath   root path specified in LogCollector during the instance initialization
     * @param folderPath additional folder path
     * @return full path from  {@param rootPath} into the {@param folderPath}
     */
    public static String getFolderPath(String rootPath, String folderPath) {
        if (folderPath == null) {
            return rootPath;
        }

        return String.join("/", rootPath, folderPath);
    }
}
