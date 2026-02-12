# Kubetest4j JUnit Extensions

A comprehensive JUnit 6 extension for Kubernetes testing that provides declarative test configuration, multi-namespace support, comprehensive log collection, and automatic resource management.

## Features

- **Automatic Resource Management** - Configurable cleanup strategies and lifecycle management
- **Multi-Context Testing** - Test across different Kubernetes clusters simultaneously with context-specific namespaces and resources
- **Log Collection** - Automatic log collection with multiple strategies and exception handling
- **Dependency Injection** - Inject Kubernetes clients, resource managers, and resources with context-specific support
- **YAML Resource Loading** - Load and inject resources from YAML files with multi-context support
- **YAML Storage** - Automatically store deployed resources as YAML files for debugging and audit
- **Visual Test Output** - Enhanced test logging with visual separators

## Quick Start

### 1. Add Dependency

Add the JUnit 6 extension dependency to your project:

```xml
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>junit-extension</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
```

### 2. Basic Test Example

```java
@KubernetesTest(namespaces = {"my-test"})
class MyKubernetesTest {

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testPodCreation() {
        Pod pod = new PodBuilder()
            .withNewMetadata()
            .withName("test-pod")
            .withNamespace("my-test")  // Must specify namespace explicitly
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .withImage("quay.io/prometheus/busybox")
            .withCommand("sleep", "300")
            .endContainer()
            .endSpec()
            .build();

        resourceManager.createResourceWithWait(pod);

        // Pod is automatically cleaned up after test
        assertNotNull(client.getClient().pods()
            .inNamespace("my-test")
            .withName("test-pod")
            .get());
    }
}
```

## Multi-Namespace Testing

Create and manage multiple namespaces for complex test scenarios. **Existing namespaces are automatically protected** - only namespaces created by the test will be deleted during cleanup:

> **Design Note**: `@KubernetesTest` always uses your current/default Kubernetes context as the primary context. Use `additionalKubeContexts` to test across multiple clusters simultaneously.

```java
@KubernetesTest(
    namespaces = {"frontend", "backend", "monitoring"},
    cleanup = CleanupStrategy.AUTOMATIC
)
class MultiNamespaceTest {

    @Test
    void testCrossNamespaceNetworking() {
        // Create service in backend namespace
        Service backendService = new ServiceBuilder()
            .withNewMetadata()
            .withName("api-service")
            .withNamespace("backend")  // Explicit namespace required
            .endMetadata()
            .withNewSpec()
            .withSelector(Map.of("app", "backend"))
            .addNewPort()
            .withPort(8080)
            .withTargetPort(new IntOrStringBuilder().withValue(8080).build())
            .endPort()
            .endSpec()
            .build();

        resourceManager.createResourceWithWait(backendService);

        // Test connectivity from frontend namespace
        // All three namespaces are automatically created and available
    }
}
```

## Comprehensive Log Collection

### Automatic Log Collection with Exception Handlers

The framework uses exception handlers to capture failures from **ANY** test lifecycle phase:

- `@BeforeAll` method failures
- `@BeforeEach` method failures
- Test execution failures
- `@AfterEach` method failures
- `@AfterAll` method failures

```java
@KubernetesTest(
    namespaces = {"log-test"},
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE,
    logCollectionPath = "target/test-logs",
    collectPreviousLogs = true,
    collectNamespacedResources = {"pods", "services", "configmaps", "secrets"},
    collectClusterWideResources = {"nodes"}
)
class LogCollectionTest {

    @Test
    void testThatFails() {
        // If this test fails, logs will be automatically collected
        // from all configured namespaces and resources
        throw new AssertionError("Demonstrating automatic log collection");
    }
}
```

### Log Collection Strategies

- **`ON_FAILURE`** - Collect logs only when tests fail (default)
- **`AFTER_EACH`** - Collect logs after each test method (success or failure)

### Configurable Log Collection

