/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestVisualSeparator
final class LogCollectorUtilsTest {

    @Test
    void testYamlFileNameForResource() {
        String resourceName = "secret-RESOURCE";
        String expectedFileName = "secret-resource.yaml";

        assertEquals(expectedFileName, LogCollectorUtils.getYamlFileNameForResource(resourceName));
    }

    @Test
    void testLogFileNameForResource() {
        String resourceName = "pod-name-RES";
        String expectedFileName = "pod-name-res.log";

        assertEquals(expectedFileName, LogCollectorUtils.getLogFileNameForResource(resourceName));
    }

    @Test
    void testLogFileNameForPodAndContainer() {
        String podName = "my-cluster-323232";
        String containerName = "pavel";
        String expectedFileName = "logs-pod-" + podName + "-container-" + containerName + ".log";

        assertEquals(expectedFileName, LogCollectorUtils.getLogFileNameForPodContainer(podName, containerName));
    }

    @Test
    void testLogFileNameForPodDescription() {
        String podName = "my-ultimate-cluster-3000";
        String expectedFileName = "describe-pod-" + podName + ".log";

        assertEquals(expectedFileName, LogCollectorUtils.getLogFileNameForPodDescription(podName));
    }

    @Test
    void testFullDirPathWithNamespace() {
        String rootPathDir = "/tmp/logs";
        String namespaceName = "my-namespace";
        String expectedFullPath = rootPathDir + "/" + namespaceName;

        assertEquals(expectedFullPath, LogCollectorUtils.getFullDirPathWithNamespace(rootPathDir, namespaceName));
    }

    @Test
    void testFullPathDirWithNamespaceAndResourceType() {
        String rootPathDir = "/tmp/logs";
        String namespaceName = "my-namespace";
        String resourceType = "ULTIMATE";
        String expectedFullPath = rootPathDir + "/" + namespaceName + "/" + resourceType.toLowerCase(Locale.ROOT);

        assertEquals(expectedFullPath, LogCollectorUtils.getNamespaceFullDirPathForResourceType(
            LogCollectorUtils.getFullDirPathWithNamespace(rootPathDir, namespaceName), resourceType));
    }

    @Test
    void testFullPathForFolderPathAndFileName() {
        String fullNamespacePath = "/tmp/logs/my-namespace";
        String fileName = "tonda.yaml";
        String expectedFullPath = fullNamespacePath + "/" + fileName;

        assertEquals(expectedFullPath,
            LogCollectorUtils.getFullPathForFolderPathAndFileName(fullNamespacePath, fileName));
    }

    @Test
    void testGetFolderPath() {
        String rootPath = "/tmp/logs";
        String folderPath = "additional/path/to/folder";
        String expectedFolderPath = rootPath + "/" + folderPath;

        assertEquals(expectedFolderPath, LogCollectorUtils.getFolderPath(rootPath, folderPath));
    }
}
