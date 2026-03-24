/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.MethodNamespace;
import io.skodjob.kubetest4j.annotations.InjectCmdKubeClient;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResource;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Handles all dependency injection for Kubernetes test components.
 * This class eliminates duplication between parameter and field injection
 * by providing unified injection methods that work with either source.
 */
class DependencyInjector {

    private final ContextStoreHelper contextStoreHelper;
    private final MethodNamespaceService methodNamespaceService;

    /**
     * Creates a new DependencyInjector with the given dependencies.
     *
     * @param contextStoreHelper      provides access to extension kubeContext storage
     * @param methodNamespaceService  provides method namespace resolution
     */
    DependencyInjector(ContextStoreHelper contextStoreHelper,
                       MethodNamespaceService methodNamespaceService) {
        this.contextStoreHelper = contextStoreHelper;
        this.methodNamespaceService = methodNamespaceService;
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
            parameterContext.isAnnotated(MethodNamespace.class);
    }

    /**
     * Resolves a parameter for injection.
     */
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {

        try {
            return resolveInjection(new ParameterInjectionSource(parameterContext), extensionContext);
        } catch (RuntimeException e) {
            throw new ParameterResolutionException(e.getMessage(), e);
        }
    }

    // ===============================
    // Field Injection
    // ===============================

    /**
     * Injects all annotated fields in the test class.
     * Static fields with @ClassNamespace are skipped here — they are injected
     * directly by ClassNamespaceService during beforeAll.
     */
    public void injectTestClassFields(ExtensionContext context) {
        Object testInstance = context.getTestInstance().orElse(null);
        if (testInstance == null) {
            return;
        }

        Field[] fields = context.getRequiredTestClass().getDeclaredFields();
        for (Field field : fields) {
            // Skip static fields — @ClassNamespace static fields are handled by ClassNamespaceService
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

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
            field.isAnnotationPresent(MethodNamespace.class);
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
        } else if (source.hasAnnotation(MethodNamespace.class)) {
            return injectMethodNamespace(source, context);
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

            List<HasMetadata> resources = loadResources(annotation.value(), resourceManager, context);

            if (resources.isEmpty()) {
                throw new RuntimeException("No resources found in file: " + annotation.value());
            }

            Class<?> expectedType = annotation.type() == Object.class ? targetType : annotation.type();
            HasMetadata resource = selectResource(resources, expectedType, annotation.name(), annotation.value());

            if (annotation.waitForReady()) {
                resourceManager.createOrUpdateResourceWithWait(resources.toArray(new HasMetadata[0]));
            } else {
                resourceManager.createOrUpdateResourceWithoutWait(resources.toArray(new HasMetadata[0]));
            }

            return (T) resource;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource from: " + annotation.value(), e);
        }
    }

    private List<HasMetadata> loadResources(String resourcePath, KubeResourceManager resourceManager,
                                            ExtensionContext context) throws IOException {
        Path filePath = Paths.get(resourcePath);
        if (Files.exists(filePath)) {
            return resourceManager.readResourcesFromFile(filePath);
        }

        ClassLoader classLoader = context.getRequiredTestClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        }

        if (inputStream == null) {
            throw new IOException("Resource not found on filesystem or classpath: " + resourcePath);
        }

        return resourceManager.readResourcesFromFile(inputStream);
    }

    private HasMetadata selectResource(List<HasMetadata> resources, Class<?> expectedType,
                                       String resourceName, String resourcePath) {
        return resources.stream()
            .filter(resource -> resourceMatchesType(resource, expectedType))
            .filter(resource -> resourceName.isBlank() || resourceName.equals(resource.getMetadata().getName()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No matching resource found in " + resourcePath
                + " for type " + expectedType.getSimpleName()
                + (resourceName.isBlank() ? "" : " and name " + resourceName)));
    }

    private boolean resourceMatchesType(HasMetadata resource, Class<?> expectedType) {
        return expectedType == Object.class || expectedType.isAssignableFrom(resource.getClass());
    }

    /**
     * Unified MethodNamespace injection logic.
     * Resolves a per-method namespace by injection source identity.
     */
    private Namespace injectMethodNamespace(InjectionSource source, ExtensionContext context) {
        Namespace namespace = methodNamespaceService.resolveMethodNamespace(context, source.getName());
        if (namespace == null) {
            throw new RuntimeException(
                "Method namespace not found for '" + source.getName() + "'. "
                    + "Ensure @KubernetesTest is on the test class and the namespace was created.");
        }
        return namespace;
    }

    // ===============================
    // Helper Methods
    // ===============================

    private KubeResourceManager getResourceManagerForContext(ExtensionContext context, String clusterContext) {
        if (clusterContext.isEmpty()) {
            return contextStoreHelper.getResourceManager(context);
        } else {
            return contextStoreHelper.getContextManager(context, clusterContext);
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
