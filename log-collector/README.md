# LogCollector

Utility for collecting logs from Pods and their containers, description of Pods, and YAML descriptions for specified
list of resources in desired Namespaces.
The logs are stored in root folder (which user specifies) within the corresponding folder named after the Namespace 
it collects from.

## Prerequisites

- Java 17 and higher
- Access to a Kubernetes cluster
- Kubernetes Client and Command Line Tools (kubectl) setup in your Java environment.

## Installation

In case you are using Maven, you can install the LogCollector dependencies using this code snippet added into `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>io.skodjob.kubetest4j</groupId>
        <artifactId>log-collector</artifactId>
        <version>{version}</version>
    </dependency>
</dependencies>
```

## Usage

### Configuration and initialization

For building the `LogCollector` instance, you can use the `LogCollectorBuilder`.
In the builder, you can specify the list of resources that should be checked during the log collection, or a path to
folder, where all the logs/descriptions should be stored.

The initialization of `LogCollector` object can look like follows:

```java
import io.skodjob.kubetest4j.LogCollector;
import io.skodjob.kubetest4j.LogCollectorBuilder;

LogCollector logCollector = new LogCollectorBuilder()
    .withNamespacedResources("secret", "deployment", "my-custom-resource")
    .withRootFolderPath("/path/to/logs/folder")
    .build();
```

### Collecting logs and descriptions

Each Namespace has its own directory in the root folder, where everything collected is stored to.
For each resource specified in the list, the folder is created and all the YAMLs are stored there - only in case that 
there are resources with the specified resource kind.
Otherwise, the folder is not created.
This is the list of things that are collected by default (without specifying them in the resource list):

- Events of the Namespace - `kubectl get events -n NAMESPACE`
- Pod description - `kubectl describe pod POD_NAME -n NAMESPACE`
- Logs from each Pod and its container(s) - `kubectl logs POD_NAME -c CONTAINER_NAME -n NAMESPACE`

In LogCollector, you can collect the logs and resource descriptions using multiple methods, based on your needs:

#### 1. Collect from single Namespace with specified name

```java
import io.skodjob.kubetest4j.LogCollector;
import io.skodjob.kubetest4j.LogCollectorBuilder;

LogCollector logCollector = new LogCollectorBuilder()
    .withNamespacedResources("secret", "deployment", "my-custom-resource")
    .withRootFolderPath("/path/to/logs/folder")
    .build();

public static void collectFromNamespace() {
    logCollector.collectFromNamespace("my-namespace");
}
```
the logs path will then look like this:
```bash
/path/to/logs/folder
└── my-namespace
    ├── deployment
    ├── events.log
    ├── my-custom-resource
    ├── pods
    └── secret
```

#### 2. Collect from multiple Namespaces with specified names:

```java
import io.skodjob.kubetest4j.LogCollector;
import io.skodjob.kubetest4j.LogCollectorBuilder;

LogCollector logCollector = new LogCollectorBuilder()
    .withNamespacedResources("secret", "deployment", "my-custom-resource")
    .withRootFolderPath("/path/to/logs/folder")
    .build();

public static void collectFromNamespaces() {
    logCollector.collectFromNamespaces("my-namespace", "my-namespace2");
}
```
the logs path will then look like this:
```bash
/path/to/logs/folder
├── my-namespace
│   ├── deployment
│   ├── events.log
│   ├── pods
│   └── secret
└── my-namespace2
    ├── deployment
    ├── events.log
    ├── pods
    └── secret

```
#### 3. Collect from Namespaces matching labels

Let's assume that `my-namespace` in this example is the one containing our specified labels and we have other Namespaces
that don't have this label.
Then to collect logs and YAMLs from those Namespaces, that are matching the labels, you can do:

```java
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.skodjob.kubetest4j.LogCollector;
import io.skodjob.kubetest4j.LogCollectorBuilder;

LogCollector logCollector = new LogCollectorBuilder()
    .withNamespacedResources("secret", "deployment", "my-custom-resource")
    .withRootFolderPath("/path/to/logs/folder")
    .build();

public static void collectFromNamespaces() {
    logCollector.collectFromNamespacesWithLabels(new LabelSelectorBuilder()
            .withMatchLabels(Map.of("my-label", "my-value"))
    );
}
```

#### 4. Collect cluster wide resource lists

Collect cluster wide resource lists yaml like `nodes`, `pvs`:

```java
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.skodjob.kubetest4j.LogCollector;
import io.skodjob.kubetest4j.LogCollectorBuilder;

LogCollector logCollector = new LogCollectorBuilder()
    .withClusterWideResources("nodes", "pvs")
    .withRootFolderPath("/path/to/logs/folder")
    .build();

public static void collectClusterWideRes() {
    logCollector.collectClusterWideResources();
}
```
the logs path will then look like this:
```bash
/path/to/logs/folder
└── cluster-wide-resources
    ├── nodes
    │   ├── node1.yaml
    │   └── node2.yaml
    └── pvs
        ├── pv1.yaml
        └── pv2.yaml
```
The tree path will look similarly to above examples, there will be folders for Namespaces matching the specified labels.

### Specifying additional folder path

In case that you would like to collect the logs to additional sub-directories of your root folder, the `LogCollector` contains
methods for specifying the additional path for each of the three methods for log collection (mentioned above):

- `collectFromNamespacesWithLabelsToFolder(LabelSelector labelSelector, String folderPath)`
- `collectFromNamespacesToFolder(List<String> namespacesNames, String folderPath)`
- `collectFromNamespaceToFolder(String namespaceName, String folderPath)`

The usage is same as for the methods that don't contain the `folderPath` parameter.
The `folderPath` is appended after the `rootPath` you specified during the `LogCollector` initialization.

Example:
```
rootPath: /tmp/logs
folderPath: run1
namespaceName: my-namespace

final full path for log collection: /tmp/logs/run1/my-namespace/
```

### Collecting logs from previously failed Pod containers

When a Pod (and its containers) is caught in a crash loop, 
it's useful to collect logs from the previous instance of the Pod.
In some cases, logs are only captured during container startup, 
so retrieving logs from the previous instance can help identify the exact error that caused the Pod to fail.

This feature can be enabled using `withCollectPreviousLogs()` option of the `LogCollectorBuilder`:

```java 
    LogCollector localLogCollector = new LogCollectorBuilder()
        .withCollectPreviousLogs()
        .build();
```

By default, this feature is **disabled**.

### Using MustGather with LogCollector

In case you want to use `MustGather` for automatically call your log collector in case of test failure or test lifecycle failure you can use annotation `@MustGather` like in following example.

```java
import io.skodjob.kubetest4j.annotations.MustGather;
import org.junit.jupiter.api.Test;

@MustGather(config = MyMustGatherSupplierImplementation.class)
class Tests {
    @Test
    void someTest() {

    }
}
```
where `MyMustGatherSupplierImplementation` is implementation of [MustGatherSupplier](src/main/java/io/skodjob/kubetest4j/interfaces/MustGatherSupplier.java) interface.

```java
import io.skodjob.kubetest4j.LogCollector;
import io.skodjob.kubetest4j.interfaces.MustGatherSupplier;

public class MyMustGatherSupplierImplementation implements MustGatherSupplier {
    @Override
    public void saveKubernetesState(ExtensionContext context) {
        LogCollector logCollector = new LogCollectorBuilder()
            .withNamespacedResources(
                "configmap",
                "secret"
            )
            .withClusterWideResources("node")
            .withKubeClient(KubeResourceManager.get().kubeClient())
            .withKubeCmdClient(KubeResourceManager.get().kubeCmdClient())
            .withRootFolderPath("/some/path/to/store/logs")
            .build();
        logCollector.collectFromNamespace("default");
        logCollector.collectClusterWideResources();
    }
}
```