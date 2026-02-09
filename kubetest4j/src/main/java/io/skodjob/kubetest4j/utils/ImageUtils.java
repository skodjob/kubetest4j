/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class containing methods for handling images
 */
public final class ImageUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUtils.class);

    private ImageUtils() {
        // Private constructor to prevent instantiation
    }

    private static final Pattern IMAGE_PATTERN_FULL_PATH =
        Pattern.compile("^(?<registry>[^/]*)/(?<org>[^/]*)/(?<image>[^:]*):(?<tag>.*)$");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("^(?<org>[^/]*)/(?<image>[^:]*):(?<tag>.*)$");

    /**
     * Method that, for specified {@param image}, replaces the registry, based on the parameters.
     *
     * @param image       image that should be replaced with new values
     * @param newRegistry desired registry
     * @return updated image based on the parameters
     */
    public static String changeRegistry(String image, String newRegistry) {
        return changeRegistryOrgAndTag(image, newRegistry, null, null);
    }

    /**
     * Method that, for specified {@param image}, replaces the registry and organization, based on the parameters.
     *
     * @param image       image that should be replaced with new values
     * @param newRegistry desired registry
     * @param newOrg      desired organization
     * @return updated image based on the parameters
     */
    public static String changeRegistryAndOrg(String image, String newRegistry, String newOrg) {
        return changeRegistryOrgAndTag(image, newRegistry, newOrg, null);
    }

    /**
     * Method that, for specified {@param image}, replaces the registry and tag, based on the parameters.
     *
     * @param image       image that should be replaced with new values
     * @param newRegistry desired registry
     * @param newTag      desired tag
     * @return updated image based on the parameters
     */
    public static String changeRegistryAndTag(String image, String newRegistry, String newTag) {
        return changeRegistryOrgAndTag(image, newRegistry, null, newTag);
    }

    /**
     * Method that, for specified {@param image}, replaces the organization, based on the parameters.
     *
     * @param image  image that should be replaced with new values
     * @param newOrg desired organization
     * @return updated image based on the parameters
     */
    public static String changeOrg(String image, String newOrg) {
        return changeRegistryOrgAndTag(image, null, newOrg, null);
    }

    /**
     * Method that, for specified {@param image}, replaces the organization and tag, based on the parameters.
     *
     * @param image  image that should be replaced with new values
     * @param newOrg desired organization
     * @param newTag desired tag
     * @return updated image based on the parameters
     */
    public static String changeOrgAndTag(String image, String newOrg, String newTag) {
        return changeRegistryOrgAndTag(image, null, newOrg, newTag);
    }

    /**
     * Method that, for specified {@param image}, replaces the tag, based on the parameters.
     *
     * @param image  image that should be replaced with new values
     * @param newTag desired tag
     * @return updated image based on the parameters
     */
    public static String changeTag(String image, String newTag) {
        return changeRegistryOrgAndTag(image, null, null, newTag);
    }

    /**
     * Method that, for specified {@param image}, replaces the registry, organization, and tag, based on the parameters.
     *
     * @param image       image that should be replaced with new values
     * @param newRegistry desired registry
     * @param newOrg      desired organization
     * @param newTag      desired tag
     * @return updated image based on the parameters
     */
    public static String changeRegistryOrgAndTag(String image, String newRegistry, String newOrg, String newTag) {
        return changeRegistryOrgImageAndTag(image, newRegistry, newOrg, null, newTag);
    }

    /**
     * Method that, for specified {@param image}, replaces the registry, organization, imageName and tag,
     * based on the parameters.
     *
     * @param image the image
     * @param newRegistry the new registry
     * @param newOrg the new org
     * @param newImageName the new image name
     * @param newTag the new tag
     * @return the string
     */
    public static String changeRegistryOrgImageAndTag(String image, String newRegistry, String newOrg,
                                                      String newImageName, String newTag) {
        Matcher m = IMAGE_PATTERN_FULL_PATH.matcher(image);

        if (m.find()) {
            String registry = setImagePropertiesIfNeeded(m.group("registry"), newRegistry);
            String org = setImagePropertiesIfNeeded(m.group("org"), newOrg);
            String tag = setImagePropertiesIfNeeded(m.group("tag"), newTag);
            String imageName = setImagePropertiesIfNeeded(m.group("image"), newImageName);

            String newImage = registry + "/" + org + "/" + imageName + ":" + tag;

            LOGGER.info("Updating container image to {}", newImage);

            return newImage;
        }

        m = IMAGE_PATTERN.matcher(image);

        if (m.find()) {
            String registry = newRegistry != null ? newRegistry + "/" : "";
            String org = setImagePropertiesIfNeeded(m.group("org"), newOrg);
            String tag = setImagePropertiesIfNeeded(m.group("tag"), newTag);
            String imageName = setImagePropertiesIfNeeded(m.group("image"), newImageName);

            String newImage = registry + org + "/" + imageName + ":" + tag;

            LOGGER.info("Updating container image to {}", newImage);

            return newImage;
        }

        return image;
    }

    private static String setImagePropertiesIfNeeded(String currentValue, String newValue) {
        if (newValue != null
            && !newValue.isEmpty()
            && !currentValue.equals(newValue)
        ) {
            return newValue;
        }
        return currentValue;
    }
}
