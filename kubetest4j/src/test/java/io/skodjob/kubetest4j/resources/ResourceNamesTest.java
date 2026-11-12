/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceNamesTest {

    private static final String SUFFIX = "[bcdfghjklmnpqrstvwxz2456789]{5}";

    @Test
    void testRequireNameMessageForNamespacedResource() {
        ConfigMap cm = new ConfigMapBuilder().withNewMetadata().withGenerateName("gen-")
            .withNamespace("default").endMetadata().build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ResourceNames.requireName(cm, "delete"));
        assertEquals("Refusing to delete ConfigMap in namespace 'default': metadata.name is not set",
            e.getMessage());
    }

    @Test
    void testRequireNameMessageForClusterScopedResourceAndNullMetadata() {
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("").endMetadata().build();
        assertEquals("Refusing to track Namespace: metadata.name is not set",
            assertThrows(IllegalArgumentException.class,
                () -> ResourceNames.requireName(ns, "track")).getMessage());
        ConfigMap noMeta = new ConfigMap();
        assertEquals("Refusing to delete ConfigMap: metadata.name is not set",
            assertThrows(IllegalArgumentException.class,
                () -> ResourceNames.requireName(noMeta, "delete")).getMessage());
    }

    @Test
    void testRequireNameAcceptsNamedResource() {
        assertDoesNotThrow(() -> ResourceNames.requireName(
            new ConfigMapBuilder().withNewMetadata().withName("x").endMetadata().build(), "delete"));
    }

    @Test
    void testGenerateNameAppendsFiveCharsFromApiServerAlphabet() {
        assertTrue(ResourceNames.generateName("gen-").matches("gen-" + SUFFIX));
    }

    @Test
    void testGenerateNameTruncatesBaseTo58Chars() {
        String name = ResourceNames.generateName("a".repeat(70));
        assertEquals(63, name.length());
        assertTrue(name.startsWith("a".repeat(58)));
    }

    @Test
    void testResolvedNameKeepsNamedResourceInstance() {
        ConfigMap named = new ConfigMapBuilder().withNewMetadata().withName("x").withGenerateName("gen-")
            .endMetadata().build();
        assertSame(named, ResourceNames.withResolvedName(named));
    }

    @Test
    void testResolvedNameCopiesAndLeavesCallerObjectUnchanged() {
        ConfigMap template = new ConfigMapBuilder().withNewMetadata().withName("").withGenerateName("gen-")
            .withNamespace("default").endMetadata().build();
        ConfigMap resolved = ResourceNames.withResolvedName(template);
        assertNotSame(template, resolved);
        assertTrue(resolved.getMetadata().getName().matches("gen-" + SUFFIX));
        assertNull(resolved.getMetadata().getGenerateName());
        assertEquals("", template.getMetadata().getName());
        assertEquals("gen-", template.getMetadata().getGenerateName());
    }

    @Test
    void testResolvedNameRejectsResourceWithoutNameOrGenerateName() {
        ConfigMap cm = new ConfigMapBuilder().withNewMetadata().withNamespace("default").endMetadata().build();
        assertEquals("Cannot create ConfigMap in namespace 'default': "
                + "neither metadata.name nor metadata.generateName is set",
            assertThrows(IllegalArgumentException.class, () -> ResourceNames.withResolvedName(cm)).getMessage());
        assertEquals("Cannot create ConfigMap: neither metadata.name nor metadata.generateName is set",
            assertThrows(IllegalArgumentException.class,
                () -> ResourceNames.withResolvedName(new ConfigMap())).getMessage());
    }
}
