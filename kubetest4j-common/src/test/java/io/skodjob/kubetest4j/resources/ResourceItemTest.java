/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.interfaces.ThrowableRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestVisualSeparator
class ResourceItemTest {

    @Test
    void testResourceItemWithResourceAndRunnable() {
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test-config")
            .withNamespace("test-namespace")
            .endMetadata()
            .build();

        ThrowableRunner runner = () -> {
            // Test runnable action
        };

        ResourceItem<ConfigMap> item = new ResourceItem<>(runner, configMap);

        assertEquals(runner, item.throwableRunner());
        assertEquals(configMap, item.resource());
        assertNotNull(item.resource());
        assertEquals("test-config", item.resource().getMetadata().getName());
    }

    @Test
    void testResourceItemWithOnlyRunnable() {
        ThrowableRunner runner = () -> {
            // Test runnable action
        };

        ResourceItem<ConfigMap> item = new ResourceItem<>(runner);

        assertEquals(runner, item.throwableRunner());
        assertNull(item.resource());
    }

    @Test
    void testResourceItemEquality() {
        ThrowableRunner runner1 = () -> {
        };
        ThrowableRunner runner2 = () -> {
        };

        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test-config")
            .endMetadata()
            .build();

        ResourceItem<ConfigMap> item1 = new ResourceItem<>(runner1, configMap);
        ResourceItem<ConfigMap> item2 = new ResourceItem<>(runner1, configMap);
        ResourceItem<ConfigMap> item3 = new ResourceItem<>(runner2, configMap);

        assertEquals(item1, item2);
        assertEquals(item1.hashCode(), item2.hashCode());

        // Different runnable should not be equal
        assertNotNull(item1);
        assertNotNull(item3);
    }

    @Test
    void testResourceItemToString() {
        ThrowableRunner runner = () -> {
        };
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test-config")
            .endMetadata()
            .build();

        ResourceItem<ConfigMap> item = new ResourceItem<>(runner, configMap);
        String toString = item.toString();

        assertNotNull(toString);
        // toString should contain class name and field information
        assert (toString.contains("ResourceItem"));
    }
}