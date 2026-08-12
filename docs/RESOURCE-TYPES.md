# Resource Types

A `ResourceType<T>` implementation teaches `KubeResourceManager` how to manage a specific Kubernetes resource kind — how to create it, update it, delete it, check whether it is ready, and check whether it has been fully deleted.

Resource types are optional but recommended for resources that have meaningful readiness semantics (for example, Deployments roll out replicas; Jobs succeed or fail; Namespaces become Active). Without a registered type, a resource is treated as a generic object: it is created and deleted via the Fabric8 generic API, and readiness checks are skipped.

## Registration

Register resource types once before any test creates resources. With the JUnit extension the declarative way is through `@KubernetesTest`:

```java
@KubernetesTest(resourceTypes = {NamespaceType.class, DeploymentType.class, JobType.class})
class MyTest { ... }
```

With the core-only `@ResourceManager` annotation, use a static initialiser:

```java
@ResourceManager
class MyTest {
    static {
        KubeResourceManager.get().setResourceTypes(
            new NamespaceType(),
            new DeploymentType(),
            new JobType()
        );
    }
}
```

`setResourceTypes(...)` replaces the global list for all contexts. Call it once from a shared abstract base class or static block.

---

## Built-in: kubernetes-resources

Add the dependency to get all standard Kubernetes resource types:

**Maven:**
```xml
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>kubernetes-resources</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
```

**Gradle:**
```groovy
testImplementation 'io.skodjob.kubetest4j:kubernetes-resources:{version}'
```

### Available Types

| Class | Kind | Readiness Semantics |
|-------|------|---------------------|
| `ClusterRoleType` | `ClusterRole` | Always ready (immutable after creation) |
| `ClusterRoleBindingType` | `ClusterRoleBinding` | Always ready |
| `ConfigMapType` | `ConfigMap` | Always ready |
| `CronJobType` | `CronJob` | Always ready (scheduled, not running) |
| `CustomResourceDefinitionType` | `CustomResourceDefinition` | Waits for `Established` condition |
| `DaemonSetType` | `DaemonSet` | Waits for all desired pods to be ready |
| `DeploymentType` | `Deployment` | Waits for all replicas to be available |
| `HorizontalPodAutoscalerType` | `HorizontalPodAutoscaler` | Always ready |
| `IngressType` | `Ingress` | Always ready (no pod readiness concept) |
| `JobType` | `Job` | Waits for job to succeed or fail |
| `LeaseType` | `Lease` | Always ready |
| `NamespaceType` | `Namespace` | Waits for `Active` phase |
| `NetworkPolicyType` | `NetworkPolicy` | Always ready |
| `PersistentVolumeClaimType` | `PersistentVolumeClaim` | Waits for `Bound` phase |
| `RoleType` | `Role` | Always ready |
| `RoleBindingType` | `RoleBinding` | Always ready |
| `SecretType` | `Secret` | Always ready |
| `ServiceType` | `Service` | Always ready |
| `ServiceAccountType` | `ServiceAccount` | Always ready |
| `StatefulSetType` | `StatefulSet` | Waits for all replicas to be ready |
| `ValidatingWebhookConfigurationType` | `ValidatingWebhookConfiguration` | Always ready |

### Example: Only register what you need

```java
KubeResourceManager.get().setResourceTypes(
    new NamespaceType(),
    new DeploymentType(),
    new ServiceType(),
    new ConfigMapType()
);
```

You do not need to register every type. Types not registered fall back to the generic Fabric8 create/delete path with no readiness poll.

---

## Built-in: openshift-resources

For OpenShift and OLM-specific resources:

**Maven:**
```xml
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>openshift-resources</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
```

### Available Types

| Class | Kind | Readiness Semantics |
|-------|------|---------------------|
| `BuildConfigType` | `BuildConfig` | Always ready |
| `CatalogSourceType` | `CatalogSource` | Waits for `READY` state |
| `ImageDigestMirrorSetType` | `ImageDigestMirrorSet` | Always ready |
| `ImageStreamType` | `ImageStream` | Always ready |
| `InstallPlanType` | `InstallPlan` | Waits for `Complete` phase |
| `OperatorGroupType` | `OperatorGroup` | Always ready |
| `SubscriptionType` | `Subscription` | Waits for CSV to be installed |

