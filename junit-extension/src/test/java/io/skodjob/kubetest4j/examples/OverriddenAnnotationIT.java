/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Example test demonstrating annotation override with inherited resourceTypes.
 * <p>
 * This class overrides {@code @KubernetesTest} to change the cleanup strategy,
 * but does NOT redeclare {@code resourceTypes}. The extension walks the class hierarchy
 * and finds {@code resourceTypes = {NamespaceType.class}} from {@link AbstractKubernetesIT}.
 * <p>
 * This proves that overriding other annotation parameters does not lose the parent's
 * resourceTypes registration.
 */
@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC, storeYaml = true)
class OverriddenAnnotationIT extends AbstractKubernetesIT {

    @ClassNamespace(name = "overridden-test")
    static Namespace testNs;

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testOverriddenAnnotationStillHasResourceTypes() {
        // Verify injection and namespace work
        assertNotNull(client, "KubeClient should be injected");
        assertNotNull(resourceManager, "KubeResourceManager should be injected");
        assertNotNull(testNs, "ClassNamespace should be created with inherited resourceTypes");

        // Create a ConfigMap to verify the namespace is usable
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("overridden-config")
            .withNamespace("overridden-test")
            .endMetadata()
            .addToData("source", "overridden-annotation")
            .build();

        resourceManager.createResourceWithWait(configMap);

        ConfigMap created = client.getClient().configMaps()
            .inNamespace("overridden-test")
            .withName("overridden-config")
            .get();

        assertNotNull(created, "ConfigMap should be created");
        assertEquals("overridden-annotation", created.getData().get("source"));
    }
}
