/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.interfaces.ThrowableRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestVisualSeparator
class ResourceBatchTest {

    @Test
    void testSingleItemBatch() {
        ThrowableRunner runner = () -> { };
        ConfigMap cm = new ConfigMapBuilder()
            .withNewMetadata().withName("test-cm").endMetadata().build();
        ResourceItem<ConfigMap> item = new ResourceItem<>(runner, cm);

        ResourceBatch batch = new ResourceBatch(item);

        assertEquals(1, batch.size());
        assertFalse(batch.isEmpty());
        assertEquals(1, batch.items().size());
        assertEquals(cm, batch.items().get(0).resource());
    }

    @Test
    void testMultiItemBatch() {
        ThrowableRunner runner = () -> { };
        ConfigMap cm = new ConfigMapBuilder()
            .withNewMetadata().withName("cm-1").endMetadata().build();
        Namespace ns = new NamespaceBuilder()
            .withNewMetadata().withName("ns-1").endMetadata().build();

        List<ResourceItem<?>> items = new ArrayList<>();
        items.add(new ResourceItem<>(runner, cm));
        items.add(new ResourceItem<>(runner, ns));

        ResourceBatch batch = new ResourceBatch(items);

        assertEquals(2, batch.size());
        assertFalse(batch.isEmpty());
    }

    @Test
    void testEmptyBatch() {
        ResourceBatch batch = new ResourceBatch(new ArrayList<>());
        assertTrue(batch.isEmpty());
        assertEquals(0, batch.size());
    }

    @Test
    void testRemoveIf() {
        ThrowableRunner runner = () -> { };
        ConfigMap cm1 = new ConfigMapBuilder()
            .withNewMetadata().withName("cm-1").endMetadata().build();
        ConfigMap cm2 = new ConfigMapBuilder()
            .withNewMetadata().withName("cm-2").endMetadata().build();

        List<ResourceItem<?>> items = new ArrayList<>();
        items.add(new ResourceItem<>(runner, cm1));
        items.add(new ResourceItem<>(runner, cm2));

        ResourceBatch batch = new ResourceBatch(items);
        assertEquals(2, batch.size());

        boolean removed = batch.removeIf(item ->
            item.resource() != null
                && item.resource().getMetadata().getName().equals("cm-1"));

        assertTrue(removed);
        assertEquals(1, batch.size());
        assertEquals("cm-2", batch.items().get(0).resource().getMetadata().getName());
    }

    @Test
    void testRemoveIfNoMatch() {
        ThrowableRunner runner = () -> { };
        ConfigMap cm = new ConfigMapBuilder()
            .withNewMetadata().withName("cm-1").endMetadata().build();

        ResourceBatch batch = new ResourceBatch(new ResourceItem<>(runner, cm));

        boolean removed = batch.removeIf(item ->
            item.resource() != null
                && item.resource().getMetadata().getName().equals("nonexistent"));

        assertFalse(removed);
        assertEquals(1, batch.size());
    }

    @Test
    void testRemoveIfMakesEmpty() {
        ThrowableRunner runner = () -> { };
        ConfigMap cm = new ConfigMapBuilder()
            .withNewMetadata().withName("cm-1").endMetadata().build();

        ResourceBatch batch = new ResourceBatch(new ResourceItem<>(runner, cm));
        batch.removeIf(item -> true);

        assertTrue(batch.isEmpty());
        assertEquals(0, batch.size());
    }

    @Test
    void testItemsReturnsUnmodifiableView() {
        ThrowableRunner runner = () -> { };
        ConfigMap cm = new ConfigMapBuilder()
            .withNewMetadata().withName("cm-1").endMetadata().build();

        ResourceBatch batch = new ResourceBatch(new ResourceItem<>(runner, cm));
        List<ResourceItem<?>> items = batch.items();

        try {
            items.add(new ResourceItem<>(runner));
            // If we get here, the list is not truly unmodifiable — fail
            assertFalse(true, "items() should return an unmodifiable list");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }
}
