/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the lifecycle of class-level namespaces declared via {@link ClassNamespace}.
 * <p>
 * Class namespaces are created in {@code beforeAll} and deleted in {@code afterAll}.
 * If a namespace already exists on the cluster, it is used as-is and not deleted
 * during cleanup (namespace protection).
 * <p>
 * Namespaces are created via {@link KubeResourceManager} to benefit from auto-labeling
 * for log collection and YAML storage.
 */
class ClassNamespaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassNamespaceService.class);

    private final ContextStoreHelper contextStoreHelper;
    private final NamespaceService.MultiKubeContextProvider multiContextProvider;

    /**
     * Holds information about a class namespace.
     *
     * @param namespace      the Namespace object (from cluster or built)
     * @param fieldName      the field name for injection matching
     * @param kubeContext    the kube context name (empty for default)
     * @param created        whether this namespace was created by the test (vs already existed)
     * @param resourceManager the resource manager used to create the namespace
     */
    record ClassNamespaceEntry(
        Namespace namespace,
        String fieldName,
        String kubeContext,
        boolean created,
        KubeResourceManager resourceManager
    ) {
    }

    /**
     * Creates a new ClassNamespaceService.
     *
     * @param contextStoreHelper   provides access to extension context storage
     * @param multiContextProvider provides multi-context operations
     */
    ClassNamespaceService(ContextStoreHelper contextStoreHelper,
                           NamespaceService.MultiKubeContextProvider multiContextProvider) {
        this.contextStoreHelper = contextStoreHelper;
        this.multiContextProvider = multiContextProvider;
    }

    /**
     * Scans the test class for {@link ClassNamespace} annotations on static fields,
     * creates the namespaces (or uses existing ones), and stores them for injection.
     * <p>
     * Must be called in {@code beforeAll}.
     *
     * @param context the class-level extension context
     */
    void createClassNamespaces(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        List<ClassNamespaceEntry> entries = new ArrayList<>();

        for (Field field : testClass.getDeclaredFields()) {
            ClassNamespace annotation = field.getAnnotation(ClassNamespace.class);
            if (annotation == null) {
                continue;
            }

            validateField(field);

            ClassNamespaceEntry entry = createNamespaceEntry(annotation, field.getName(), context);
            entries.add(entry);

            // Inject the static field immediately
            try {
                field.setAccessible(true);
                field.set(null, entry.namespace());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                    "Failed to inject @ClassNamespace field '" + field.getName() + "'", e);
            }
        }

        if (!entries.isEmpty()) {
            contextStoreHelper.putClassNamespaceEntries(context, entries);
            LOGGER.info("Set up {} class namespace(s) for class {}",
                entries.size(), testClass.getSimpleName());
        }
    }

    /**
     * Cleans up class namespaces that were created by the test.
     * Namespaces that already existed before the test are not deleted (namespace protection).
     * <p>
     * Must be called in {@code afterAll}.
     *
     * @param context the class-level extension context
     */
    void cleanupClassNamespaces(ExtensionContext context) {
        List<ClassNamespaceEntry> entries = contextStoreHelper.getClassNamespaceEntries(context);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        LOGGER.debug("Cleaning up class namespaces");

        for (ClassNamespaceEntry entry : entries) {
            if (!entry.created()) {
                LOGGER.info("Skipping deletion of pre-existing namespace '{}'",
                    entry.namespace().getMetadata().getName());
                continue;
            }

            try {
                LoggerUtils.logResource("Deleting class", Level.DEBUG, entry.namespace());
                entry.resourceManager().deleteResourceWithWait(entry.namespace());
                LOGGER.debug("Deleted class namespace '{}'",
                    entry.namespace().getMetadata().getName());
            } catch (Exception e) {
                LOGGER.warn("Failed to delete class namespace '{}': {}",
                    entry.namespace().getMetadata().getName(), e.getMessage());
            }
        }
    }

    /**
     * Resolves a class namespace for injection by field name.
     *
     * @param context   the extension context
     * @param fieldName the field name
     * @return the Namespace object, or null if not found
     */
    Namespace resolveClassNamespace(ExtensionContext context, String fieldName) {
        List<ClassNamespaceEntry> entries = contextStoreHelper.getClassNamespaceEntries(context);
        if (entries == null) {
            return null;
        }
        return entries.stream()
            .filter(e -> e.fieldName().equals(fieldName))
            .map(ClassNamespaceEntry::namespace)
            .findFirst()
            .orElse(null);
    }

    // ===============================
    // Private Helper Methods
    // ===============================

    private ClassNamespaceEntry createNamespaceEntry(ClassNamespace annotation,
                                                      String fieldName,
                                                      ExtensionContext context) {
        String namespaceName = annotation.name();
        String kubeContext = annotation.kubeContext();

        KubeResourceManager resourceManager = resolveResourceManager(context, kubeContext);

        // Check if namespace already exists (namespace protection)
        Namespace existingNamespace = resourceManager.kubeClient().getClient()
            .namespaces()
            .withName(namespaceName)
            .get();

        if (existingNamespace != null) {
            LOGGER.info("Using existing namespace '{}'{}", namespaceName,
                kubeContext.isEmpty() ? "" : " in kubeContext '" + kubeContext + "'");
            return new ClassNamespaceEntry(existingNamespace, fieldName, kubeContext, false, resourceManager);
        }

        // Create the namespace
        Map<String, String> labels = parseKeyValuePairs(annotation.labels());
        Map<String, String> annotations = parseKeyValuePairs(annotation.annotations());

        Namespace namespace = new NamespaceBuilder()
            .withNewMetadata()
            .withName(namespaceName)
            .withLabels(labels)
            .withAnnotations(annotations)
            .endMetadata()
            .build();

        LoggerUtils.logResource("Creating class", Level.DEBUG, namespace);
        resourceManager.createResourceWithWait(namespace);

        // Remove from KRM stack — class namespaces are managed by this service,
        // not by KRM's automatic LIFO cleanup. We delete them explicitly in
        // cleanupClassNamespaces() which runs after all resource cleanup.
        resourceManager.removeFromStack(namespace);

        // Retrieve actual namespace from cluster for complete object with status
        Namespace actualNamespace = resourceManager.kubeClient().getClient()
            .namespaces()
            .withName(namespaceName)
            .get();

        if (actualNamespace == null) {
            LOGGER.warn("Could not retrieve namespace '{}' from cluster, using built object",
                namespaceName);
            actualNamespace = namespace;
        }

        LOGGER.debug("Created class namespace '{}'{}", namespaceName,
            kubeContext.isEmpty() ? "" : " in kubeContext '" + kubeContext + "'");
        return new ClassNamespaceEntry(actualNamespace, fieldName, kubeContext, true, resourceManager);
    }

    private KubeResourceManager resolveResourceManager(ExtensionContext context, String kubeContext) {
        if (kubeContext.isEmpty()) {
            KubeResourceManager rm = contextStoreHelper.getResourceManager(context);
            if (rm == null) {
                throw new IllegalStateException(
                    "@ClassNamespace requires @KubernetesTest on the test class");
            }
            return rm;
        }
        return multiContextProvider.getResourceManagerForKubeContext(context, kubeContext);
    }

    private void validateField(Field field) {
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException(
                "@ClassNamespace must be used on static field '" + field.getName()
                    + "'. Class namespaces are class-level. "
                    + "Use @MethodNamespace for per-test-method namespaces on instance fields.");
        }
        if (!Namespace.class.isAssignableFrom(field.getType())) {
            throw new IllegalArgumentException(
                "@ClassNamespace field '" + field.getName()
                    + "' must be of type Namespace, but is " + field.getType().getSimpleName());
        }
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
