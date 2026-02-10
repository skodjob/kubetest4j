/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.helper.NamespaceType;
import io.skodjob.kubetest4j.helper.TestLoggerAppender;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.resources.ResourceItem;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
@ResourceManager
@TestVisualSeparator
class KubeResourceManagerTest {
    private KubernetesClient kubernetesClient;
    private KubernetesMockServer server;

    @BeforeEach
    void setupClient() {
        KubeResourceManager.get().kubeClient().testReconnect(kubernetesClient.getConfiguration());
        KubeResourceManager.get().setResourceTypes(new NamespaceType());
        KubeResourceManager.get().addCreateCallback(r ->
            LoggerUtils.logResource("Create", r)
        );
        KubeResourceManager.get().addDeleteCallback(r ->
            LoggerUtils.logResource("Delete", r)
        );
    }

    @Test
    void testCreateDeleteNamespace() {
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder().withNewMetadata().withName("test").endMetadata().build());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test").get());
    }

    @Test
    void testDeleteAllResources() {
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder().withNewMetadata().withName("test2").endMetadata().build());
        assertNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test").get());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test2").get());
        KubeResourceManager.get().deleteResources();
        assertNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test2").get());
    }

    @Test
    void testUpdateResource() {
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("test3").endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test3").get());
        KubeResourceManager.get().updateResource(ns.edit()
            .editMetadata().addToLabels(Collections.singletonMap("test-label", "true")).endMetadata().build());
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test3").get()
            .getMetadata().getLabels().get("test-label"));
    }

    @Test
    void testCreateOrUpdateResource() {
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("test4").endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test4").get());
        KubeResourceManager.get().createOrUpdateResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test4").get());
        KubeResourceManager.get().createOrUpdateResourceWithoutWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test4").get());
    }

    @Test
    void testReplaceResource() {
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("test5").endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test5").get());

        KubeResourceManager.get().replaceResource(ns,
            resource -> resource.getMetadata().setLabels(Map.of("my-label", "here")));
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("test5").get()
            .getMetadata().getLabels().get("my-label"));
    }

    @Test
    void testReplaceResourceWithRetries() {
        String namespaceName = "test6";

        // Check if replace will be retried when there is conflict once
        server
            .expect()
            .put()
            .withPath("/api/v1/namespaces/" + namespaceName)
            .andReturn(409, "{\"message\":\"Conflict\"}")
            .once();

        Namespace ns = new NamespaceBuilder().withNewMetadata().withName(namespaceName).endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(namespaceName).get());

        KubeResourceManager.get().replaceResourceWithRetries(ns,
            resource -> resource.getMetadata().setLabels(Map.of("my-label", "here")));
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(namespaceName).get()
            .getMetadata().getLabels().get("my-label"));

        // Check retries will fail when it reaches the max retries (default 3)
        server
            .expect()
            .put()
            .withPath("/api/v1/namespaces/" + namespaceName)
            .andReturn(409, "{\"message\":\"Conflict\"}")
            .times(3);

        assertThrows(RuntimeException.class, () -> KubeResourceManager.get().replaceResourceWithRetries(ns,
            resource -> resource.getMetadata().setLabels(Map.of("my-label2", "not-here"))));

        // Check retries will fail if the exception is not Conflict code
        server
            .expect()
            .put()
            .withPath("/api/v1/namespaces/" + namespaceName)
            .andReturn(404, "{\"message\":\"Not-Found\"}")
            .once();

        assertThrows(RuntimeException.class, () -> KubeResourceManager.get().replaceResourceWithRetries(ns,
            resource -> resource.getMetadata().setLabels(Map.of("my-label2", "not-here"))));
    }

    @Test
    void testReplaceResourceWithRetriesSpecifiedByUser() {
        String namespaceName = "test7";
        int maxRetries = 6;

        // Check if replace will be retried when there is conflict once
        server
            .expect()
            .put()
            .withPath("/api/v1/namespaces/" + namespaceName)
            .andReturn(409, "{\"message\":\"Conflict\"}")
            .once();

        Namespace ns = new NamespaceBuilder().withNewMetadata().withName(namespaceName).endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(namespaceName).get());

        KubeResourceManager.get().replaceResourceWithRetries(ns,
            resource -> resource.getMetadata().setLabels(Map.of("my-label", "here")), maxRetries);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(namespaceName).get()
            .getMetadata().getLabels().get("my-label"));

        // Check retries will fail when it reaches the max retries (default 3)
        server
            .expect()
            .put()
            .withPath("/api/v1/namespaces/" + namespaceName)
            .andReturn(409, "{\"message\":\"Conflict\"}")
            .times(5);

        KubeResourceManager.get().replaceResourceWithRetries(ns,
            resource -> resource.getMetadata().setLabels(Map.of("my-label2", "not-here")), maxRetries);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName(namespaceName).get()
            .getMetadata().getLabels().get("my-label2"));

        // Check retries will fail if the exception is not Conflict code
        server
            .expect()
            .put()
            .withPath("/api/v1/namespaces/" + namespaceName)
            .andReturn(404, "{\"message\":\"Not-Found\"}")
            .once();

        assertThrows(RuntimeException.class, () -> KubeResourceManager.get().replaceResourceWithRetries(ns,
            resource -> resource.getMetadata().setLabels(Map.of("my-label2", "not-here")), maxRetries));
    }

    @Test
    void testLoggingManagedResources() {
        // create resources
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder().withNewMetadata().withName("test-ns").endMetadata().build());
        KubeResourceManager.get().createResourceWithWait(
            new ServiceAccountBuilder().withNewMetadata().withName("test-sa").endMetadata().build());

        // setup mock logger appender
        TestLoggerAppender testAppender = new TestLoggerAppender("TestAppender");

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.addAppender(testAppender, null, null);
        Configurator.setLevel(loggerConfig.getName(), Level.DEBUG);
        testAppender.start();

        // print resources
        KubeResourceManager.get().printCurrentResources(org.slf4j.event.Level.INFO);
        System.out.println(testAppender.getLogEvents());
        List<LogEvent> events = testAppender.getLogEvents();

        assertEquals(3, events.size());
        assertEquals("Managed resource: Namespace/test-ns", events.get(1).getMessage().getFormattedMessage());
        assertEquals("Managed resource: ServiceAccount/test-sa", events.get(2).getMessage().getFormattedMessage());
        assertEquals(Level.INFO, events.get(0).getLevel());

        testAppender.clean();

        // print all resources on debug output
        KubeResourceManager.get().printAllResources(org.slf4j.event.Level.DEBUG);
        events = testAppender.getLogEvents();

        assertEquals(5, events.size());
        assertEquals("Managed resource: Namespace/test-ns", events.get(3).getMessage().getFormattedMessage());
        assertEquals("Managed resource: ServiceAccount/test-sa", events.get(4).getMessage().getFormattedMessage());
        assertEquals(Level.DEBUG, events.get(0).getLevel());
    }

    @Test
    void testUseContext() throws Exception {
        // create resources
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("test-ns-2").endMetadata().build();
        ServiceAccount sa = new ServiceAccountBuilder().withNewMetadata().withName("test-sa-2")
            .withNamespace("test-ns-2").endMetadata().build();

        KubeResourceManager.get().createResourceWithWait(ns);
        KubeResourceManager.get().createResourceWithWait(sa);

        try (var ignored = KubeResourceManager.get().useContext(KubeTestConstants.DEFAULT_CONTEXT_NAME)) {
            assertTrue(KubeResourceManager.get().kubeClient().namespaceExists("test-ns-2"));
            KubeResourceManager.get().deleteResourceWithWait(sa);
        }

        assertNull(KubeResourceManager.get().kubeClient().getClient()
            .serviceAccounts().inNamespace("test-ns-2").withName("test-sa-2").get());
    }

    @Test
    void testUseContextThrowsExceptionWhenContextMissing() {
        String nonExistingContext = "non-existing-context";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> KubeResourceManager.get().useContext(nonExistingContext));

        assertTrue(ex.getMessage().contains("Unknown context '" + nonExistingContext + "'"),
            "Exception message should mention the unknown context");
    }

    @Test
    void testListPrefixedDeployments() {
        KubeResourceManager.get().createResourceWithoutWait(
            new DeploymentBuilder().withNewMetadata()
                .withName("prefixdeployment").withNamespace("test").endMetadata().build()
        );

        assertEquals("prefixdeployment", KubeResourceManager.get().kubeClient()
            .getDeploymentNameByPrefix("test", "pre"));
    }

    @Test
    void readFilesFromYaml() throws IOException {
        List<HasMetadata> res = KubeResourceManager.get()
            .readResourcesFromFile(Path.of(getClass().getClassLoader().getResource("resources.yaml").getPath()));
        assertEquals(2, res.size());
    }

    @Test
    void testStoreYamlPathGetterSetter() {
        String originalPath = KubeResourceManager.get().getStoreYamlPath();
        String testPath = "/tmp/test-path";

        KubeResourceManager.get().setStoreYamlPath(testPath);
        assertEquals(testPath, KubeResourceManager.get().getStoreYamlPath());

        // Restore original path
        KubeResourceManager.get().setStoreYamlPath(originalPath);
    }

    @Test
    void testKubeCmdClient() {
        assertNotNull(KubeResourceManager.get().kubeCmdClient());
    }

    @Test
    void testGetTestContext() {
        // This tests the getter - the context may be null initially
        KubeResourceManager.get().getTestContext();
    }

    @Test
    void testCleanContextMethods() {
        // Test methods that clean various contexts
        KubeResourceManager.get().cleanTestContext();
        KubeResourceManager.get().cleanClusterContext();
    }

    @Test
    void testReadResourcesFromInputStream() throws IOException {
        String yamlContent = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: test-configmap
            data:
              key: value
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());
        List<HasMetadata> resources = KubeResourceManager.get().readResourcesFromFile(inputStream);

        assertEquals(1, resources.size());
        assertEquals("ConfigMap", resources.get(0).getKind());
        assertEquals("test-configmap", resources.get(0).getMetadata().getName());
    }

    @Test
    void testAsyncMethods() {
        // Test the async methods with simple calls to ensure they're covered
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("async-test").endMetadata().build();

        KubeResourceManager.get().createResourceAsyncWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("async-test").get());

        KubeResourceManager.get().createOrUpdateResourceAsyncWait(ns);
        assertNotNull(KubeResourceManager.get().kubeClient().getClient().namespaces().withName("async-test").get());

        KubeResourceManager.get().deleteResourceAsyncWait(ns);
    }

    @Test
    void testWriteResourceAsYaml() throws Exception {
        // Create a temporary directory for the test
        String tempDir = System.getProperty("java.io.tmpdir");
        String testPath = tempDir + "/test-yaml-output";

        KubeResourceManager.get().setStoreYamlPath(testPath);

        // Get the current cluster context using reflection
        Field contextField = KubeResourceManager.class.getDeclaredField("CURRENT_CLUSTER_CONTEXT");
        contextField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<String> clusterContext = (ThreadLocal<String>) contextField.get(null);
        String currentContext = clusterContext.get();

        // Create a test resource
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test-configmap")
            .withNamespace("test-namespace")
            .endMetadata()
            .withData(Map.of("key", "value"))
            .build();

        // Create the resource which should trigger writeResourceAsYaml
        KubeResourceManager.get().createResourceWithWait(configMap);

        // Verify the YAML file was created  
        Path expectedFile = Paths.get(testPath)
            .resolve("test-files")
            .resolve(currentContext)
            .resolve(getClass().getName())
            .resolve("testWriteResourceAsYaml")
            .resolve("ConfigMap-test-namespace-test-configmap.yaml");

        assertTrue(Files.exists(expectedFile), "YAML file should be created at: " + expectedFile);

        // Verify the content
        String yamlContent = Files.readString(expectedFile);
        assertTrue(yamlContent.contains("test-configmap"), "YAML should contain resource name");
        assertTrue(yamlContent.contains("test-namespace"), "YAML should contain namespace");
        assertTrue(yamlContent.contains("value"), "YAML should contain data");

        // Clean up
        Files.deleteIfExists(expectedFile);
    }

    @Test
    void testPushToStack() throws Exception {
        // Create a test resource item
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("stack-test").endMetadata().build();
        ResourceItem<Namespace> resourceItem = new ResourceItem<>(() -> {
            // Mock delete action
        }, ns);

        // Get initial stack size
        Field storedResourcesField = KubeResourceManager.class.getDeclaredField("STORED_RESOURCES");
        storedResourcesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Stack<ResourceItem<?>>>> storedResources =
            (Map<String, Map<String, Stack<ResourceItem<?>>>>) storedResourcesField.get(null);

        // Get the current cluster context using reflection
        Field contextField = KubeResourceManager.class.getDeclaredField("CURRENT_CLUSTER_CONTEXT");
        contextField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<String> clusterContextTL = (ThreadLocal<String>) contextField.get(null);
        String clusterContext = clusterContextTL.get();
        String testName = KubeResourceManager.get().getTestContext().getDisplayName();

        int initialSize = storedResources
            .computeIfAbsent(clusterContext, c -> new ConcurrentHashMap<>())
            .computeIfAbsent(testName, t -> new Stack<>())
            .size();

        // Test pushToStack method
        KubeResourceManager.get().pushToStack(resourceItem);

        // Verify the item was added to the stack
        int newSize = storedResources.get(clusterContext).get(testName).size();
        assertEquals(initialSize + 1, newSize, "Stack size should increase by 1");

        // Verify the correct item was added
        ResourceItem<?> addedItem = storedResources.get(clusterContext).get(testName).peek();
        assertEquals(resourceItem, addedItem, "The added item should match the original");
    }

}
