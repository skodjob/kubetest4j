/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.skodjob.kubetest4j.resources.KubeResourceManager;

/**
 * Class containing utilities for handling resources
 */
public class ResourceUtils {
    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    private ResourceUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Method which gets the {@link GenericKubernetesResource} based on the parameters
     * and returns parsed specific resource.
     * This is useful in case that more versions of CRDs are supported and you would like to work with the object ->
     * but it has older version of the API.
     *
     * @param namespaceName     Name of the Namespace where the resource is present.
     * @param resourceName      Name of the resource the method will try to get.
     * @param apiVersion        Version of API by which the resource should be searched by.
     * @param kind              Kind of the resource.
     * @param resourceType      Class to which the object should be cast.
     *
     * @return  specific resource parsed from the {@link GenericKubernetesResource}.
     * @param <T> generic that extends {@link HasMetadata}
     */
    public static <T extends HasMetadata> T getGenericResourceReturnSpecific(
        String namespaceName,
        String resourceName,
        String apiVersion,
        String kind,
        Class<T> resourceType
    ) {
        GenericKubernetesResource foundGenericResource = KubeResourceManager.get().kubeClient()
            .getClient()
            .genericKubernetesResources(apiVersion, kind)
            .inNamespace(namespaceName)
            .withName(resourceName)
            .get();

        JsonNode genericResourceJson = JSON_MAPPER.valueToTree(foundGenericResource);
        return JSON_MAPPER.convertValue(genericResourceJson, resourceType);
    }
}
