# Kubetest4j Core

The core module providing resource lifecycle management, Kubernetes clients, and testing utilities.

For the higher-level declarative approach with `@KubernetesTest`, see the [junit-extension](../junit-extension/README.md).

## Installation

### Maven
```xml
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>kubetest4j</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
```

### Gradle
```groovy
testImplementation 'io.skodjob.kubetest4j:kubetest4j:{version}'
```

## KubeResourceManager

[KubeResourceManager](src/main/java/io/skodjob/kubetest4j/resources/KubeResourceManager.java) is the central component for managing Kubernetes resources during tests. Every resource created through it is automatically deleted at the end of the test, whether the test passes or fails.

### Basic Usage

Annotate your test class with `@ResourceManager` to enable automatic resource tracking and cleanup. Add `@TestVisualSeparator` for readable test log output.

```java
@ResourceManager
@TestVisualSeparator
class MyTest {

    @Test
    void testMethod() {
        Namespace ns = new NamespaceBuilder()
            .withNewMetadata().withName("test").endMetadata().build();

        KubeResourceManager.get().createResourceWithWait(ns);

        assertNotNull(KubeResourceManager.get().kubeCmdClient().get("namespace", "test"));

        // No manual cleanup needed - handled automatically after the test
    }
}
```

For cleanup options (disabling automatic cleanup, async vs synchronous deletion, manual `deleteResources()` calls, and `openBatch()` grouping), see **[Resource Batches](../docs/RESOURCE-BATCH.md)**.

## Clients

### Fabric8 Kubernetes Client

`KubeResourceManager` provides access to a pre-configured Fabric8 client:

```java
KubeResourceManager.get().kubeClient().getClient()
    .pods().inNamespace("test").list();
```

### CMD Client (kubectl/oc)

```java
KubeResourceManager.get().kubeCmdClient().exec("get", "pods", "-n", "test");
```

Set the client type via `CLIENT_TYPE` environment variable. For all connection variables, multi-context configuration, and YAML config file format, see the **[Configuration Reference](../docs/CONFIGURATION.md)**.

## Multi-Context Cluster Support

KubeResourceManager supports testing across multiple Kubernetes clusters simultaneously.

### Option 1: Temporary Context Switching

Use `useContext()` for short operations in a different context:

```java
@ResourceManager
class MultiContextTest {

    @Test
    void testMethod() {
        KubeResourceManager.get().createResourceWithWait(defaultNamespace);

        try (var ctx = KubeResourceManager.get().useContext("prod")) {
            KubeResourceManager.get().createResourceWithWait(prodNamespace);
        }
        // Automatically returns to previous context
    }
}
```

### Option 2: Per-Context Singletons (Recommended)

Get dedicated instances for each context that can be used simultaneously without conflicts:

```java
@ResourceManager
class MultiContextTest {

    @Test
    void testMultiContext() {
        KubeResourceManager defaultMgr = KubeResourceManager.get();
        KubeResourceManager prodMgr = KubeResourceManager.getForContext("prod");
        KubeResourceManager stageMgr = KubeResourceManager.getForContext("stage");

        // All can be used simultaneously without conflicts
        defaultMgr.createResourceWithWait(defaultDeployment);
        prodMgr.createResourceWithWait(prodDeployment);
        stageMgr.createResourceWithWait(stageDeployment);
    }
}
```

Configure additional contexts via environment variables, e.g. `KUBE_URL_PROD` / `KUBE_TOKEN_PROD` or `KUBECONFIG_PROD`. See the **[Configuration Reference](../docs/CONFIGURATION.md)** for the full multi-context setup.

## ResourceType Registration

`ResourceType<T>` implementations teach `KubeResourceManager` how to create, update, delete, and check readiness for a specific resource kind. Register them once before any test creates resources:

```java
KubeResourceManager.get().setResourceTypes(
    new NamespaceType(),
    new DeploymentType(),
    new ServiceType()
);
```

