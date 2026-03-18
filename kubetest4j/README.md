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

### Disabling Automatic Cleanup

```java
@ResourceManager(cleanResources = false)
@TestVisualSeparator
class ManualCleanupTest {
    // Resources will NOT be deleted after tests
    // Use KubeResourceManager.get().deleteResource(...) explicitly
}
```

### Async vs Sync Deletion

By default, resources are deleted asynchronously for faster cleanup. Disable this if you need ordered deletion:

```java
@ResourceManager(asyncDeletion = false)
class OrderedCleanupTest {
    // Resources are deleted synchronously in reverse creation order
}
```

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

Set the client type via `CLIENT_TYPE` environment variable (`kubectl` or `oc`).

## Multi-Context Cluster Support

KubeResourceManager supports testing across multiple Kubernetes clusters simultaneously.

### Option 1: Temporary Context Switching

Use `useContext()` for short operations in a different context:

```java
@ResourceManager
class MultiContextTest {

    @Test
    void testMethod() {
        // Default context
        Namespace ns = new NamespaceBuilder()
            .withNewMetadata().withName("test").endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);

        // Temporarily switch to prod context
        try (var ctx = KubeResourceManager.get().useContext("prod")) {
            Namespace prodNs = new NamespaceBuilder()
                .withNewMetadata().withName("test-prod").endMetadata().build();
            KubeResourceManager.get().createResourceWithWait(prodNs);
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

Configure additional contexts via environment variables:
```bash
# Default context
KUBE_URL=https://api.default:6443
KUBE_TOKEN=default-token

# Additional contexts (use any suffix)
KUBE_URL_PROD=https://api.prod:6443
KUBE_TOKEN_PROD=prod-token
KUBECONFIG_STAGE=/path/to/stage.kubeconfig
```

## ResourceType Registration

ResourceTypes teach `KubeResourceManager` how to handle specific Kubernetes resources - how to create, update, delete, and check readiness.

### Using Built-in ResourceTypes

Add the `kubernetes-resources` (and optionally `openshift-resources`) dependency:

```xml
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>kubernetes-resources</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
```

Then register the types your tests need:

```java
KubeResourceManager.get().setResourceTypes(
    new NamespaceType(),
    new DeploymentType(),
    new ServiceType(),
    new JobType(),
    new NetworkPolicyType()
);
```

If a resource is **not** registered, it is handled as a generic Kubernetes resource with no special readiness check.

### Available Built-in ResourceTypes

**kubernetes-resources:** ClusterRole, ClusterRoleBinding, ConfigMap, CustomResourceDefinition, Deployment, Job, Lease, Namespace, NetworkPolicy, Role, RoleBinding, Secret, Service, ServiceAccount, ValidatingWebhookConfiguration

**openshift-resources:** BuildConfig, CatalogSource, ImageDigestMirrorSet, ImageStream, InstallPlan, OperatorGroup, Subscription

### Implementing Custom ResourceTypes

For custom resources (e.g., your operator's CRDs), implement the `ResourceType<T>` interface:

```java
public class MyCustomResourceType implements ResourceType<MyCustomResource> {

    private final MixedOperation<MyCustomResource, MyCustomResourceList, Resource<MyCustomResource>> client;

    public MyCustomResourceType() {
        this.client = KubeResourceManager.get().kubeClient()
            .getClient().resources(MyCustomResource.class, MyCustomResourceList.class);
    }

    @Override
    public String getKind() {
        return "MyCustomResource";
    }

    @Override
    public Long getTimeoutForResourceReadiness() {
        return KubeTestConstants.GLOBAL_TIMEOUT;
    }

    @Override
    public MixedOperation<?, ?, ?> getClient() {
        return client;
    }

    @Override
    public void create(MyCustomResource resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .resource(resource).create();
    }

    @Override
    public void update(MyCustomResource resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .resource(resource).update();
    }

    @Override
    public void delete(MyCustomResource resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public void replace(MyCustomResource resource, Consumer<MyCustomResource> editor) {
        MyCustomResource toBeUpdated = client
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    @Override
    public boolean isReady(MyCustomResource resource) {
        // Implement your readiness check
        return client.resource(resource).isReady();
    }

    @Override
    public boolean isDeleted(MyCustomResource resource) {
        return resource == null;
    }
}
```

Register it:
```java
KubeResourceManager.get().setResourceTypes(
    new NamespaceType(),
    new MyCustomResourceType()
);
```

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

## Constants

Common timeouts and intervals in [KubeTestConstants](src/main/java/io/skodjob/kubetest4j/KubeTestConstants.java):

| Constant | Value |
|----------|-------|
| `GLOBAL_TIMEOUT` | 10 minutes |
| `GLOBAL_TIMEOUT_MEDIUM` | 5 minutes |
| `GLOBAL_TIMEOUT_SHORT` | 3 minutes |
| `GLOBAL_POLL_INTERVAL_SHORT` | 5 seconds |
| `GLOBAL_POLL_INTERVAL_1_SEC` | 1 second |