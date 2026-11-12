/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Name checks for resources handled by {@link KubeResourceManager}.
 *
 * <p>A request for a resource without {@code metadata.name} targets the whole collection of
 * that kind (for example a DELETE removes every object of the kind in the namespace), so such
 * resources must never be deleted or tracked for cleanup.
 *
 * <p>Package-private — accessed only through {@link KubeResourceManager} services.
 */
final class ResourceNames {

    /** Characters the API server uses for the random part of a generated name. */
    static final String GENERATED_NAME_CHARS = "bcdfghjklmnpqrstvwxz2456789";
    /** Longest {@code generateName} base the API server keeps (63 - {@link #RANDOM_SUFFIX_LENGTH}). */
    static final int MAX_GENERATED_BASE_LENGTH = 58;
    /** Length of the random suffix of a generated name. */
    static final int RANDOM_SUFFIX_LENGTH = 5;

    private ResourceNames() {
    }

    /**
     * Fails when the resource has no {@code metadata.name}.
     *
     * @param resource the resource to check
     * @param action   the refused action, used in the message (for example {@code "delete"})
     * @throws IllegalArgumentException if the metadata is missing or the name is null or blank
     */
    static void requireName(HasMetadata resource, String action) {
        ObjectMeta meta = resource.getMetadata();
        if (meta == null || !hasText(meta.getName())) {
            throw new IllegalArgumentException("Refusing to %s %s: metadata.name is not set"
                .formatted(action, describe(resource)));
        }
    }

    /**
     * Generates a name from a {@code generateName} base in the same format as the Kubernetes API
     * server: the base truncated to {@value #MAX_GENERATED_BASE_LENGTH} characters followed by
     * {@value #RANDOM_SUFFIX_LENGTH} random characters from {@link #GENERATED_NAME_CHARS}.
     *
     * @param base the {@code metadata.generateName} value
     * @return the generated name
     */
    static String generateName(String base) {
        String prefix = base.substring(0, Math.min(base.length(), MAX_GENERATED_BASE_LENGTH));
        return prefix + ThreadLocalRandom.current()
            .ints(RANDOM_SUFFIX_LENGTH, 0, GENERATED_NAME_CHARS.length())
            .mapToObj(i -> String.valueOf(GENERATED_NAME_CHARS.charAt(i)))
            .collect(Collectors.joining());
    }

    /**
     * Returns a resource that has {@code metadata.name} set, so it can be created, waited for and
     * cleaned up by name. A resource with a name is returned as is. A resource with only
     * {@code metadata.generateName} is copied and the copy gets a generated name; the given object
     * is not modified, so it can be reused as a template.
     *
     * @param resource the resource to create
     * @param <T>      the resource type
     * @return the same resource, or a named copy
     * @throws IllegalArgumentException if neither {@code metadata.name} nor {@code metadata.generateName} is set
     */
    static <T extends HasMetadata> T withResolvedName(T resource) {
        ObjectMeta meta = resource.getMetadata();
        if (meta != null && hasText(meta.getName())) {
            return resource;
        }
        if (meta == null || !hasText(meta.getGenerateName())) {
            throw new IllegalArgumentException(
                "Cannot create %s: neither metadata.name nor metadata.generateName is set"
                    .formatted(describe(resource)));
        }
        T copy = Serialization.clone(resource);
        copy.getMetadata().setName(generateName(meta.getGenerateName()));
        copy.getMetadata().setGenerateName(null);
        return copy;
    }

    private static String describe(HasMetadata resource) {
        ObjectMeta meta = resource.getMetadata();
        return meta == null || meta.getNamespace() == null
            ? resource.getKind()
            : "%s in namespace '%s'".formatted(resource.getKind(), meta.getNamespace());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
