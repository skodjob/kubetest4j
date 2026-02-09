/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration.helpers;

import io.skodjob.kubetest4j.interfaces.MustGatherSupplier;
import io.skodjob.kubetest4j.LogCollector;
import io.skodjob.kubetest4j.LogCollectorBuilder;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.test.integration.AbstractIT;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class MustGatherSupplierImpl implements MustGatherSupplier {
    @Override
    public void saveKubernetesState(ExtensionContext context) {
        LogCollector logCollector = new LogCollectorBuilder()
            .withNamespacedResources(
                "configmap",
                "secret"
            )
            .withClusterWideResources("node")
            .withKubeClient(KubeResourceManager.get().kubeClient())
            .withKubeCmdClient(KubeResourceManager.get().kubeCmdClient())
            .withRootFolderPath(AbstractIT.LOG_DIR.resolve("failedTest")
                .resolve(context.getTestMethod().get().getName()).toString())
            .build();
        logCollector.collectFromNamespace("default");
        logCollector.collectClusterWideResources();
    }
}
