/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.InjectCmdKubeClient;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectNamespace;
import io.skodjob.kubetest4j.annotations.InjectNamespaces;
import io.skodjob.kubetest4j.annotations.InjectResource;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Handles all dependency injection for Kubernetes test components.
 * This class eliminates duplication between parameter and field injection
 * by providing unified injection methods that work with either source.
 */
class DependencyInjector {

    private final ContextStoreHelper contextStoreHelper;

    /**
     * Creates a new DependencyInjector with the given kubeContext store helper.
     *
     * @param contextStoreHelper provides access to extension kubeContext storage
     */
    DependencyInjector(ContextStoreHelper contextStoreHelper) {
        this.contextStoreHelper = contextStoreHelper;
    }

    // ===============================
    // Parameter Resolution
    // ===============================

    /**
     * Checks if a parameter can be resolved by this injector.
     */
    public boolean supportsParameter(ParameterContext parameterContext) {
        return parameterContext.isAnnotated(InjectKubeClient.class) ||
            parameterContext.isAnnotated(InjectCmdKubeClient.class) ||
            parameterContext.isAnnotated(InjectResourceManager.class) ||
            parameterContext.isAnnotated(InjectResource.class) ||
            parameterContext.isAnnotated(InjectNamespaces.class) ||
            parameterContext.isAnnotated(InjectNamespace.class);
    }

    /**
     * Resolves a parameter for injection.
     */
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {

        try {
            return resolveInjection(new ParameterInjectionSource(parameterContext), extensionContext);
        } catch (RuntimeException e) {
            // Convert RuntimeException to ParameterResolutionException for parameter injection
            throw new ParameterResolutionException(e.getMessage(), e);
        }
    }

    // ===============================
    // Field Injection
    // ===============================