```java
@KubernetesTest(
    namespaces = {"comprehensive-test"},

    // ===== Log Collection Configuration =====
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.AFTER_EACH,
    logCollectionPath = "target/test-logs/comprehensive",
    collectPreviousLogs = true,  // Include previous container logs

    // Specify which namespaced resources to collect
    collectNamespacedResources = {
        "pods", "services", "configmaps", "secrets",
        "deployments", "replicasets", "daemonsets"
    },

    // Specify cluster-wide resources to collect
    collectClusterWideResources = {"nodes", "persistentvolumes"}
)
```

## Annotations Reference

### @KubernetesTest

Main annotation to enable Kubernetes test features:

```java
@KubernetesTest(
    // Multi-namespace support
    namespaces = {"app", "monitoring"},     // Namespaces to create/use

    // Resource management
    cleanup = CleanupStrategy.AUTOMATIC,  // Cleanup strategy
    storeYaml = true,                      // Store resource YAMLs
    yamlStorePath = "target/yamls",        // YAML storage path

    // Namespace metadata
    namespaceLabels = {"env=test", "team=backend"},
    namespaceAnnotations = {"description=Test namespace"},

    // Visual output
    visualSeparatorChar = "#",             // Separator character
    visualSeparatorLength = 76,            // Separator length

    // ===== Additional Kube Contexts =====
    additionalKubeContexts = {
        @KubernetesTest.AdditionalKubeContext(
            name = "staging",                      // Additional context name
            namespaces = {"stg-app", "stg-db"},    // Context-specific namespaces
            namespaceLabels = {"env=staging"},     // Context-specific namespace labels
            namespaceAnnotations = {"stage=stg"}, // Context-specific namespace annotations
            cleanup = CleanupStrategy.AUTOMATIC   // Context-specific cleanup strategy
        )
    },

    // ===== Log Collection =====
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE,
    logCollectionPath = "target/test-logs",
    collectPreviousLogs = true,
    collectNamespacedResources = {"pods", "services"},
    collectClusterWideResources = {"nodes"}
)
```

### @InjectKubeClient

Inject a configured Kubernetes client:

```java
@InjectKubeClient
KubeClient client;

// Context-specific injection
@InjectKubeClient(kubeContext = "staging")
KubeClient stagingClient;

@InjectKubeClient(kubeContext = "production")
KubeClient productionClient;

@Test
void testWithClient(
    @InjectKubeClient KubeClient client,
    @InjectKubeClient(kubeContext = "staging") KubeClient stagingClient
) {
    // Both field and parameter injection work
    // Both primary and kubeContext-specific injection work
}
```

### @InjectCmdKubeClient

Inject a command-line kubectl client:

```java
@InjectCmdKubeClient
KubeCmdClient<?> cmdClient;

// Context-specific injection
@InjectCmdKubeClient(kubeContext = "staging")
KubeCmdClient<?> stagingCmdClient;

@Test
void testWithCmdClient() {
    cmdClient.exec("get", "pods", "-n", "my-namespace");
    stagingCmdClient.exec("get", "pods", "-n", "stg-frontend");
}
```

### @InjectResourceManager

Inject the resource manager for lifecycle management:

```java
@InjectResourceManager
KubeResourceManager resourceManager;

// Context-specific injection
@InjectResourceManager(kubeContext = "staging")
KubeResourceManager stagingResourceManager;

@InjectResourceManager(kubeContext = "production")
KubeResourceManager productionResourceManager;

@Test
void testResourceLifecycle() {
    // Primary kubeContext
    resourceManager.createResourceWithWait(myResource);

    // Context-specific resource management - truly isolated instances
    stagingResourceManager.createResourceWithWait(stagingResource);
    productionResourceManager.createResourceWithWait(productionResource);
    // Each manager operates on its own context simultaneously
}
```

#### Multi-Context Architecture

