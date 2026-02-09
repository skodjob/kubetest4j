# Kubetest4j
Library for easy testing of Kubernetes deployments and operators using Fabric8 API.

[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/skodjob/kubetest4j/badge)](https://scorecard.dev/viewer/?uri=github.com/skodjob/kubetest4j)
[![Build](https://github.com/skodjob/kubetest4j/actions/workflows/build.yaml/badge.svg?branch=main)](https://github.com/skodjob/kubetest4j/actions/workflows/build.yaml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=skodjob_kubetest4j&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=skodjob_kubetest4j)
[![GitHub Release](https://img.shields.io/github/v/release/skodjob/kubetest4j)](https://github.com/skodjob/kubetest4j/releases)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.skodjob/kubetest4j)](Ihttps://central.sonatype.com/search?q=io.skodjob.kubetest4j)

## Provided functionality
### Kubernetes resource manager
[KubeResourceManager](kubetest4j-common/src/main/java/io/skodjob/kubetest4j/resources/KubeResourceManager.java) provides management of resources created during test phases.
Every Kubernetes resource created by `KubeResourceManager` is automatically deleted at the end of the test, whether the test passed or failed.
So the Kubernetes environment is clean before and after every test run and user do not need to handle it.
Working with Kubernetes resources using `KubeResourceManager` also provides proper wait for resource readiness.

### Fabric8 Kubernetes client and CMD client
Instance of `KubeResourceManager` contains accessible fabric8 kubernetes client and kubernetes cmd client.
These clients are initialized and connected to the test cluster based on the configuration provided by the env file, env variables, or kubeconfig.

### Test visual separation
For better clarity regarding the test logs, `TestFrame` library provides ASCII vial separation of tests and test classes.

### Metrics Collector
The `MetricsCollector` is designed to facilitate the collection of metrics from Kubernetes pods. 
It integrates seamlessly with Kubernetes environments to gather and process metrics data efficiently. 
For more detailed documentation, see the MetricsCollector [README](kubetest4j-metrics-collector/README.md).

### Log Collector
`LogCollector` is utility for collecting logs from the Pods (and their containers), descriptions of Pods, and YAML
descriptions of resources specified by users, collected from the desired Namespaces.
To Log Collector's [README](kubetest4j-log-collector/README.md) contains detailed documentation about this component,
together with the usage and installation.

### Utils
`TestFrame` contains also tweaks and [utils](kubetest4j-common/src/main/java/io/skodjob/kubetest4j/utils) for better working with kubernetes cluster.

## Usage
### Include dependency to your maven test project
```xml
<dependency>
    <groupId>io.skodjob</groupId>
    <artifactId>kubetest4j-common</artifactId>
    <version>1.0.0</version>
</dependency>
```
### Or use snapshot releases
```xml
<repositories>
  <repository>
    <name>Central Portal Snapshots</name>
    <id>central-portal-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
      <enabled>false</enabled>
    </releases>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>

...

<dependency>
    <groupId>io.skodjob</groupId>
    <artifactId>kubetest4j-common</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

### Use annotations for working with `KubeResourceManager` or other provided functionality
```java
//...
@ResourceManager
@TestVisualSeparator
class Test {
    //...
}
//...
```
### To disable `KubeResourceManager` clean resources functionality
```java
//...
@ResourceManager(cleanResources = false)
@TestVisualSeparator
class Test {
    //...
}
//...
```
### Work with `KubeResourceManager` and clients
```java
//...
@ResourceManager
class Test {
    @Test
    void testMethod() {
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("test").endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeCmdClient().get("namespace", "test"));

        ...

        KubeResourceManager.get().deleteResource(ns);
    }
}
//...
```
### Switch between cluster contexts
KubeResourceManager always uses context `default`, if you want to use different configured kube cluster context use followed syntax.
```java
//...
@ResourceManager
class Test {
    @Test
    void testMethod() {
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("test").endMetadata().build();
        KubeResourceManager.get().createResourceWithWait(ns);
        assertNotNull(KubeResourceManager.get().kubeCmdClient().withTimeout(2000).get("namespace", "test"));

        try (var ctx = KubeResourceManager.get().useContext("prod")) {
            Namespace ns = new NamespaceBuilder().withNewMetadata().withName("test-prod").endMetadata().build();
            KubeResourceManager.get().createResourceWithWait(ns);
            assertNotNull(KubeResourceManager.get().kubeCmdClient().get("namespace", "test-prod"));
        }
    }
}
//...
```
### Register `ResourceType` or `NamespacedResourceType` classes into `KubeResourceManager`
Include `kubetest4j-kubernetes` or for openshift specific resources include also `kubetest4j-openshift` package.
```xml
...
<dependency>
    <groupId>io.skodjob</groupId>
    <artifactId>kubetest4j-common</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.skodjob</groupId>
    <artifactId>kubetest4j-kubernetes</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.skodjob</groupId>
    <artifactId>kubetest4j-openshift</artifactId>
    <version>1.0.0</version>
</dependency>
...
```
Then register resources which will be handled specifically by KubeResourceManager.
If resource is not registered then it is handled as common kubernetes resource with no special readiness check.
If you have any own resource for your operator then you can implement `ResourceType` interface with your specific readiness and handling.
```java
KubeResourceManager.get().setResourceTypes(
        new NamespaceType(),
        new JobType(),
        new NetworkPolicyType(),
        new SubsciptionType(),
        new OperatorGroupType()
);
```

## Examples
Examples are stored in [kubetest4j-test-examples](kubetest4j-test-examples/src/test/java/io/skodjob/kubetest4j/test/integration) module.

## Config environment variables
* **ENV_FILE** - path to YAML file with env variables values
* **KUBE_TOKEN** - token of Kube access (use instead of username/password)
* **KUBE_URL** - URL of the cluster (API URL)
* **KUBECONFIG** - Path to kubeconfig (Overwrites token and url)
* **CLIENT_TYPE** - Switch between `kubectl` or `oc` cmd client
* **KUBE_TOKEN_XXX** - token of Kube access (additional cluster use suffix like PROD, DEV, TEST)
* **KUBE_URL_XXX** - URL of the cluster (additional cluster use suffix like PROD, DEV, TEST)
* **KUBECONFIG_XXX** - Path to kubeconfig (additional cluster use suffix like PROD, DEV, TEST)

## Adopters
* [opendatahub.io](https://github.com/opendatahub-io/opendatahub-operator) operator test suite - [odh-e2e](https://github.com/skodjob/odh-e2e)
* [strimzi.io](https://github.com/strimzi/strimzi-kafka-operator) Strimzi Kafka operator - [e2e](https://github.com/strimzi/strimzi-kafka-operator/tree/main/systemtest)
* [strimzi.io](https://github.com/strimzi/kafka-access-operator) Kafka access operator - [e2e](https://github.com/strimzi/kafka-access-operator/tree/main/systemtest)
* [debezium.io](https://github.com/debezium/debezium-operator) Debezium Operator - [e2e](https://github.com/debezium/debezium-operator/tree/main/systemtests)
* [streamshub](https://github.com/streamshub) Streams E2E - [e2e](https://github.com/streamshub/streams-e2e)

## Maintainers
* [David Kornel](https://github.com/kornys) <kornys@outlook.com>
* [Lukas Kral](https://github.com/im-konge) <lukywill16@gmail.com>
* [Jakub Stejskal](https://github.com/Frawless) <xstejs24@gmail.com>
