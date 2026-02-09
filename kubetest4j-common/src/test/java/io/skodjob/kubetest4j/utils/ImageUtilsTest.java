/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestVisualSeparator
class ImageUtilsTest {

    @Test
    void testChangeRegistryOrgImageAndTagFullImageAllNew() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "newregistry.com/neworg/newimage:newtag";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, "newregistry.com",
            "neworg", "newimage", "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgImageAndTagFullImagePartialNew() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "newregistry.com/myorg/myimage:newtag";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, "newregistry.com",
            null, null, "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgImageAndTagFullImageNullNewValues() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "myregistry.com/myorg/myimage:mytag";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, null,
            null, null, null);
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgImageAndTagFullImageEmptyNewValues() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "myregistry.com/myorg/myimage:mytag";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, "",
            "", "", "");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgImageAndTagNoRegistryAllNew() {
        String original = "myorg/myimage:mytag";
        String expected = "newregistry.com/neworg/newimage:newtag";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, "newregistry.com",
            "neworg", "newimage", "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgImageAndTagNoRegistryPartialNew() {
        String original = "myorg/myimage:mytag";
        String expected = "myorg/myimage:newtag";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, null,
            null, null, "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgImageAndTagNoRegistryAddNewRegistry() {
        String original = "myorg/myimage:mytag";
        String expected = "newregistry.com/myorg/myimage:mytag";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, "newregistry.com",
            null, null, null);
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgImageAndTagInvalidImageFormat() {
        String original = "myimage";
        String expected = "myimage";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, "newregistry.com",
            "neworg", "newimage", "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgImageAndTagImageWithoutTag() {
        String original = "myregistry.com/myorg/myimage";
        String expected = "myregistry.com/myorg/myimage";
        String actual = ImageUtils.changeRegistryOrgImageAndTag(original, "newregistry.com",
            "neworg", "newimage", "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistry() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "newregistry.com/myorg/myimage:mytag";
        String actual = ImageUtils.changeRegistry(original, "newregistry.com");
        assertEquals(expected, actual);

        original = "myorg/myimage:mytag";
        expected = "newregistry.com/myorg/myimage:mytag";
        actual = ImageUtils.changeRegistry(original, "newregistry.com");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryAndOrg() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "newregistry.com/neworg/myimage:mytag";
        String actual = ImageUtils.changeRegistryAndOrg(original, "newregistry.com", "neworg");
        assertEquals(expected, actual);

        original = "myorg/myimage:mytag";
        expected = "newregistry.com/neworg/myimage:mytag";
        actual = ImageUtils.changeRegistryAndOrg(original, "newregistry.com", "neworg");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryAndTag() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "newregistry.com/myorg/myimage:newtag";
        String actual = ImageUtils.changeRegistryAndTag(original, "newregistry.com", "newtag");
        assertEquals(expected, actual);

        original = "myorg/myimage:mytag";
        expected = "newregistry.com/myorg/myimage:newtag";
        actual = ImageUtils.changeRegistryAndTag(original, "newregistry.com", "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeOrg() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "myregistry.com/neworg/myimage:mytag";
        String actual = ImageUtils.changeOrg(original, "neworg");
        assertEquals(expected, actual);

        original = "myorg/myimage:mytag";
        expected = "neworg/myimage:mytag";
        actual = ImageUtils.changeOrg(original, "neworg");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeOrgAndTag() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "myregistry.com/neworg/myimage:newtag";
        String actual = ImageUtils.changeOrgAndTag(original, "neworg", "newtag");
        assertEquals(expected, actual);

        original = "myorg/myimage:mytag";
        expected = "neworg/myimage:newtag";
        actual = ImageUtils.changeOrgAndTag(original, "neworg", "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeTag() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "myregistry.com/myorg/myimage:newtag";
        String actual = ImageUtils.changeTag(original, "newtag");
        assertEquals(expected, actual);

        original = "myorg/myimage:mytag";
        expected = "myorg/myimage:newtag";
        actual = ImageUtils.changeTag(original, "newtag");
        assertEquals(expected, actual);
    }

    @Test
    void testChangeRegistryOrgAndTag() {
        String original = "myregistry.com/myorg/myimage:mytag";
        String expected = "newregistry.com/neworg/myimage:newtag";
        String actual = ImageUtils.changeRegistryOrgAndTag(original, "newregistry.com",
            "neworg", "newtag");
        assertEquals(expected, actual);

        original = "myorg/myimage:mytag";
        expected = "newregistry.com/neworg/myimage:newtag";
        actual = ImageUtils.changeRegistryOrgAndTag(original, "newregistry.com",
            "neworg", "newtag");
        assertEquals(expected, actual);
    }
}