The framework uses **per-context singleton instances**, ensuring true isolation between different Kubernetes contexts:

```java
// Each context gets its own dedicated KubeResourceManager instance
KubeResourceManager defaultMgr = KubeResourceManager.get();                    // Default context
KubeResourceManager stagingMgr = KubeResourceManager.getForContext("staging"); // Staging context
KubeResourceManager prodMgr = KubeResourceManager.getForContext("production"); // Production context

// All instances can operate simultaneously without conflicts
// No more ThreadLocal context switching - true parallel execution
```

### @InjectNamespaces

Inject a Map of Namespace objects corresponding to the namespaces defined in @KubernetesTest (key=namespace name, value=Namespace object):

```java
@InjectNamespaces
Map<String, Namespace> namespaces;

// Context-specific injection
@InjectNamespaces(kubeContext = "staging")
Map<String, Namespace> stagingNamespaces;

@InjectNamespaces(kubeContext = "production")
Map<String, Namespace> productionNamespaces;

@Test
void testNamespaces(
    @InjectNamespaces Map<String, Namespace> paramNamespaces,
    @InjectNamespaces(kubeContext = "staging") Map<String, Namespace> paramStagingNamespaces
) {
    // Primary kubeContext namespaces
    assertEquals(2, namespaces.size());
    assertTrue(namespaces.containsKey("app"));
    assertTrue(namespaces.containsKey("monitoring"));

    // Staging kubeContext namespaces
    assertEquals(2, stagingNamespaces.size());
    assertTrue(stagingNamespaces.containsKey("stg-frontend"));
    assertTrue(stagingNamespaces.containsKey("stg-backend"));
}
```

### @InjectNamespace

Inject a specific Namespace object by name:

```java
@InjectNamespace(name = "app")
Namespace appNamespace;

@InjectNamespace(name = "monitoring")
Namespace monitoringNamespace;

// Context-specific injection
@InjectNamespace(kubeContext = "staging", name = "stg-frontend")
Namespace stagingFrontendNamespace;

@InjectNamespace(kubeContext = "production", name = "prod-api")
Namespace productionApiNamespace;

@Test
void testSpecificNamespace(
    @InjectNamespace(name = "app") Namespace paramAppNamespace,
    @InjectNamespace(kubeContext = "staging", name = "stg-backend") Namespace paramStagingBackendNamespace
) {
    // Primary kubeContext namespaces
    assertEquals("app", appNamespace.getMetadata().getName());
    assertEquals("monitoring", monitoringNamespace.getMetadata().getName());

    // Context-specific namespaces
    assertEquals("stg-frontend", stagingFrontendNamespace.getMetadata().getName());
    assertEquals("prod-api", productionApiNamespace.getMetadata().getName());
}
```

### @InjectResource

Load and inject resources from YAML files:

```java
@InjectResource("deployment.yaml")
Deployment deployment;

@InjectResource(value = "service.yaml", waitForReady = true)
Service service;

// Context-specific resource injection
@InjectResource(kubeContext = "staging", value = "staging-deployment.yaml")
Deployment stagingDeployment;

@InjectResource(kubeContext = "production", value = "prod-service.yaml", waitForReady = true)
Service productionService;

@Test
void testResourceInjection(
    @InjectResource(kubeContext = "staging", value = "staging-config.yaml") ConfigMap stagingConfig
) {
    // Resources are automatically deployed to their respective contexts
    assertNotNull(stagingConfig);
    assertEquals("stg-frontend", stagingConfig.getMetadata().getNamespace());
}
```

## Cleanup Strategies

Control when resources are cleaned up:

- **`AUTOMATIC`** - Automatically clean up resources using KubeResourceManager (default)
  - Resources are cleaned up both after each test method and after all tests complete
  - Uses async deletion for better performance
  - Covers failure scenarios as well
- **`MANUAL`** - No automatic cleanup, manual resource management required

