# Resource Batches and LIFO Cleanup

`KubeResourceManager` organises every resource it tracks into **batches**. A batch is a group of resources that were created together in a single call. During cleanup, batches are deleted in reverse creation order (LIFO — last in, first out), and all resources within a single batch are deleted concurrently.

This model mirrors how real Kubernetes deployments work: infrastructure is created first (namespaces, RBAC), then applications (Deployments, Services). On teardown, applications must be deleted before the namespaces they live in.

---

## How Batches Are Formed

### Implicit batch — single call with multiple resources

Every call to `createResourceWithWait(T...)` or `createResourceWithoutWait(T...)` that receives two or more resources automatically forms one batch:

```java
// Batch 1: namespace alone
KubeResourceManager.get().createResourceWithWait(namespace);

// Batch 2: ConfigMap + ServiceAccount together
KubeResourceManager.get().createResourceWithWait(configMap, serviceAccount);
```

On cleanup, batch 2 is deleted first (configMap and serviceAccount, concurrently), then batch 1 (namespace).

### Explicit batch — group multiple create calls

Use `openBatch()` to group several independent `create*` calls into one batch. All resources added inside the `try` block are treated as a single unit during deletion:

```java
// Batch 1: namespace
KubeResourceManager.get().createResourceWithWait(namespace);

// Batch 2: multiple calls, but one batch
try (AutoCloseable ignored = KubeResourceManager.get().openBatch()) {
    KubeResourceManager.get().createResourceWithWait(deployment);
    KubeResourceManager.get().createResourceWithWait(service);
    KubeResourceManager.get().createResourceWithWait(configMap);
}
// deployment, service, and configMap are all in batch 2 and deleted together before namespace
```

`openBatch()` returns an `AutoCloseable`, so standard `try`-with-resources closes the batch automatically.

---

## LIFO Deletion Order in Practice

Consider this setup:

```
Batch 1 (created first):  Namespace "my-app"
Batch 2 (created second): Deployment "my-app/backend", Service "my-app/backend-svc"
Batch 3 (created last):   Secret "my-app/tls-secret", ConfigMap "my-app/app-config"
```

Cleanup order:

1. Batch 3 deleted — Secret and ConfigMap deleted **concurrently**
2. Batch 2 deleted — Deployment and Service deleted **concurrently**
3. Batch 1 deleted — Namespace deleted

This ensures resources are removed before the namespaces that contain them.

---

## Inspecting Tracked Resources

```java
// All resources tracked for the current test
List<HasMetadata> resources = KubeResourceManager.get().getCurrentResources();

// Number of tracked resources
int count = resources.size();
```

---

## Triggered Cleanup

### Automatic cleanup (via `@KubernetesTest` or `@ResourceManager`)

When you annotate a test class with `@KubernetesTest` (cleanup default is `AUTOMATIC`) or `@ResourceManager`, the framework calls `deleteResources()` after each test method. You do not need to call it manually.

### Manual cleanup

If you set `cleanup = CleanupStrategy.MANUAL` on `@KubernetesTest`, or use `@ResourceManager(cleanResources = false)`, you are responsible for calling:

```java
KubeResourceManager.get().deleteResources();
```

Call it in an `@AfterEach` method or wherever it fits your test lifecycle.

### Deleting a single resource

```java
KubeResourceManager.get().deleteResourceWithWait(myDeployment);
```

This removes the resource from the tracker stack and waits for deletion to complete.

---

## Async vs Synchronous Deletion

By default, resources within a batch are deleted asynchronously (concurrently). This is faster but may produce interleaved log output.

Disable async deletion for ordered, sequential cleanup:

```java
@ResourceManager(asyncDeletion = false)
class OrderedCleanupTest { ... }
```

With `@KubernetesTest`, asynchronous deletion is always enabled. Use `@ResourceManager` directly if you need synchronous deletion.

---

## Callbacks on Delete

Register a callback to run after every successful resource deletion:

```java
KubeResourceManager.get().addDeleteCallback(resource -> {
    LOGGER.info("Deleted {} {}", resource.getKind(), resource.getMetadata().getName());
});
```

Callbacks fire after the delete API call succeeds. They do not fire for failed deletions.

See also the create callback counterpart:

```java
KubeResourceManager.get().addCreateCallback(resource -> {
    if ("Namespace".equals(resource.getKind())) {
        KubeUtils.labelNamespace(resource.getMetadata().getName(), "managed-by", "kubetest4j");
    }
});
```

---

## Full Example

```java
@ResourceManager
@TestVisualSeparator
class ResourceLifecycleTest {

    static {
        KubeResourceManager.get().setResourceTypes(
            new NamespaceType(),
            new DeploymentType(),
            new ServiceType()
        );
    }

    @Test
    void testBatchedLifecycle() {
        Namespace ns = new NamespaceBuilder()
            .withNewMetadata().withName("lifecycle-test").endMetadata()
            .build();

        Deployment deploy = new DeploymentBuilder()
            .withNewMetadata().withName("my-app").withNamespace("lifecycle-test").endMetadata()
            // ... spec ...
            .build();

        Service svc = new ServiceBuilder()
            .withNewMetadata().withName("my-app").withNamespace("lifecycle-test").endMetadata()
            // ... spec ...
            .build();

        // Batch 1 — namespace (deleted last)
        KubeResourceManager.get().createResourceWithWait(ns);

        // Batch 2 — application resources (deleted first)
        KubeResourceManager.get().createResourceWithWait(deploy, svc);

        // ... assertions ...

        // Automatic cleanup after the test: batch 2 then batch 1
    }
}
```

---

## Related Documentation

- [Core Module](../kubetest4j/README.md) — `KubeResourceManager` API reference
- [Resource Types](RESOURCE-TYPES.md) — Built-in and custom `ResourceType` implementations
- [JUnit Extension](../junit-extension/README.md) — Declarative cleanup via `@KubernetesTest`
