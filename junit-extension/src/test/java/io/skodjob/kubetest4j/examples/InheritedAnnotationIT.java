/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Example test demonstrating annotation inheritance from {@link AbstractKubernetesIT}.
 * <p>
 * This class does NOT declare {@code @KubernetesTest} — it inherits it from the parent,
 * including the {@code resourceTypes = {NamespaceType.class}} registration.
 * <p>
 * This proves:
 * <ul>
 *   <li>{@code @Inherited} works — the extension is activated without re-declaring the annotation</li>
 *   <li>{@code resourceTypes} are inherited — NamespaceType readiness checks work</li>
 *   <li>Dependency injection works on child classes</li>
 *   <li>Namespace management works on child classes</li>
 * </ul>
 */
class InheritedAnnotationIT extends AbstractKubernetesIT {

    @ClassNamespace(name = "inherited-test")
    static Namespace testNs;

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testInheritedAnnotationWorks() {
        // Verify injection works via inherited annotation
        assertNotNull(client, "KubeClient should be injected via inherited @KubernetesTest");
        assertNotNull(resourceManager, "KubeResourceManager should be injected");
        assertNotNull(testNs, "ClassNamespace should be created");
        assertEquals("inherited-test", testNs.getMetadata().getName());
    }

    @Test
    void testResourceCreationWithInheritedTypes() {
        // Create a ConfigMap — this exercises the inherited resourceTypes (NamespaceType)
        // because the namespace was created with readiness checks via the inherited registration
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("inherited-config")
            .withNamespace("inherited-test")
            .endMetadata()
            .addToData("source", "inherited-annotation")
            .build();

        resourceManager.createResourceWithWait(configMap);

        ConfigMap created = client.getClient().configMaps()
            .inNamespace("inherited-test")
            .withName("inherited-config")
            .get();

        assertNotNull(created, "ConfigMap should be created in inherited namespace");
        assertEquals("inherited-annotation", created.getData().get("source"));
    }
}
