/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class KubeClientIT extends AbstractIT {

    @Test
    void testCreateResourcesFromYaml() throws IOException {
        List<HasMetadata> resources = KubeResourceManager.get().kubeClient()
            .readResourcesFromFile(getClass().getClassLoader().getResourceAsStream("resources.yaml"));

        KubeResourceManager.get().createResourceWithWait(resources.toArray(new HasMetadata[0]));

        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(nsName4).get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().serviceAccounts()
            .inNamespace(nsName4).withName("skodjob-test-user").get());
    }
}