```java
@KubernetesTest(
    namespaces = {"cleanup-test"},
    cleanup = CleanupStrategy.MANUAL  // No automatic cleanup
)
```

## Resource Injection from YAML

Load and apply resources from YAML files automatically:

```yaml
# test-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-app
  namespace: my-test  # Namespace must be specified!
spec:
  replicas: 2
  selector:
    matchLabels:
      app: test-app
  template:
    metadata:
      labels:
        app: test-app
    spec:
      containers:
      - name: app
        image: nginx:latest
```

```java
@KubernetesTest(namespaces = {"my-test"})
class ResourceTest {

    @InjectResource("test-deployment.yaml")
    Deployment deployment; // Automatically loaded and applied

    @Test
    void testDeployment() {
        assertEquals(2, deployment.getSpec().getReplicas());
        // Deployment is already running in the cluster
    }
}
```

## Advanced Multi-Context Testing

### Single Context Testing

Test against the default Kubernetes context:

```java
@KubernetesTest(
    namespaces = {"staging-test"}
)
class DefaultContextTest {
    // Tests run against current/default Kubernetes context
    // No additional context configuration needed
}
```

To test against different clusters, configure your `kubectl` context or use additional contexts:

```java
@KubernetesTest(
    namespaces = {"local-test"},
    additionalKubeContexts = {
        @KubernetesTest.AdditionalKubeContext(
            name = "staging",
            namespaces = {"stg-test"}
        )
    }
)
class MultiClusterTest {
    @Test
    void testDefaultContext(@InjectKubeClient KubeClient defaultClient) {
        // Tests against current context
    }

    @Test
    void testStagingContext(@InjectKubeClient(kubeContext = "staging") KubeClient stagingClient) {
        // Tests against staging context
    }
}
```

### Multi-Context Testing (Advanced)

Test across multiple Kubernetes clusters simultaneously with context-specific namespaces:

```java
@KubernetesTest(
    // Primary context configuration (uses default/current kubeconfig context)
    namespaces = {"local-test", "local-monitoring"},
    storeYaml = true,
    yamlStorePath = "target/test-yamls",

    // Additional context configuration
    additionalKubeContexts = {
        @KubernetesTest.AdditionalKubeContext(
            name = "staging",
            namespaces = {"stg-frontend", "stg-backend"},
            namespaceLabels = {"environment=staging", "tier=application"},
            cleanup = CleanupStrategy.AUTOMATIC
        ),
        @KubernetesTest.AdditionalKubeContext(
            name = "production",
            namespaces = {"prod-api", "prod-cache"},
            namespaceLabels = {"environment=production"}
        ),
        @KubernetesTest.AdditionalKubeContext(
            name = "development",
            namespaces = {"dev-experimental"},
            namespaceLabels = {"team=platform", "purpose=testing"}
        )
    }
)
class MultiContextTest {

    // Primary kubeContext injections
    @InjectKubeClient
    KubeClient defaultClient;

    @InjectResourceManager
    KubeResourceManager defaultResourceManager;

    // Context-specific injections
    @InjectKubeClient(kubeContext = "staging")
    KubeClient stagingClient;

    @InjectResourceManager(kubeContext = "staging")
    KubeResourceManager stagingResourceManager;

    @InjectKubeClient(kubeContext = "production")
    KubeClient productionClient;

    // Context-specific namespace injections
    @InjectNamespaces(kubeContext = "staging")
    Map<String, Namespace> stagingNamespaces;

    @InjectNamespace(kubeContext = "staging", name = "stg-frontend")
    Namespace stagingFrontendNamespace;

    @Test
    void testCrossContextOperations() {
        // Create resources in different contexts

        // Default kubeContext
        ConfigMap defaultConfig = new ConfigMapBuilder()
            .withNewMetadata()
                .withName("multi-kubeContext-config")
                .withNamespace("local-test")
            .endMetadata()
            .addToData("environment", "local")
            .build();
        defaultResourceManager.createResourceWithoutWait(defaultConfig);

        // Staging kubeContext
        Pod stagingPod = new PodBuilder()
            .withNewMetadata()
                .withName("staging-test-pod")
                .withNamespace("stg-frontend")
            .endMetadata()
            .withNewSpec()
                .addNewContainer()
                    .withName("test-container")
                    .withImage("nginx:alpine")
                .endContainer()
            .endSpec()
            .build();
        stagingResourceManager.createResourceWithWait(stagingPod);

        // Verify resources exist in their respective contexts
        assertNotNull(defaultClient.getClient()
            .configMaps()
            .inNamespace("local-test")
            .withName("multi-kubeContext-config")
            .get());

        assertNotNull(stagingClient.getClient()
            .pods()
            .inNamespace("stg-frontend")
            .withName("staging-test-pod")
            .get());
    }

    @Test
    void testResourceInjectionWithContext(
        @InjectResource(kubeContext = "staging", value = "deployment.yaml")
        Deployment stagingDeployment
    ) {
        // Resource is automatically deployed to staging kubeContext
        assertNotNull(stagingDeployment);
        assertEquals("stg-frontend", stagingDeployment.getMetadata().getNamespace());
    }
}
```