---

## Readiness Timeout

Each type has a configurable readiness timeout. The default is `KubeTestConstants.GLOBAL_TIMEOUT_MEDIUM` (5 minutes). Override it by overriding `getTimeoutForResourceReadiness()`:

```java
@Override
public Long getTimeoutForResourceReadiness() {
    return KubeTestConstants.GLOBAL_TIMEOUT; // 10 minutes
}
```

---

## Implementing a Custom ResourceType

For operator CRDs and custom resources, implement `ResourceType<T>`:

```java
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.interfaces.ResourceType;
import io.skodjob.kubetest4j.resources.KubeResourceManager;

import java.util.function.Consumer;

public class KafkaType implements ResourceType<Kafka> {

    private final MixedOperation<Kafka, KafkaList, Resource<Kafka>> client;

    public KafkaType() {
        this.client = KubeResourceManager.get()
            .kubeClient().getClient()
            .resources(Kafka.class, KafkaList.class);
    }

    @Override
    public String getKind() {
        return "Kafka";
    }

    @Override
    public Long getTimeoutForResourceReadiness() {
        return KubeTestConstants.GLOBAL_TIMEOUT; // 10 minutes for Kafka clusters
    }

    @Override
    public NonNamespaceOperation<?, ?, ?> getClient() {
        return client;
    }

    @Override
    public void create(Kafka resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .resource(resource).create();
    }

    @Override
    public void update(Kafka resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .resource(resource).update();
    }

    @Override
    public void delete(Kafka resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public void replace(Kafka resource, Consumer<Kafka> editor) {
        Kafka current = client
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(current);
        update(current);
    }

    @Override
    public boolean isReady(Kafka resource) {
        Kafka current = client
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        if (current == null) {
            return false;
        }
        // Check for a "Ready" condition in the status
        return current.getStatus() != null
            && current.getStatus().getConditions() != null
            && current.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType())
                    && "True".equals(c.getStatus()));
    }

    @Override
    public boolean isDeleted(Kafka resource) {
        return client
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get() == null;
    }
}
```

Register it alongside built-in types:

```java
KubeResourceManager.get().setResourceTypes(
    new NamespaceType(),
    new DeploymentType(),
    new KafkaType()
);
```

### Implementation checklist

- `getKind()` — return the exact value of the resource's `kind` field (case-sensitive, e.g. `"Kafka"`, not `"kafka"`)
- `create()` / `update()` / `delete()` — delegate to the Fabric8 typed client; do not perform readiness polling here
- `replace()` — fetch the current resource, apply the editor, then call `update()`; never modify the passed-in resource directly
- `isReady()` — fetch fresh state from the API; return `true` only when the resource is fully operational
- `isDeleted()` — return `true` when the API returns `null` for the resource
- `getTimeoutForResourceReadiness()` — return a timeout appropriate for the resource type; CRDs and heavyweight operators need longer timeouts

### Thread safety

`KubeResourceManager` can call `isReady()` from virtual threads concurrently. Make your `ResourceType` stateless or use thread-safe access patterns.

---

## Using replaceResourceWithRetries

When updating resources that may receive concurrent modifications (for example, operator-managed resources that re-create quickly), use the retry-aware method:

```java
KubeResourceManager.get().replaceResourceWithRetries(myKafka, kafka -> {
    kafka.getSpec().setReplicas(3);
});
```

This method retries on HTTP 409 Conflict errors, fetching fresh resource state before each retry. The underlying `replace()` on your `ResourceType` is called on each attempt.

---

## Related Documentation

- [Core Module](../kubetest4j/README.md) — `KubeResourceManager`, clients, and utility reference
- [JUnit Extension](../junit-extension/README.md) — Declarative resource type registration via `@KubernetesTest`
- [Resource Batch](RESOURCE-BATCH.md) — Batch grouping and LIFO cleanup ordering
