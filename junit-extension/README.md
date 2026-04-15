# Kubetest4j JUnit Extensions

A comprehensive JUnit 6 extension for Kubernetes testing that provides declarative test configuration, namespace management via annotations, comprehensive log collection, and automatic resource management.

## Features

- **Automatic Resource Management** - Configurable cleanup strategies and lifecycle management
- **Declarative Namespaces** - `@ClassNamespace` for class-level and `@MethodNamespace` for per-test-method isolation
- **Multi-Context Testing** - Test across different Kubernetes clusters simultaneously with context-specific namespaces and resources
- **Namespace Protection** - Existing namespaces are used as-is and never deleted during cleanup
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
@KubernetesTest
class MyKubernetesTest {

    @ClassNamespace(name = "my-test")
    static Namespace testNs;

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testPodCreation() {
        Pod pod = new PodBuilder()
            .withNewMetadata()
            .withName("test-pod")
            .withNamespace("my-test")
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

## Namespace Management

Namespaces are declared using field-level annotations on the test class, not inside `@KubernetesTest`.

### @ClassNamespace — Class-Level Namespaces

Class namespaces are created in `beforeAll` and deleted in `afterAll`. They are shared across all test methods in the class. Must be placed on **static** fields of type `Namespace`.

```java
@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
class MultiNamespaceTest {

    @ClassNamespace(name = "frontend",
        labels = {"env=test", "tier=frontend"},
        annotations = {"description=Frontend test namespace"})
    static Namespace frontendNs;

    @ClassNamespace(name = "backend")
    static Namespace backendNs;

    @ClassNamespace(name = "monitoring")
    static Namespace monitoringNs;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testCrossNamespaceNetworking() {
        Service backendService = new ServiceBuilder()
            .withNewMetadata()
            .withName("api-service")
            .withNamespace("backend")
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
    }
}
```

### Namespace Protection

**Existing namespaces are automatically protected.** If a namespace already exists on the cluster, it is used as-is and **never deleted** during cleanup. You can safely mix existing namespaces (like `default`) with test-created ones:

```java
@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
class SafeNamespaceTest {

    @ClassNamespace(name = "default")
    static Namespace defaultNs;  // Protected — never deleted

    @ClassNamespace(name = "my-test-ns")
    static Namespace testNs;     // Created by test, deleted on cleanup
}
```

### @MethodNamespace — Per-Test-Method Namespaces

Create isolated namespaces per test method, similar to JUnit's `@TempDir`. Each test method gets its own fresh namespace. Ideal for parallel test execution.

**Field injection:**
```java
@KubernetesTest
class PerMethodNamespaceTest {

    @MethodNamespace
    Namespace testNs;  // Unique namespace created for each test method

    @MethodNamespace(prefix = "kafka", labels = {"app=kafka"})
    Namespace kafkaNs;  // Custom prefix and labels

    @Test
    void testOne() {
        // testNs and kafkaNs are fresh namespaces, unique to this test method
    }

    @Test
    void testTwo() {
        // testNs and kafkaNs are different namespaces than in testOne()
    }
}
```

**Parameter injection:**
```java
@Test
void testWithNamespace(@MethodNamespace Namespace ns) {
    // ns is a fresh, unique namespace for this test invocation
    assertNotNull(ns.getMetadata().getName());
}

@Test
void testWithCustomPrefix(
    @MethodNamespace(prefix = "app", annotations = {"purpose=testing"}) Namespace ns
) {
    assertTrue(ns.getMetadata().getName().startsWith("app-"));
}
```

**Namespace naming:** Generated names follow the pattern `<prefix>-<methodName>-<index>`, truncated to 63 characters (DNS-1123). The index is a simple counter (0, 1, 2, ...) for each namespace within the same test method.

**Coexistence:** `@ClassNamespace` and `@MethodNamespace` serve different purposes and can be used together:
- `@ClassNamespace` — **class-level**, same namespace for all tests
- `@MethodNamespace` — **method-level**, unique per test

```java
@KubernetesTest
class MixedNamespaceTest {

    @ClassNamespace(name = "shared")
    static Namespace sharedNs;  // Same namespace for all tests

    @MethodNamespace
    Namespace isolatedNs;  // Different namespace per test method

    @Test
    void test() {
        // sharedNs is the class-level "shared" namespace
        // isolatedNs is a fresh namespace just for this test
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

Log collection discovers namespaces automatically via the `kubetest4j.skodjob.io/log-collection=enabled` label, which is applied to all namespaces created by the framework.

```java
@KubernetesTest(
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE,
    logCollectionPath = "target/test-logs",
    collectPreviousLogs = true,
    collectNamespacedResources = {"pods", "services", "configmaps", "secrets"},
    collectClusterWideResources = {"nodes"}
)
class LogCollectionTest {

    @ClassNamespace(name = "log-test")
    static Namespace logTestNs;

    @Test
    void testThatFails() {
        // If this test fails, logs will be automatically collected
        // from all labeled namespaces
        throw new AssertionError("Demonstrating automatic log collection");
    }
}
```

### Log Collection Strategies

- **`ON_FAILURE`** - Collect logs only when tests fail (default)
- **`AFTER_EACH`** - Collect logs after each test method (success or failure)

## Annotations Reference

### @KubernetesTest

Main annotation to enable Kubernetes test features. This annotation is `@Inherited`, so it propagates to subclasses (see [Annotation Inheritance](#annotation-inheritance)).

```java
@KubernetesTest(
    // Resource types (declarative alternative to setResourceTypes())
    resourceTypes = {NamespaceType.class, DeploymentType.class},

    // Resource management
    cleanup = CleanupStrategy.AUTOMATIC,  // Cleanup strategy
    storeYaml = true,                      // Store resource YAMLs
    yamlStorePath = "target/yamls",        // YAML storage path

    // Visual output
    visualSeparatorChar = "#",             // Separator character
    visualSeparatorLength = 76,            // Separator length

    // ===== Log Collection =====
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE,
    logCollectionPath = "target/test-logs",
    collectPreviousLogs = true,
    collectNamespacedResources = {"pods", "services"},
    collectClusterWideResources = {"nodes"}
)
```

### @ClassNamespace

Declare class-level namespaces on static fields:

```java
@ClassNamespace(name = "my-ns")
static Namespace ns;

@ClassNamespace(name = "labeled-ns",
    labels = {"env=test", "team=backend"},
    annotations = {"description=Test namespace"})
static Namespace labeledNs;

// Multi-context support
@ClassNamespace(name = "stg-app", kubeContext = "staging")
static Namespace stagingNs;
```

### @MethodNamespace

Declare per-test-method namespaces on instance fields or parameters:

```java
@MethodNamespace
Namespace testNs;

@MethodNamespace(prefix = "kafka", labels = {"app=kafka"})
Namespace kafkaNs;

// Multi-context support
@MethodNamespace(kubeContext = "staging")
Namespace stagingTestNs;

// Parameter injection
@Test
void test(@MethodNamespace(prefix = "app") Namespace ns) { ... }
```

### @InjectKubeClient

Inject a configured Kubernetes client:

```java
@InjectKubeClient
KubeClient client;

// Context-specific injection
@InjectKubeClient(kubeContext = "staging")
KubeClient stagingClient;

@Test
void testWithClient(
    @InjectKubeClient KubeClient client,
    @InjectKubeClient(kubeContext = "staging") KubeClient stagingClient
) {
    // Both field and parameter injection work
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

@Test
void testResourceLifecycle() {
    // Primary context
    resourceManager.createResourceWithWait(myResource);

    // Context-specific resource management - truly isolated instances
    stagingResourceManager.createResourceWithWait(stagingResource);
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
```

### @InjectResource

Load and inject resources from YAML files or classpath manifests:

```java
@InjectResource("deployment.yaml")
Deployment deployment;

@InjectResource(value = "service.yaml", waitForReady = true)
Service service;

@InjectResource(value = "manifest.yaml", type = Service.class, name = "frontend-service")
Service selectedService;

// Context-specific resource injection
@InjectResource(kubeContext = "staging", value = "staging-deployment.yaml")
Deployment stagingDeployment;

@Test
void testResourceInjection(
    @InjectResource(kubeContext = "staging", value = "staging-config.yaml") ConfigMap stagingConfig
) {
    assertNotNull(stagingConfig);
}
```

`value` accepts either a classpath resource name such as `deployment.yaml` or a filesystem path.
For multi-document YAML manifests, kubetest4j applies all resources from the manifest and injects the one matching the field or parameter type, optionally narrowed by `type` and `name`.

**Lifecycle:** Resources are loaded from YAML and **created on the cluster** during field injection:
- **Instance fields** (`@InjectResource` on non-static fields) — created in `beforeEach`, once per test method. Cleaned up automatically after each test.
- **Method parameters** (`@InjectResource` on test method parameters) — created just before the test method runs.
- **With `@TestInstance(Lifecycle.PER_CLASS)`** — instance fields are injected in `beforeAll`, so resources are created once for the entire class.

Use `waitForReady = true` (default) to wait for the resource to become ready, or `waitForReady = false` to create without waiting.

## Cleanup Strategies

Control when resources are cleaned up:

- **`AUTOMATIC`** - Automatically clean up resources using KubeResourceManager (default)
  - Resources are cleaned up both after each test method and after all tests complete
  - Uses async deletion for better performance
  - Covers failure scenarios as well
- **`MANUAL`** - No automatic cleanup, manual resource management required

```java
@KubernetesTest(
    cleanup = CleanupStrategy.MANUAL  // No automatic cleanup
)
```

## Advanced Multi-Context Testing

Test across multiple Kubernetes clusters simultaneously using `kubeContext` on namespace and injection annotations:

```java
@KubernetesTest(
    storeYaml = true,
    yamlStorePath = "target/test-yamls"
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiContextTest {

    // Default context namespaces
    @ClassNamespace(name = "local-test",
        labels = {"environment=local"})
    static Namespace localTestNs;

    // Staging context namespaces
    @ClassNamespace(name = "stg-frontend", kubeContext = "staging",
        labels = {"environment=staging", "tier=application"})
    static Namespace stagingFrontendNs;

    // Production context namespaces
    @ClassNamespace(name = "prod-api", kubeContext = "production",
        labels = {"environment=production"})
    static Namespace productionApiNs;

    // Default context injections
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

    @Test
    void testCrossContextOperations() {
        // Default context
        ConfigMap defaultConfig = new ConfigMapBuilder()
            .withNewMetadata()
                .withName("multi-context-config")
                .withNamespace("local-test")
            .endMetadata()
            .addToData("environment", "local")
            .build();
        defaultResourceManager.createResourceWithoutWait(defaultConfig);

        // Staging context
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
    }

    @Test
    void testResourceInjectionWithContext(
        @InjectResource(kubeContext = "staging", value = "deployment.yaml")
        Deployment stagingDeployment
    ) {
        assertNotNull(stagingDeployment);
    }
}
```

### Context Configuration

Configure contexts using environment variables:
```bash
# Default context
KUBE_URL=https://api.default:6443
KUBE_TOKEN=default-token
# or
KUBECONFIG=/path/to/default.kubeconfig

# Staging context
KUBE_URL_STAGING=https://api.staging:6443
KUBE_TOKEN_STAGING=staging-token
# or
KUBECONFIG_STAGING=/path/to/staging.kubeconfig

# Production context
KUBE_URL_PRODUCTION=https://api.prod:6443
KUBE_TOKEN_PRODUCTION=prod-token
```

## Visual Test Output

The framework provides visual separators for enhanced readability:

```
############################################################################
TestClass io.example.MyKubernetesTest STARTED
Setting up Kubernetes test environment for class: MyKubernetesTest
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
    storeYaml = true,
    yamlStorePath = "target/test-yamls"
)
class YamlStorageTest {

    @ClassNamespace(name = "yaml-test")
    static Namespace yamlTestNs;

    @Test
    void testYamlStorage() {
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
│   ├── staging/                    # Staging context resources
│   │   └── TestClass/
│   │       └── testMethod/
│   └── production/                 # Production context resources
│       └── TestClass/
│           └── testMethod/
```

## Per-Class vs Per-Method Lifecycle

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  // Important for @BeforeAll injection
@KubernetesTest(cleanup = CleanupStrategy.AUTOMATIC)
class SharedResourceTest {

    @ClassNamespace(name = "shared-resources")
    static Namespace sharedNs;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @BeforeAll
    void setupSharedResources() {
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

## Parameter Injection

All injection annotations work with test method parameters, including context-specific injections:

```java
@Test
void testWithInjection(
    @InjectKubeClient KubeClient client,
    @InjectKubeClient(kubeContext = "staging") KubeClient stagingClient,
    @InjectResourceManager KubeResourceManager resourceManager,
    @InjectResourceManager(kubeContext = "production") KubeResourceManager prodResourceManager,
    @InjectCmdKubeClient KubeCmdClient<?> cmdClient,
    @InjectResource("deployment.yaml") Deployment deployment,
    @InjectResource(kubeContext = "staging", value = "staging-deployment.yaml") Deployment stagingDeployment,
    @MethodNamespace Namespace testNs,
    @MethodNamespace(prefix = "app") Namespace appNs
) {
    // All parameters are automatically injected
}
```

## Thread Safety

The framework provides full thread safety support for parallel test execution:

- **Per-Context Instances**: Each Kubernetes context gets its own KubeResourceManager
- **Automatic Cleanup**: Resources are tracked per-test and cleaned up automatically
- **Context Isolation**: Each context operates independently without conflicts
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
- **[`MultiNamespaceIT`](src/test/java/io/skodjob/kubetest4j/examples/MultiNamespaceIT.java)** - Multi-namespace testing with `@ClassNamespace`
- **[`MultiContextIT`](src/test/java/io/skodjob/kubetest4j/examples/MultiContextIT.java)** - Advanced multi-context testing with `kubeContext`
- **[`NamespaceInjectionIT`](src/test/java/io/skodjob/kubetest4j/examples/NamespaceInjectionIT.java)** - Multiple class namespace injection
- **[`NamespaceProtectionIT`](src/test/java/io/skodjob/kubetest4j/examples/NamespaceProtectionIT.java)** - Namespace protection and safe cleanup demonstration
- **[`AdvancedKubernetesIT`](src/test/java/io/skodjob/kubetest4j/examples/AdvancedKubernetesIT.java)** - Advanced features, PER_CLASS lifecycle, manual cleanup
- **[`ResourceInjectionIT`](src/test/java/io/skodjob/kubetest4j/examples/ResourceInjectionIT.java)** - YAML resource injection patterns
- **[`PerMethodNamespaceIT`](src/test/java/io/skodjob/kubetest4j/examples/PerMethodNamespaceIT.java)** - Per-method namespace isolation with `@MethodNamespace`
- **[`ParallelExecutionIT`](src/test/java/io/skodjob/kubetest4j/examples/ParallelExecutionIT.java)** - Parallel test execution with `@MethodNamespace`
- **[`LogCollectionIT`](src/test/java/io/skodjob/kubetest4j/examples/LogCollectionIT.java)** - Log collection strategies and configuration

## Annotation Inheritance

`@KubernetesTest` is `@Inherited`, so you can define a shared base class and have child classes inherit the configuration:

```java
@KubernetesTest(
    cleanup = CleanupStrategy.AUTOMATIC,
    resourceTypes = {NamespaceType.class, DeploymentType.class},
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE
)
abstract class AbstractIT {
    // Shared setup, helpers, etc.
}

// Inherits everything from parent — no annotation needed
class BasicIT extends AbstractIT {
    @ClassNamespace(name = "basic-test")
    static Namespace testNs;

    @Test
    void testSomething() { ... }
}

// Override: child re-declares @KubernetesTest with different config
@KubernetesTest(cleanup = CleanupStrategy.MANUAL)
class ManualCleanupIT extends AbstractIT {
    // cleanup is MANUAL, but resourceTypes are still inherited from parent
}
```

### resourceTypes Inheritance

The `resourceTypes` parameter has special merge behavior. When a child overrides `@KubernetesTest` but doesn't declare its own `resourceTypes`, it inherits the parent's `resourceTypes` automatically. This means you can override individual parameters without losing the parent's resource type registrations:

```java
@KubernetesTest(resourceTypes = {NamespaceType.class, DeploymentType.class})
abstract class AbstractIT {}

// Inherits parent's resourceTypes via @Inherited
class ChildIT extends AbstractIT {}

// Overrides cleanup but keeps parent's resourceTypes
@KubernetesTest(cleanup = CleanupStrategy.MANUAL)
class OverrideIT extends AbstractIT {}

// Declares own resourceTypes — uses these instead of parent's
@KubernetesTest(resourceTypes = {NamespaceType.class, ServiceType.class})
class FullOverrideIT extends AbstractIT {}
```

## Important Notes

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
