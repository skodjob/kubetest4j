/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Groups {@link ResourceItem}s that were created together in a single operation.
 * During cleanup, all items within a batch are deleted concurrently,
 * while batches themselves are deleted sequentially in LIFO order.
 *
 * @param items the resource items in this batch
 */
public record ResourceBatch(List<ResourceItem<?>> items) {

    /**
     * Constructs a batch from a list of resource items.
     *
     * @param items the resource items in this batch
     */
    public ResourceBatch(List<ResourceItem<?>> items) {
        this.items = new CopyOnWriteArrayList<>(items);
    }

    /**
     * Constructs a single-item batch.
     *
     * @param item the resource item
     */
    public ResourceBatch(ResourceItem<?> item) {
        this(new CopyOnWriteArrayList<>());
        this.items.add(item);
    }

    /**
     * Returns an unmodifiable view of the items in this batch.
     *
     * @return list of resource items
     */
    @Override
    public List<ResourceItem<?>> items() {
        return List.copyOf(items);
    }

    /**
     * Returns the number of items in this batch.
     *
     * @return item count
     */
    public int size() {
        return items.size();
    }

    /**
     * Returns whether this batch has no items.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Removes all items matching the given predicate.
     *
     * @param filter predicate to test each item
     * @return true if any items were removed
     */
    public boolean removeIf(Predicate<ResourceItem<?>> filter) {
        return items.removeIf(filter);
    }
}