### Context Configuration

Configure contexts using environment variables:
```bash
# Default kubeContext
KUBE_URL=https://api.default:6443
KUBE_TOKEN=default-token
# or
KUBECONFIG=/path/to/default.kubeconfig

# Staging kubeContext
KUBE_URL_STAGING=https://api.staging:6443
KUBE_TOKEN_STAGING=staging-token
# or
KUBECONFIG_STAGING=/path/to/staging.kubeconfig

# Production kubeContext
KUBE_URL_PRODUCTION=https://api.prod:6443
KUBE_TOKEN_PRODUCTION=prod-token

# Development kubeContext
KUBECONFIG_DEVELOPMENT=/path/to/development.kubeconfig
```

## Visual Test Output

The framework provides visual separators for enhanced readability:

```
############################################################################
TestClass io.example.MyKubernetesTest STARTED
Setting up Kubernetes test environment for class: MyKubernetesTest
Using namespaces: frontend, backend, monitoring
############################################################################
Test io.example.MyKubernetesTest.testMethod STARTED
...
Test io.example.MyKubernetesTest.testMethod SUCCEEDED
############################################################################
TestClass io.example.MyKubernetesTest FINISHED
############################################################################
```

Customize separators:
```java
@KubernetesTest(
    visualSeparatorChar = "=",
    visualSeparatorLength = 60
)
```

## YAML Storage

Automatically store deployed resources as YAML files for debugging and audit purposes:

```java
@KubernetesTest(
    namespaces = {"yaml-test"},
    storeYaml = true,                      // Enable YAML storage
    yamlStorePath = "target/test-yamls",   // Storage directory (default: target/test-yamls)

    // Additional contexts for YAML storage
    additionalKubeContexts = {
        @KubernetesTest.AdditionalKubeContext(
            name = "staging",
            namespaces = {"stg-app"}
        )
    }
)
class YamlStorageTest {

    @Test
    void testYamlStorage() {
        // Create resources - they will be automatically stored as YAML files
        ConfigMap config = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test-config")
            .withNamespace("yaml-test")
            .endMetadata()
            .addToData("key", "value")
            .build();

        resourceManager.createResourceWithWait(config);

        // YAML file will be stored at:
        // target/test-yamls/test-files/primary/YamlStorageTest/testYamlStorage/ConfigMap-yaml-test-test-config.yaml
    }

    @Test
    void testMultiContextYamlStorage(
        @InjectResource(kubeContext = "staging", value = "staging-app.yaml")
        Deployment stagingApp
    ) {
        // Multi-kubeContext resources are stored in separate directories:
        // target/test-yamls/test-files/staging/YamlStorageTest/testMultiContextYamlStorage/Deployment-stg-app-staging-app.yaml
    }
}
```