    /**
     * Injects all annotated fields in the test class.
     */
    public void injectTestClassFields(ExtensionContext context) {
        Object testInstance = context.getTestInstance().orElse(null);
        if (testInstance == null) {
            return;
        }

        Field[] fields = context.getRequiredTestClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                Object value = getInjectableValue(field, context);
                if (value != null) {
                    field.setAccessible(true);
                    field.set(testInstance, value);
                }
            } catch (Exception e) {
                throw new RuntimeException("Field injection failed for: " + field.getName(), e);
            }
        }
    }

    /**
     * Gets the injectable value for a field, or null if not injectable.
     */
    private Object getInjectableValue(Field field, ExtensionContext context) {
        if (hasInjectAnnotation(field)) {
            return resolveInjection(new FieldInjectionSource(field), context);
        }
        return null;
    }

    /**
     * Checks if a field has any inject annotation.
     */
    private boolean hasInjectAnnotation(Field field) {
        return field.isAnnotationPresent(InjectKubeClient.class) ||
            field.isAnnotationPresent(InjectCmdKubeClient.class) ||
            field.isAnnotationPresent(InjectResourceManager.class) ||
            field.isAnnotationPresent(InjectResource.class) ||
            field.isAnnotationPresent(InjectNamespaces.class) ||
            field.isAnnotationPresent(InjectNamespace.class);
    }

    // ===============================
    // Unified Injection Logic
    // ===============================

    /**
     * Resolves injection from any source (parameter or field).
     */
    private Object resolveInjection(InjectionSource source, ExtensionContext context) {
        if (source.hasAnnotation(InjectKubeClient.class)) {
            return injectKubeClient(source.getAnnotation(InjectKubeClient.class), context);
        } else if (source.hasAnnotation(InjectCmdKubeClient.class)) {
            return injectCmdKubeClient(source.getAnnotation(InjectCmdKubeClient.class), context);
        } else if (source.hasAnnotation(InjectResourceManager.class)) {
            return injectResourceManager(source.getAnnotation(InjectResourceManager.class), context);
        } else if (source.hasAnnotation(InjectResource.class)) {
            return injectResource(source.getAnnotation(InjectResource.class), source.getType(), context);
        } else if (source.hasAnnotation(InjectNamespaces.class)) {
            return injectNamespaces(source.getAnnotation(InjectNamespaces.class), context);
        } else if (source.hasAnnotation(InjectNamespace.class)) {
            return injectNamespace(source.getAnnotation(InjectNamespace.class), context);
        }

        throw new RuntimeException("Cannot resolve injection for: " + source.getName());
    }

    // ===============================
    // Individual Injection Methods (Unified)
    // ===============================

    /**
     * Unified KubeClient injection logic.
     */
    private KubeClient injectKubeClient(InjectKubeClient annotation, ExtensionContext context) {
        String clusterContext = annotation.kubeContext();

        KubeResourceManager resourceManager = getResourceManagerForContext(context, clusterContext);
        if (resourceManager == null) {
            throw new RuntimeException("KubeResourceManager not available for kubeContext: " +
                (clusterContext.isEmpty() ? KubeTestConstants.DEFAULT_CONTEXT_NAME : clusterContext));
        }
        return resourceManager.kubeClient();
    }

    /**
     * Unified CmdKubeClient injection logic.
     */
    private KubeCmdClient<?> injectCmdKubeClient(InjectCmdKubeClient annotation, ExtensionContext context) {
        String clusterContext = annotation.kubeContext();

        KubeResourceManager resourceManager = getResourceManagerForContext(context, clusterContext);
        if (resourceManager == null) {
            throw new RuntimeException("KubeResourceManager not available for kubeContext: " +
                (clusterContext.isEmpty() ? KubeTestConstants.DEFAULT_CONTEXT_NAME : clusterContext));
        }
        return resourceManager.kubeCmdClient();
    }

    /**
     * Unified ResourceManager injection logic.
     */
    private KubeResourceManager injectResourceManager(InjectResourceManager annotation, ExtensionContext context) {
        String clusterContext = annotation.kubeContext();

        KubeResourceManager resourceManager = getResourceManagerForContext(context, clusterContext);
        if (resourceManager == null) {
            throw new RuntimeException("KubeResourceManager not available for kubeContext: " +
                (clusterContext.isEmpty() ? KubeTestConstants.DEFAULT_CONTEXT_NAME : clusterContext));
        }
        return resourceManager;
    }

    /**
     * Unified Resource injection logic.
     */
    @SuppressWarnings("unchecked")
    private <T extends HasMetadata> T injectResource(InjectResource annotation, Class<?> targetType,
                                                     ExtensionContext context) {
        try {
            String clusterContext = annotation.kubeContext();
            KubeResourceManager resourceManager = getResourceManagerForContext(context, clusterContext);

            if (resourceManager == null) {
                throw new RuntimeException("KubeResourceManager not available for kubeContext: " +
                    (clusterContext.isEmpty() ? KubeTestConstants.DEFAULT_CONTEXT_NAME : clusterContext));
            }

            // Load resource from file
            List<HasMetadata> resources = resourceManager.readResourcesFromFile(
                Paths.get(annotation.value()));

            if (resources.isEmpty()) {
                throw new RuntimeException("No resources found in file: " + annotation.value());
            }

            // Find resource of matching type
            HasMetadata resource = resources.stream()
                .filter(r -> targetType.isAssignableFrom(r.getClass()))
                .findFirst()
                .orElse(resources.getFirst());

            // Apply resource to cluster
            if (annotation.waitForReady()) {
                resourceManager.createOrUpdateResourceWithWait(resource);
            } else {
                resourceManager.createOrUpdateResourceWithoutWait(resource);
            }

            return (T) resource;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource from: " + annotation.value(), e);
        }
    }

    /**
     * Unified Namespaces injection logic.
     */
    private Map<String, Namespace> injectNamespaces(InjectNamespaces annotation, ExtensionContext context) {
        String clusterContext = annotation.kubeContext();

        Map<String, Namespace> namespaceObjects = getNamespaceObjectsForContext(context, clusterContext);
        if (namespaceObjects == null) {
            throw new RuntimeException("Namespace objects not available for kubeContext: " +
                (clusterContext.isEmpty() ? KubeTestConstants.DEFAULT_CONTEXT_NAME : clusterContext));
        }
        return namespaceObjects;
    }

    /**
     * Unified Namespace injection logic.
     */
    private Namespace injectNamespace(InjectNamespace annotation, ExtensionContext context) {
        String namespaceName = annotation.name();
        String clusterContext = annotation.kubeContext();

        Map<String, Namespace> namespaceObjects = getNamespaceObjectsForContext(context, clusterContext);
        if (namespaceObjects == null) {
            throw new RuntimeException("Namespace objects not available for kubeContext: " +
                (clusterContext.isEmpty() ? KubeTestConstants.DEFAULT_CONTEXT_NAME : clusterContext));
        }

        Namespace namespace = namespaceObjects.get(namespaceName);
        if (namespace == null) {
            throw new RuntimeException("Namespace '" + namespaceName +
                "' not found in test namespaces for kubeContext: " +
                (clusterContext.isEmpty() ? KubeTestConstants.DEFAULT_CONTEXT_NAME : clusterContext) + ". " +
                "Make sure it's defined in @KubernetesTest annotation.");
        }
        return namespace;
    }

    // ===============================
    // Helper Methods
    // ===============================

    private KubeResourceManager getResourceManagerForContext(ExtensionContext context, String clusterContext) {
        if (clusterContext.isEmpty()) {
            return contextStoreHelper.getResourceManager(context); // Primary kubeContext from beforeAll setup
        } else {
            return contextStoreHelper.getContextManager(context, clusterContext);
        }
    }

    private Map<String, Namespace> getNamespaceObjectsForContext(ExtensionContext context, String clusterContext) {
        if (clusterContext.isEmpty()) {
            return contextStoreHelper.getNamespaceObjects(context);
        } else {
            return contextStoreHelper.getNamespaceObjectsForContext(context, clusterContext);
        }
    }

    // ===============================
    // Injection Source Abstraction
    // ===============================

    /**
     * Abstraction for getting annotations from either parameters or fields.
     */
    private interface InjectionSource {
        <T extends java.lang.annotation.Annotation> T getAnnotation(Class<T> annotationType);

        boolean hasAnnotation(Class<? extends java.lang.annotation.Annotation> annotationType);

        Class<?> getType();

        String getName();
    }

    /**
     * Parameter-based injection source.
     */
    private record ParameterInjectionSource(ParameterContext parameterContext) implements InjectionSource {

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
            return parameterContext.getParameter().getAnnotation(annotationType);
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return parameterContext.isAnnotated(annotationType);
        }

        @Override
        public Class<?> getType() {
            return parameterContext.getParameter().getType();
        }

        @Override
        public String getName() {
            return parameterContext.getParameter().toString();
        }
    }

    /**
     * Field-based injection source.
     */
    private record FieldInjectionSource(Field field) implements InjectionSource {

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
            return field.getAnnotation(annotationType);
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return field.isAnnotationPresent(annotationType);
        }

        @Override
        public Class<?> getType() {
            return field.getType();
        }

        @Override
        public String getName() {
            return field.getName();
        }
    }
}