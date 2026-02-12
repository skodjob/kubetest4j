/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utils class
 */
public class TestUtils {

    private TestUtils() {
        // empty constructor
    }

    /**
     * Generates log root path
     *
     * @param folderName root
     * @param context    ext kubeContext
     * @return folder for current test logs
     */
    public static Path getLogPath(String folderName, ExtensionContext context) {
        return getLogPath(folderName, context, "primary");
    }

    /**
     * Generates log root path
     *
     * @param folderName     root
     * @param context        ext kubeContext
     * @param clusterContext cluster kubeContext
     * @return folder for current test logs
     */
    public static Path getLogPath(String folderName, ExtensionContext context, String clusterContext) {
        String testMethod = context.getDisplayName();
        String testClassName = context.getTestClass().map(Class::getName).orElse("NOCLASS");
        return getLogPath(folderName, testClassName, testMethod, clusterContext);
    }

    /**
     * Generates log root path
     *
     * @param folderName root
     * @param info       test info
     * @return folder for current test logs
     */
    public static Path getLogPath(String folderName, TestInfo info) {
        String testMethod = info.getDisplayName();
        String testClassName = info.getTestClass().map(Class::getName).orElse("NOCLASS");
        return getLogPath(folderName, testClassName, testMethod, "primary");
    }

    /**
     * Generates log root path
     *
     * @param folderName    root
     * @param testClassName class name
     * @param testMethod    method name
     * @param contextName   cluster kubeContext
     * @return folder for current test logs
     */
    public static Path getLogPath(String folderName, String testClassName, String testMethod, String contextName) {
        Path path = Paths.get(System.getProperty("user.dir"), "target", "test-logs")
            .resolve(Paths.get(folderName, testClassName));
        if (testMethod != null) {
            path = path
                .resolve(testMethod.replace("(", "").replace(")", ""))
                .resolve(contextName);
        }
        return path;
    }
}