Resources without a registered type are handled as generic Kubernetes objects with no readiness check.

See **[Resource Types](../docs/RESOURCE-TYPES.md)** for the full list of built-in types (kubernetes-resources and openshift-resources modules), readiness semantics for each, and a complete guide to implementing custom types for operator CRDs.

## Resource Callbacks

Register callbacks that run on every resource create or delete:

```java
// Called for every resource creation
KubeResourceManager.get().addCreateCallback(resource -> {
    if (resource.getKind().equals("Namespace")) {
        KubeUtils.labelNamespace(resource.getMetadata().getName(), "managed-by", "kubetest4j");
    }
});

// Called for every resource deletion
KubeResourceManager.get().addDeleteCallback(resource -> {
    LoggerUtils.logResource("Deleted", resource);
});
```

## YAML Storage

Store all created resources as YAML files for debugging:

```java
KubeResourceManager.get().setStoreYamlPath("target/test-yamls");
```

## Utilities

The core module includes utilities for common Kubernetes operations:

| Utility | Description |
|---------|-------------|
| [PodUtils](src/main/java/io/skodjob/kubetest4j/utils/PodUtils.java) | Wait for pod readiness, pod snapshots, stability checks |
| [JobUtils](src/main/java/io/skodjob/kubetest4j/utils/JobUtils.java) | Wait for job success/failure, log message checks |
| [KubeUtils](src/main/java/io/skodjob/kubetest4j/utils/KubeUtils.java) | OLM operations, namespace labeling, cluster detection |
| [ImageUtils](src/main/java/io/skodjob/kubetest4j/utils/ImageUtils.java) | Image registry/org/tag manipulation |
| [SecurityUtils](src/main/java/io/skodjob/kubetest4j/utils/SecurityUtils.java) | Certificate/TLS PEM export utilities |
| [KubeTestUtils](src/main/java/io/skodjob/kubetest4j/utils/KubeTestUtils.java) | YAML parsing, classpath resource loading, retry logic |
| [LoggerUtils](src/main/java/io/skodjob/kubetest4j/utils/LoggerUtils.java) | Resource logging, visual separators |
| [Wait](src/main/java/io/skodjob/kubetest4j/wait/Wait.java) | Polling-based wait: `Wait.until(description, pollMs, timeoutMs, condition)` |
| [Exec](src/main/java/io/skodjob/kubetest4j/executor/Exec.java) | Command execution with timeout support |

### Wait

`Wait.until` polls a `BooleanSupplier` at a fixed interval until it returns `true` or the timeout is reached. On timeout it throws `WaitException`.

```java
// Wait up to 5 minutes, polling every 5 seconds
Wait.until(
    "my-deployment to have all replicas available",
    KubeTestConstants.GLOBAL_POLL_INTERVAL_SHORT,   // 5 s
    KubeTestConstants.GLOBAL_TIMEOUT_MEDIUM,         // 5 min
    () -> {
        Deployment d = KubeResourceManager.get().kubeClient().getClient()
            .apps().deployments().inNamespace("my-ns").withName("my-app").get();
        return d != null
            && d.getStatus() != null
            && Integer.valueOf(3).equals(d.getStatus().getAvailableReplicas());
    }
);
```

With an optional `onTimeout` callback that runs before the exception is thrown:

```java
Wait.until(
    "custom-resource to become Ready",
    KubeTestConstants.GLOBAL_POLL_INTERVAL_1_SEC,
    KubeTestConstants.GLOBAL_TIMEOUT,
    () -> myResource.getStatus().getPhase().equals("Ready"),
    () -> LOGGER.error("Timed out. Current status: {}",
        KubeResourceManager.get().kubeClient().getClient()
            .resource(myResource).get().getStatus())
);
```

For non-blocking waits, use `Wait.untilAsync`, which returns a `CompletableFuture<Void>`:

