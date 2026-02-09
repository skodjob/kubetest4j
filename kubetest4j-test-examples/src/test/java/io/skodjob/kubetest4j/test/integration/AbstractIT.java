/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;

import io.skodjob.kubetest4j.utils.LoggerUtils;
import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.resources.DeploymentType;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.resources.NamespaceType;
import io.skodjob.kubetest4j.resources.ServiceAccountType;
import io.skodjob.kubetest4j.utils.KubeUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

@ResourceManager
@TestVisualSeparator(separator = "=")
public abstract class AbstractIT {
    static AtomicBoolean isCreateHandlerCalled = new AtomicBoolean(false);
    static AtomicBoolean isDeleteHandlerCalled = new AtomicBoolean(false);
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    public static final Path LOG_DIR = Paths.get(System.getProperty("user.dir"), "target", "logs")
        .resolve("test-run-" + DATE_FORMAT.format(LocalDateTime.now()));

    static {
        // Register resources which KRM uses for handling instead of native status check
        KubeResourceManager.get().setResourceTypes(
            new NamespaceType(),
            new ServiceAccountType(),
            new DeploymentType()
        );

        // Allow storing yaml files
        KubeResourceManager.get().setStoreYamlPath(LOG_DIR.toString());

        // Register callback which are called with every create resource method for every resource
        KubeResourceManager.get().addCreateCallback(r -> {
            isCreateHandlerCalled.set(true);
            if (r.getKind().equals("Namespace")) {
                KubeUtils.labelNamespace(r.getMetadata().getName(), "test-label", "true");
            }
        });

        // Register callback which are called with every delete resource method for every resource
        KubeResourceManager.get().addDeleteCallback(r -> {
            isDeleteHandlerCalled.set(true);
            if (r.getKind().equals("Namespace")) {
                LoggerUtils.logResource("Deleted", r);
            }
        });
    }

    protected String nsName1 = "test";
    protected String nsName2 = "test2";
    protected String nsName3 = "test3";
    protected String nsName4 = "test4";
}