### YAML File Organization

YAML files are organized by context and test:

```
target/test-yamls/
├── test-files/
│   ├── primary/                    # Primary context resources
│   │   └── TestClass/
│   │       ├── before-all/         # Resources created in @BeforeAll
│   │       ├── testMethod/         # Resources created in test method
│   │       └── after-each/         # Resources created in @AfterEach
│   ├── staging/                    # Staging kubeContext resources
│   │   └── TestClass/
│   │       └── testMethod/
│   └── production/                 # Production context resources
│       └── TestClass/
│           └── testMethod/
```

## Advanced Features

### Complex Namespace Configuration

```java
@KubernetesTest(
    namespaces = {"app-frontend", "app-backend", "monitoring"},
    namespaceLabels = {
        "environment=test",
        "team=backend",
        "cost-center=engineering"
    },
    namespaceAnnotations = {
        "description=Integration tests for microservices",
        "contact=backend-team@company.com"
    }
)
```

### Per-Class vs Per-Method Lifecycle

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  // Important for @BeforeAll injection
@KubernetesTest(
    namespaces = {"shared-resources"},
    cleanup = CleanupStrategy.AUTOMATIC
)
class SharedResourceTest {

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @BeforeAll
    void setupSharedResources() {
        // resourceManager is properly injected for PER_CLASS lifecycle
        ConfigMap sharedConfig = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("shared-config")
            .withNamespace("shared-resources")
            .endMetadata()
            .addToData("shared.key", "shared.value")
            .build();

        resourceManager.createResourceWithWait(sharedConfig);
    }
}
```

### Namespace Injection Example

```java
@KubernetesTest(
    namespaces = {"app", "monitoring"},
    namespaceLabels = {"environment=test", "team=backend"}
)
class NamespaceTest {

    @InjectNamespaces
    Map<String, Namespace> namespaces;

    @Test
    void testNamespaceAccess() {
        assertEquals(2, namespaces.size());

        // Access namespace metadata
        assertEquals("app", namespaces.get("app").getMetadata().getName());
        assertEquals("test", namespaces.get("app").getMetadata().getLabels().get("environment"));

        assertEquals("monitoring", namespaces.get("monitoring").getMetadata().getName());
        assertEquals("backend", namespaces.get("monitoring").getMetadata().getLabels().get("team"));
    }
}
```

### Parameter Injection

All injection annotations work with test method parameters, including context-specific injections:

```java
@Test
void testWithInjection(
    @InjectKubeClient KubeClient client,
    @InjectKubeClient(kubeContext = "staging") KubeClient stagingClient,
    @InjectResourceManager KubeResourceManager resourceManager,
    @InjectResourceManager(kubeContext = "production") KubeResourceManager prodResourceManager,
    @InjectCmdKubeClient KubeCmdClient<?> cmdClient,
    @InjectNamespaces Map<String, Namespace> namespaces,
    @InjectNamespaces(kubeContext = "staging") Map<String, Namespace> stagingNamespaces,
    @InjectNamespace(name = "app") Namespace appNamespace,
    @InjectNamespace(kubeContext = "staging", name = "stg-frontend") Namespace stagingNamespace,
    @InjectResource("deployment.yaml") Deployment deployment,
    @InjectResource(kubeContext = "staging", value = "staging-deployment.yaml") Deployment stagingDeployment
) {

    // All parameters are automatically injected
    assertNotNull(client);
    assertNotNull(stagingClient);
    assertNotNull(resourceManager);
    assertNotNull(prodResourceManager);
    assertNotNull(cmdClient);
    assertNotNull(namespaces);
    assertNotNull(stagingNamespaces);
    assertNotNull(appNamespace);
    assertNotNull(stagingNamespace);
    assertNotNull(deployment);
    assertNotNull(stagingDeployment);

    // Verify kubeContext isolation
    assertNotEquals(client, stagingClient);
    assertNotEquals(resourceManager, prodResourceManager);
}
```

## Thread Safety

The framework provides full thread safety support for parallel test execution:

- **ThreadLocal Variables**: All ResourceManager context switching uses ThreadLocal variables
- **Automatic Cleanup**: ThreadLocal variables are automatically cleaned up after each test class
- **Context Isolation**: Each test thread maintains its own Kubernetes context stack
- **Parallel Execution**: Tests can run in parallel without context contamination

```java
// Maven Surefire configuration for parallel execution
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <parallel>classes</parallel>
        <threadCount>4</threadCount>
    </configuration>
