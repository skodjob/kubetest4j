# Architecture

## Overview

kubetest4j is a modular Java testing library for Kubernetes and OpenShift.
It provides declarative, annotation-based testing with automatic resource
management, multi-context cluster support, and integrated log/metrics
collection.

## Module Structure

```
kubetest4j (core)
  +-- kubernetes-resources   K8s native resource types (Deployment, Service, etc.)
  +-- openshift-resources    OpenShift/OLM resource types
  +-- junit-extension        JUnit 5 extension with @KubernetesTest annotation
  +-- log-collector          Pod log/description/YAML collection
  +-- metrics-collector      Prometheus metrics scraping from pods
  +-- test-examples          Integration test examples (not published)
```

All modules depend on `kubetest4j` (core). The `test-examples` module
depends on all other modules in test scope.

## Key Abstractions

### ResourceType\<T\>

Core interface for Kubernetes resource lifecycle. Each supported resource
kind (Deployment, Service, ConfigMap, etc.) has an implementation that
provides `create()`, `update()`, `delete()`, `isReady()`, and
`isDeleted()` methods. Implementations live in `kubernetes-resources` and
`openshift-resources` modules.

### KubeResourceManager

Singleton managing resource lifecycle with LIFO-stack cleanup. Tracks
resources per `contextId + testDisplayName` key. Supports three creation
modes:
- `createResourceWithWait` — create and block until ready
- `createResourceWithoutWait` — create without readiness check
- `createResourceAsyncWait` — create and check readiness asynchronously

Uses a `Semaphore` (default 50) to throttle concurrent Kubernetes API
calls and virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`)
for async operations.

### KubeClient / KubeCmdClient

`KubeClient` wraps the Fabric8 `KubernetesClient` for programmatic API
access. `KubeCmdClient` (with `Kubectl` and `Oc` implementations)
provides CLI-based operations for cases where the programmatic API is
insufficient.

### JUnit Extension (@KubernetesTest)

JUnit 5 extension that provides:
- **Namespace management** — `@ClassNamespace` (class-level) and
  `@MethodNamespace` (per-test-method) annotations
- **Dependency injection** — `@InjectKubeClient`, `@InjectCmdKubeClient`,
  `@InjectResourceManager`, `@InjectResource`
- **Log collection** — automatic pod log/description/YAML capture on
  test failure
- **Automatic cleanup** — LIFO-stack resource deletion after each test

## Data Flow

1. Test class annotated with `@KubernetesTest` is discovered by JUnit
2. `KubernetesTestExtension.beforeAll()` creates class-level namespaces,
   initializes `KubeResourceManager`
3. `beforeEach()` creates per-method namespaces, injects fields and
   parameters via `DependencyInjector`
4. Test methods create/modify Kubernetes resources via
   `KubeResourceManager` — each resource is pushed onto the LIFO stack
5. `afterEach()` cleans up resources in reverse order, then deletes
   per-method namespaces
6. `afterAll()` deletes class-level namespaces
7. On test failure, `ExceptionHandlerDelegate` triggers log collection
   before cleanup

## Threading Model

- **Virtual threads** for all async resource operations (readiness
  polling, async creation)
- **Semaphore** (default 50) throttles concurrent Kubernetes API calls
  to avoid overwhelming the API server
- **ConcurrentHashMap** for thread-safe manager instances and resource
  caches
- **CopyOnWriteArrayList** for create/delete callback lists
- **ThreadLocal** for cluster context propagation in multi-context
  scenarios

## Multi-Context Support

The library supports testing against multiple Kubernetes clusters
simultaneously. Each context is identified by a string ID (default:
`"primary"`). Contexts are configured via environment variables:
- `KUBE_URL` / `KUBE_TOKEN` for the default context
- `KUBE_URL_<SUFFIX>` / `KUBE_TOKEN_<SUFFIX>` for additional contexts

Annotations (`@InjectKubeClient`, `@ClassNamespace`, etc.) accept a
`kubeContext` parameter to target a specific cluster.
