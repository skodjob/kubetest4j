/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.annotations.MethodNamespace;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the lifecycle of method namespaces created per test method.
 * <p>
 * Method namespaces are created in {@code beforeEach} via {@link KubeResourceManager},
 * which tracks them on the resource stack. KRM's LIFO deletion order naturally ensures
 * resources inside namespaces are deleted before the namespaces themselves, since
 * namespaces are created first (bottom of stack) and resources on top.
 * <p>
 * This service handles:
 * <ul>
 *     <li>Scanning test classes for {@link MethodNamespace} annotations</li>
 *     <li>Generating unique, K8s-legal namespace names</li>
 *     <li>Creating namespaces via {@link KubeResourceManager}</li>
 * </ul>
 */
class MethodNamespaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodNamespaceService.class);

    /**
     * Maximum length for Kubernetes namespace names (DNS-1123 label).
     */
    private static final int MAX_NAMESPACE_LENGTH = 63;

    private final ContextStoreHelper contextStoreHelper;
    private final NamespaceService.MultiKubeContextProvider multiContextProvider;

    /**
     * Holds information about a per-method namespace.
     *
     * @param namespace      the created Namespace object
     * @param sourceIdentity the injection source identity (field name or parameter name)
     */
    record MethodNamespaceEntry(
        Namespace namespace,
        String sourceIdentity
    ) {
    }

    /**
     * Creates a new MethodNamespaceService.
     *
     * @param contextStoreHelper   provides access to extension context storage
     * @param multiContextProvider provides multi-context operations
     */
    MethodNamespaceService(ContextStoreHelper contextStoreHelper,
                            NamespaceService.MultiKubeContextProvider multiContextProvider) {
        this.contextStoreHelper = contextStoreHelper;
        this.multiContextProvider = multiContextProvider;
    }

    /**
     * Scans the test class for {@link MethodNamespace} annotations on fields,
     * creates the namespaces, and stores them for injection.
     * <p>
     * This method must be called in {@code beforeEach}, before field injection.
     * Namespaces are created via KRM and stay on its resource stack. KRM's LIFO
     * deletion handles cleanup automatically in the correct order.
     *
     * @param context the method-level extension context
     */
    void createMethodNamespaces(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        List<MethodNamespaceEntry> entries = new ArrayList<>();
        int[] counter = {0};

        // Scan fields for @MethodNamespace
        for (Field field : testClass.getDeclaredFields()) {
            MethodNamespace annotation = field.getAnnotation(MethodNamespace.class);
            if (annotation == null) {
                continue;
            }

            validateField(field);

            MethodNamespaceEntry entry = createNamespaceEntry(
                annotation, field.getName(), context, counter[0]++);
            entries.add(entry);
        }

        // Scan current test method parameters for @MethodNamespace
        context.getTestMethod().ifPresent(method ->
            scanMethodParameters(method, context, entries, counter));

        if (!entries.isEmpty()) {
            contextStoreHelper.putMethodNamespaceEntries(context, entries);
            LOGGER.info("Created {} method namespace(s) for test {}.{}",
                entries.size(),
                testClass.getSimpleName(),
                context.getDisplayName().replace("()", ""));
        }
    }

    /**
     * Resolves a method namespace for injection by source identity (field name or parameter name).
     *
     * @param context        the extension context
     * @param sourceIdentity the field name or parameter name
     * @return the Namespace object, or null if not found
     */
    Namespace resolveMethodNamespace(ExtensionContext context, String sourceIdentity) {
        List<MethodNamespaceEntry> entries = contextStoreHelper.getMethodNamespaceEntries(context);
        if (entries == null) {
            return null;
        }
        return entries.stream()
            .filter(e -> e.sourceIdentity().equals(sourceIdentity))
            .map(MethodNamespaceEntry::namespace)
            .findFirst()
            .orElse(null);
    }

    // ===============================
    // Private Helper Methods
    // ===============================

    private void scanMethodParameters(Method method, ExtensionContext context,
                                      List<MethodNamespaceEntry> entries, int[] counter) {
        for (Parameter parameter : method.getParameters()) {
            MethodNamespace annotation = parameter.getAnnotation(MethodNamespace.class);
            if (annotation == null) {
                continue;
            }

            if (!Namespace.class.isAssignableFrom(parameter.getType())) {
                throw new IllegalArgumentException(
                    "@MethodNamespace parameter '" + parameter.getName()
                        + "' must be of type Namespace, but is " + parameter.getType().getSimpleName());
            }

            MethodNamespaceEntry entry = createNamespaceEntry(
                annotation, parameter.toString(), context, counter[0]++);
            entries.add(entry);
        }
    }

    private MethodNamespaceEntry createNamespaceEntry(MethodNamespace annotation,
                                                       String sourceIdentity,
                                                       ExtensionContext context,
                                                       int index) {
        String namespaceName = generateNamespaceName(annotation.prefix(), context, index);
        String kubeContext = annotation.kubeContext();

        // Resolve the resource manager for the target context
        KubeResourceManager resourceManager = resolveResourceManager(context, kubeContext);

        // Build namespace with labels and annotations
        Map<String, String> labels = parseKeyValuePairs(annotation.labels());
        Map<String, String> annotations = parseKeyValuePairs(annotation.annotations());

        Namespace namespace = new NamespaceBuilder()
            .withNewMetadata()
            .withName(namespaceName)
            .withLabels(labels)
            .withAnnotations(annotations)
            .endMetadata()
            .build();

        // Create via KRM — namespace stays on the resource stack.
        // KRM's LIFO deletion ensures resources created inside the namespace
        // (which are on top of the stack) are deleted before the namespace itself.
        LoggerUtils.logResource("Creating method", Level.DEBUG, namespace);
        resourceManager.createResourceWithWait(namespace);

        // Retrieve the actual namespace from cluster for complete object with status
        Namespace actualNamespace = resourceManager.kubeClient().getClient()
            .namespaces()
            .withName(namespaceName)
            .get();

        if (actualNamespace == null) {
            LOGGER.warn("Could not retrieve method namespace '{}' from cluster, using built object",
                namespaceName);
            actualNamespace = namespace;
        }

        LOGGER.debug("Created method namespace '{}' for source '{}'", namespaceName, sourceIdentity);
        return new MethodNamespaceEntry(actualNamespace, sourceIdentity);
    }

    private KubeResourceManager resolveResourceManager(ExtensionContext context, String kubeContext) {
        if (kubeContext.isEmpty()) {
            KubeResourceManager rm = contextStoreHelper.getResourceManager(context);
            if (rm == null) {
                throw new IllegalStateException(
                    "@MethodNamespace requires @KubernetesTest on the test class");
            }
            return rm;
        }
        return multiContextProvider.getResourceManagerForKubeContext(context, kubeContext);
    }

    private void validateField(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException(
                "@MethodNamespace cannot be used on static field '" + field.getName()
                    + "'. Method namespaces are per-test-method and incompatible with static fields.");
        }
        if (!Namespace.class.isAssignableFrom(field.getType())) {
            throw new IllegalArgumentException(
                "@MethodNamespace field '" + field.getName()
                    + "' must be of type Namespace, but is " + field.getType().getSimpleName());
        }
        if (field.isAnnotationPresent(ClassNamespace.class)) {
            throw new IllegalArgumentException(
                "Field '" + field.getName()
                    + "' cannot have both @MethodNamespace and @ClassNamespace");
        }
    }

    /**
     * Generates a unique, K8s-legal namespace name.
     * <p>
     * Format: {@code <prefix>-<methodName>-<index>}
     * <p>
     * The index is a simple counter (0, 1, 2, ...) for each namespace within the same
     * test method. The total length is capped at 63 characters.
     *
     * @param prefix  the namespace prefix from the annotation
     * @param context the extension context
     * @param index   the namespace index within the test method
     * @return a DNS-1123 compliant namespace name
     */
    static String generateNamespaceName(String prefix, ExtensionContext context, int index) {
        String methodName = context.getTestMethod()
            .map(Method::getName)
            .orElse("unknown");

        // Sanitize prefix and method name for DNS-1123
        String sanitizedPrefix = sanitizeDnsLabel(prefix);
        String sanitizedMethod = sanitizeDnsLabel(methodName);

        String suffix = String.valueOf(index);

        // Calculate available length for method name
        // Format: <prefix>-<method>-<index>
        int availableForMethod = MAX_NAMESPACE_LENGTH - sanitizedPrefix.length() - suffix.length() - 2; // 2 dashes

        if (availableForMethod < 1) {
            // Prefix alone is too long, truncate it
            sanitizedPrefix = sanitizedPrefix.substring(0,
                Math.min(sanitizedPrefix.length(), MAX_NAMESPACE_LENGTH - suffix.length() - 2));
            availableForMethod = 1;
        }

        if (sanitizedMethod.length() > availableForMethod) {
            sanitizedMethod = sanitizedMethod.substring(0, availableForMethod);
        }

        // Remove trailing dashes from truncation
        sanitizedMethod = stripTrailingDashes(sanitizedMethod);
        sanitizedPrefix = stripTrailingDashes(sanitizedPrefix);

        return sanitizedPrefix + "-" + sanitizedMethod + "-" + suffix;
    }

    private static String sanitizeDnsLabel(String input) {
        String result = input.toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-");
        // Strip leading and trailing dashes
        if (result.startsWith("-")) {
            result = result.substring(1);
        }
        return stripTrailingDashes(result);
    }

    private static String stripTrailingDashes(String input) {
        int end = input.length();
        while (end > 0 && input.charAt(end - 1) == '-') {
            end--;
        }
        return input.substring(0, end);
    }

    private static Map<String, String> parseKeyValuePairs(String[] pairs) {
        Map<String, String> result = new HashMap<>();
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                result.put(parts[0], parts[1]);
            }
        }
        return result;
    }
}
