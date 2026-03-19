# Comparison with Alternatives

How kubetest4j compares to other Java-based Kubernetes testing approaches.

## Overview

| Feature | kubetest4j | Fabric8 (direct) | Testcontainers K3s | Arquillian Cube | JKube |
|---------|-----------|------------------|-------------------|----------------|-------|
| **Real cluster testing** | Yes | Yes | Embedded (K3s) | Yes | Yes |
| **Multi-cluster support** | Yes | Manual | No | No | No |
| **Automatic resource cleanup** | Yes | Manual | N/A | Yes | No |
| **Log collection** | Yes | Manual | No | No | No |
| **Metrics collection** | Yes | Manual | No | No | No |
| **Annotation-driven** | Yes | No | No | Yes | No |
| **Dependency injection** | Yes | No | No | Yes | No |
| **OpenShift/OLM support** | Yes | Yes | No | Yes | No |
| **Namespace management** | Yes | Manual | N/A | Yes | No |
| **YAML resource loading** | Yes | Manual | No | Yes | No |
| **Readiness checks** | Yes | Manual | N/A | Yes | No |
| **Minimum Java version** | 21 | 11 | 11 | 8 | 11 |
| **Actively maintained** | Yes | Yes | Yes | Limited | Yes |

## Detailed Comparison

### Testcontainers with K3s

[Testcontainers](https://java.testcontainers.org/) runs a K3s cluster inside a Docker container. Good for unit-level integration tests, but limited for real E2E scenarios.

**Choose Testcontainers when:**
- You need a self-contained test that doesn't require an external cluster
- Tests are lightweight and don't need multi-node behavior
- CI doesn't have access to a Kubernetes cluster

**Choose kubetest4j when:**
- You test against real clusters (staging, production-like environments)
- You need multi-cluster testing
- You test operator behavior, OLM integration, or OpenShift-specific features
- You need log/metrics collection from running pods
- Your tests interact with existing cluster infrastructure

### Fabric8 Kubernetes Client

The [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client) is the standard Java client for Kubernetes APIs. It can be used directly for E2E and integration testing, and is in fact the foundation that kubetest4j is built on (v7.x). Fabric8 also provides a JUnit extension with `@EnableKubernetesMockClient` for unit testing with a mock API server.

When using Fabric8 directly for E2E tests, you need to handle resource cleanup, namespace management, client lifecycle, and test infrastructure (log collection, readiness checks, etc.) yourself.

**Use Fabric8 directly when:**
- You have simple test scenarios with few resources
- You want full control without any framework overhead
- You're unit testing with the mock client

**Add kubetest4j on top when:**
- You want automatic resource lifecycle management and cleanup (even on test failure)
- You need multi-namespace and multi-context testing
- You want built-in log and metrics collection
- You need readiness checks and resource type abstractions
- You want declarative test configuration via annotations

> kubetest4j is built *on top of* Fabric8 - it's not a replacement. You still use the Fabric8 API for resource construction and cluster interactions. kubetest4j adds the testing framework layer (resource tracking, cleanup, DI, log collection) that you'd otherwise write yourself.

### Arquillian Cube

[Arquillian Cube](https://arquillian.org/arquillian-cube/) is a mature framework for container and Kubernetes testing. However, development has slowed significantly.

**Choose Arquillian Cube when:**
- You already use Arquillian for other testing needs
- You need Java 8 compatibility

**Choose kubetest4j when:**
- You want an actively maintained framework
- You prefer annotation-based configuration over XML
- You need multi-cluster support
- You want simpler setup without the Arquillian ecosystem overhead

### JKube

[Eclipse JKube](https://eclipse.dev/jkube/) focuses on building and deploying Java applications to Kubernetes. It's not primarily a testing framework.

**Choose JKube when:**
- You need to build container images and deploy apps as part of the Maven/Gradle lifecycle
- Your focus is on deployment, not testing

**Choose kubetest4j when:**
- Your focus is on testing Kubernetes resources, operators, and applications
- You need test lifecycle management (setup, assertions, teardown)
- You want declarative test configuration

## Migration Guide

### From raw Fabric8 client tests

If you're writing tests with plain Fabric8 `KubernetesClient`:

**Before:**
```java
class MyTest {
    KubernetesClient client;

    @BeforeEach
    void setup() {
        client = new KubernetesClientBuilder().build();
        client.namespaces().resource(new NamespaceBuilder()
            .withNewMetadata().withName("test").endMetadata().build()).create();
    }

    @AfterEach
    void cleanup() {
        try {
            client.namespaces().withName("test").delete();
        } finally {
            client.close();
        }
    }

    @Test
    void testSomething() {
        // test code
    }
}
```

**After (with kubetest4j):**
```java
@KubernetesTest(namespaces = {"test"})
class MyTest {

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testSomething() {
        // test code - namespace created automatically, cleanup handled
    }
}
```

Benefits: no manual setup/teardown, automatic cleanup even on failure, log collection, multi-namespace support.

## Summary

kubetest4j is designed for **real-cluster E2E and integration testing** of Kubernetes-native applications and operators. It fills the gap between low-level client libraries (Fabric8) and container-based testing (Testcontainers) by providing a declarative, annotation-driven framework with production-grade features like multi-cluster support, log collection, and automatic resource lifecycle management.