</plugin>
```

## Examples

See comprehensive examples in the test directory:

- **[`BasicKubernetesIT`](src/test/java/io/skodjob/kubetest4j/examples/BasicKubernetesIT.java)** - Basic usage, injection, and resource creation
- **[`MultiNamespaceIT`](src/test/java/io/skodjob/kubetest4j/examples/MultiNamespaceIT.java)** - Multi-namespace testing patterns
- **[`MultiContextIT`](src/test/java/io/skodjob/kubetest4j/examples/MultiContextIT.java)** - Advanced multi-context testing with context mappings
- **[`NamespaceInjectionIT`](src/test/java/io/skodjob/kubetest4j/examples/NamespaceInjectionIT.java)** - Map-based namespace injection with @InjectNamespaces
- **[`SingleNamespaceInjectionIT`](src/test/java/io/skodjob/kubetest4j/examples/SingleNamespaceInjectionIT.java)** - Single namespace injection patterns
- **[`NamespaceProtectionIT`](src/test/java/io/skodjob/kubetest4j/examples/NamespaceProtectionIT.java)** - Namespace protection and safe cleanup demonstration
- **[`AdvancedKubernetesIT`](src/test/java/io/skodjob/kubetest4j/examples/AdvancedKubernetesIT.java)** - Advanced features, PER_CLASS lifecycle, manual cleanup
- **[`ResourceInjectionIT`](src/test/java/io/skodjob/kubetest4j/examples/ResourceInjectionIT.java)** - YAML resource injection patterns
- **[`LogCollectionIT`](src/test/java/io/skodjob/kubetest4j/examples/LogCollectionIT.java)** - Log collection strategies and configuration

## Important Notes

### Namespace Protection and Management

The framework **automatically protects existing namespaces** from deletion:

- **Existing namespaces**: If a namespace already exists, it will be used as-is and **never deleted**
- **Created namespaces**: Only namespaces created by the test will be deleted during cleanup
- **Mixed scenarios**: You can safely mix existing namespaces (like `default`, `kube-system`) with test-created ones

```java
@KubernetesTest(
    namespaces = {"default", "my-test-ns", "existing-ns"} // Mix of existing and new
)
class SafeNamespaceTest {
    // 'default' and 'existing-ns' will be protected from deletion
    // Only 'my-test-ns' will be deleted if it was created by the test
}
```

### Namespace Specification Required

**All resources must explicitly specify their namespace** in the metadata. The framework does not inject or default namespaces:

```java
// CORRECT - Namespace explicitly specified
ConfigMap config = new ConfigMapBuilder()
    .withNewMetadata()
    .withName("config")
    .withNamespace("my-test")  // Required!
    .endMetadata()
    .build();

// INCORRECT - No namespace specified
ConfigMap config = new ConfigMapBuilder()
    .withNewMetadata()
    .withName("config")
    // Missing namespace - will fail or use unexpected namespace
    .endMetadata()
    .build();
```

### YAML Resources Must Include Namespace

```yaml
# CORRECT
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-config
  namespace: my-test  # Required!
data:
  key: value

# INCORRECT - Missing namespace
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-config
  # No namespace specified
data:
  key: value
```