```java
CompletableFuture<Void> future = Wait.untilAsync(
    "pod to be Running",
    KubeTestConstants.GLOBAL_POLL_INTERVAL_1_SEC,
    KubeTestConstants.GLOBAL_TIMEOUT_SHORT,
    () -> "Running".equals(
        KubeResourceManager.get().kubeClient().getClient()
            .pods().inNamespace("my-ns").withName("my-pod").get()
            .getStatus().getPhase())
);
future.get(); // block when you need the result
```

### Exec

`ExecBuilder` runs an OS-level command and returns an `ExecResult` with stdout, stderr, and the exit code.

```java
// Run a command and capture output
ExecResult result = new ExecBuilder()
    .withCommand("kubectl", "get", "pods", "-n", "my-ns", "-o", "name")
    .timeout(30)        // seconds
    .logToOutput(true)  // stream output to the SLF4J logger as it runs
    .throwErrors(true)  // throw KubeClusterException on non-zero exit code
    .exec();

String podList = result.out();
int exitCode   = result.returnCode();
```

Inspect the result without throwing on failure:

```java
ExecResult result = new ExecBuilder()
    .withCommand("helm", "status", "my-release", "-n", "my-ns")
    .timeout(15)
    .throwErrors(false)
    .exec();

if (result.returnCode() != 0) {
    LOGGER.warn("Helm release not found: {}", result.err());
}
```

### PodUtils

```java
// Wait for all pods in a namespace to be Ready or Succeeded
PodUtils.waitForPodsReady("my-ns", true, () ->
    LOGGER.error("Pods still not ready — dumping pod list: {}",
        KubeResourceManager.get().kubeClient().getClient()
            .pods().inNamespace("my-ns").list().getItems())
);

// Wait for exactly 3 pods matching a label selector to be ready
LabelSelector selector = new LabelSelectorBuilder()
    .withMatchLabels(Map.of("app", "my-operator")).build();
PodUtils.waitForPodsReady("my-ns", selector, 3, true, () -> {});

// Capture pod UIDs before an operation, then verify rollout replaced them
Map<String, String> snapshot = PodUtils.podSnapshot("my-ns", selector);
// ... trigger rolling update ...
Wait.until("pods to be replaced", KubeTestConstants.GLOBAL_POLL_INTERVAL_SHORT,
    KubeTestConstants.GLOBAL_TIMEOUT,
    () -> {
        Map<String, String> current = PodUtils.podSnapshot("my-ns", selector);
        return !current.equals(snapshot);
    }
);
```

### JobUtils

```java
// Wait for a Job to succeed (timeout: 5 minutes)
JobUtils.waitForJobSuccess("my-ns", "db-migrate", KubeTestConstants.GLOBAL_TIMEOUT_MEDIUM);

// Wait for a Job to fail (useful for negative testing)
JobUtils.waitForJobFailure("my-ns", "bad-job", KubeTestConstants.GLOBAL_TIMEOUT_SHORT);

// Wait until the Job pod's log contains an expected message
JobUtils.waitForJobContainingLogMessage("my-ns", "init-job", "Migration complete");

// Log the full Job status and pod conditions (useful on test failure)
JobUtils.logCurrentJobStatus("my-ns", "db-migrate");

// Delete a Job and wait for its pod to disappear
JobUtils.deleteJobWithWait("my-ns", "db-migrate");
```

## Constants

Common timeouts and intervals in [KubeTestConstants](src/main/java/io/skodjob/kubetest4j/KubeTestConstants.java):

| Constant | Value |
|----------|-------|
| `GLOBAL_TIMEOUT` | 10 minutes |
| `GLOBAL_TIMEOUT_MEDIUM` | 5 minutes |
| `GLOBAL_TIMEOUT_SHORT` | 3 minutes |
| `GLOBAL_POLL_INTERVAL_SHORT` | 5 seconds |
| `GLOBAL_POLL_INTERVAL_1_SEC` | 1 second |