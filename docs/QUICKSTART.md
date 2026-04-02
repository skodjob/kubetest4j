# Quickstart Guide

Get your first Kubernetes test running in under 5 minutes.

## Prerequisites

- **Java 21+**
- **A Kubernetes cluster** (Kind, Minikube, or a remote cluster)
- **kubectl** configured and pointing to your cluster

Don't have a cluster? Create one with [Kind](https://kind.sigs.k8s.io/):
```bash
kind create cluster
```

## 1. Add Dependencies

Replace `{version}` below with the latest release from
[![Maven Central](https://img.shields.io/maven-central/v/io.skodjob.kubetest4j/kubetest4j)](https://central.sonatype.com/search?q=io.skodjob.kubetest4j).

kubetest4j offers two approaches. Pick the one that fits your needs:

| Approach | Best for | Annotation |
|----------|----------|------------|
| **JUnit Extension** (recommended) | Most users - declarative, batteries-included | `@KubernetesTest` |
| **Core only** | Maximum control, custom setup | `@ResourceManager` |

### Maven

**JUnit Extension (recommended):**
```xml
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>junit-extension</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>kubernetes-resources</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
```

**Core only:**
```xml
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>kubetest4j</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>kubernetes-resources</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
```

### Gradle

**JUnit Extension (recommended):**
```groovy
testImplementation 'io.skodjob.kubetest4j:junit-extension:{version}'
testImplementation 'io.skodjob.kubetest4j:kubernetes-resources:{version}'
```

**Core only:**
```groovy
testImplementation 'io.skodjob.kubetest4j:kubetest4j:{version}'
testImplementation 'io.skodjob.kubetest4j:kubernetes-resources:{version}'
```

> **Snapshot builds:** To use the latest development snapshot, add the Sonatype snapshots repository.
> See [Snapshot Configuration](#snapshot-configuration) at the bottom.

## 2. Write Your First Test

### Option A: Using `@KubernetesTest` (Recommended)

The `@KubernetesTest` annotation gives you automatic namespace management, dependency injection,
log collection, and resource cleanup - all declaratively configured.

```java
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@KubernetesTest(
    namespaces = {"my-test"},
    cleanup = CleanupStrategy.AUTOMATIC
)
class MyFirstKubernetesIT {

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testConfigMapCreation() {
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
                .withName("hello-kubetest4j")
                .withNamespace("my-test")
            .endMetadata()
            .addToData("greeting", "Hello from kubetest4j!")
            .build();

        resourceManager.createResourceWithWait(configMap);

        ConfigMap created = client.getClient().configMaps()
            .inNamespace("my-test")
            .withName("hello-kubetest4j")
            .get();

        assertNotNull(created);
        assertEquals("Hello from kubetest4j!", created.getData().get("greeting"));

        // The namespace and ConfigMap are automatically cleaned up after the test
    }
}
```

### Option B: Using `@ResourceManager` (Core Only)

For maximum control, use the core module directly with `@ResourceManager`.

```java
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.resources.NamespaceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ResourceManager
@TestVisualSeparator
class MyFirstCoreIT {

    static {
        KubeResourceManager.get().setResourceTypes(new NamespaceType());
    }

    @Test
    void testNamespaceCreation() {
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder()
                .withNewMetadata().withName("my-test").endMetadata()
                .build()
        );

        assertNotNull(KubeResourceManager.get().kubeClient()
            .getClient().namespaces().withName("my-test").get());

        // Namespace is automatically cleaned up after the test
    }
}
```

## 3. Run It

Make sure your cluster is accessible:
```bash
kubectl cluster-info
```

Run the test:
```bash
# Maven
./mvnw test -pl <your-module> -Dtest=MyFirstKubernetesIT

# Gradle
./gradlew test --tests MyFirstKubernetesIT
```

That's it! The test creates resources, verifies them, and cleans up automatically.

## What's Next?

Now that you have a working test, explore these features:

| Feature | Documentation |
|---------|--------------|
| Per-test namespace isolation | [junit-extension README](../junit-extension/README.md#methodnamespace--per-test-method-namespaces) |
| Multi-namespace testing | [junit-extension README](../junit-extension/README.md#multi-namespace-testing) |
| Log collection on failure | [junit-extension README](../junit-extension/README.md#comprehensive-log-collection) |
| Multi-cluster testing | [junit-extension README](../junit-extension/README.md#advanced-multi-context-testing) |
| YAML resource injection | [junit-extension README](../junit-extension/README.md#resource-injection-from-yaml) |
| Metrics collection | [metrics-collector README](../metrics-collector/README.md) |
| Log collection utility | [log-collector README](../log-collector/README.md) |
| Full examples | [test-examples module](../test-examples/src/test/java/io/skodjob/kubetest4j/test/integration/) |
| JUnit extension examples | [junit-extension examples](../junit-extension/src/test/java/io/skodjob/kubetest4j/examples/) |

### Adding Log Collection

Collect pod logs automatically when tests fail:
```java
@KubernetesTest(
    namespaces = {"my-test"},
    collectLogs = true,
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE,
    logCollectionPath = "target/test-logs"
)
```

### Adding OpenShift Resources

For OpenShift-specific resources (OLM Subscriptions, CatalogSources, etc.):
```xml
<dependency>
    <groupId>io.skodjob.kubetest4j</groupId>
    <artifactId>openshift-resources</artifactId>
    <version>{version}</version>
    <scope>test</scope>
</dependency>
```

### Environment Variables

Configure cluster access via environment variables instead of kubeconfig:

| Variable | Description |
|----------|-------------|
| `KUBE_URL` | Kubernetes API server URL |
| `KUBE_TOKEN` | Authentication token |
| `KUBECONFIG` | Path to kubeconfig file (overrides URL/token) |
| `CLIENT_TYPE` | `kubectl` or `oc` (default: `kubectl`) |

For multi-cluster testing, append a suffix: `KUBE_URL_STAGING`, `KUBE_TOKEN_STAGING`, etc.

## Snapshot Configuration

To use the latest development snapshot:

**Maven:**
```xml
<repositories>
    <repository>
        <id>central-portal-snapshots</id>
        <name>Central Portal Snapshots</name>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
```

**Gradle:**
```groovy
repositories {
    maven {
        url 'https://central.sonatype.com/repository/maven-snapshots/'
        mavenContent { snapshotsOnly() }
    }
}
```