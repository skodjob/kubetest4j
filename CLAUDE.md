# CLAUDE.md - AI Agent Guide for kubetest4j

## Project Overview

**kubetest4j** is a Java testing library for Kubernetes and OpenShift clusters. It provides declarative, annotation-based testing with automatic resource management, multi-context cluster support, and integrated log/metrics collection.

- **Organization:** skodjob
- **License:** Apache 2.0
- **Java:** 21+
- **Repository:** https://github.com/skodjob/kubetest4j

## Build & Test Commands

```bash
./mvnw install                        # Build all modules
./mvnw install -pl kubetest4j         # Build single module
./mvnw spotbugs:spotbugs              # Static analysis
./mvnw -P integration install         # Run integration tests (requires cluster)
./mvnw test -pl <module>              # Unit tests for a module
```

CI runs builds on Java 21 and 25. Checkstyle and SpotBugs are enforced.

## Module Structure

```
kubetest4j/                  # Core library - clients, resource management, utilities
kubernetes-resources/        # ResourceType implementations for K8s native resources
openshift-resources/         # ResourceType implementations for OpenShift/OLM resources
junit-extension/             # JUnit 5 extension with @KubernetesTest annotation
log-collector/               # Pod log/description/YAML collection utility
metrics-collector/           # Prometheus metrics scraping from pods
test-examples/               # Integration test examples (reference for patterns)
```

## Architecture & Key Abstractions

### ResourceType<T> (`kubetest4j/.../interfaces/ResourceType.java`)
Core interface for Kubernetes resource lifecycle. Every resource type (Deployment, Service, Pod, etc.) implements this with:
- `getKind()`, `create(T)`, `update(T)`, `delete(T)`, `replace(T, Consumer<T>)`
- `isReady(T)`, `isDeleted(T)` - readiness/deletion checks
- `getTimeoutForResourceReadiness()` - configurable timeout

**To add a new resource type:** Create a class implementing `ResourceType<T>` in `kubernetes-resources` (or `openshift-resources`), wrapping the Fabric8 client API. Follow the pattern in `DeploymentType.java`.

### KubeResourceManager (`kubetest4j/.../resources/KubeResourceManager.java`)
Singleton factory managing resource lifecycle and cleanup:
- `KubeResourceManager.get()` - default context instance
- `KubeResourceManager.getForContext("name")` - per-context instance
- `createResourceWithWait(T)` / `createResourceWithoutWait(T)` - create resources
- Tracks all created resources and cleans up automatically after tests

**`@ResourceManager` annotation** (`kubetest4j/.../annotations/ResourceManager.java`) - Class-level annotation that registers JUnit extensions for automatic resource setup and cleanup. Options:
- `cleanResources()` (default `true`) - enable/disable automatic deletion of created resources after tests
- `asyncDeletion()` (default `true`) - enable/disable async resource deletion for faster cleanup

This is the **standalone** (non-`@KubernetesTest`) way to get automatic resource cleanup. Apply it to test classes that use `KubeResourceManager` directly without the full `@KubernetesTest` extension.

### KubeClient (`kubetest4j/.../clients/KubeClient.java`)
Wrapper around Fabric8 `KubernetesClient`:
- `new KubeClient()` - auto-configure from env vars
- `new KubeClient(kubeconfigPath)` - from kubeconfig file
- `KubeClient.fromUrlAndToken(url, token)` - from API URL + token

### KubeCmdClient / Kubectl / Oc (`kubetest4j/.../clients/cmdClient/`)
Abstraction for kubectl/oc CLI operations with fluent API.

### JUnit Extension (`junit-extension/.../KubernetesTestExtension.java`)
`@KubernetesTest` annotation configures:
- `namespaces` - auto-create test namespaces
- `cleanup` - AUTOMATIC or MANUAL
- `collectLogs` / `logCollectionStrategy` - log collection on failure
- `additionalKubeContexts` - multi-cluster testing
- `storeYaml` / `yamlStorePath` - persist created resource YAMLs

Injection annotations: `@InjectKubeClient`, `@InjectCmdKubeClient`, `@InjectResourceManager`, `@InjectNamespaces`, `@InjectNamespace(name="...")`, `@InjectResource(value="...", waitForReady=true)`. All support `kubeContext` parameter for multi-context.

### Wait Utility (`kubetest4j/.../wait/Wait.java`)
Polling-based wait: `Wait.until(description, pollMs, timeoutMs, BooleanSupplier)`

## Environment Variables

```
KUBE_URL, KUBE_TOKEN, KUBECONFIG                # Default context
KUBE_URL_<SUFFIX>, KUBE_TOKEN_<SUFFIX>           # Additional contexts
KUBECONFIG_<SUFFIX>                              # Kubeconfig per context
CLIENT_TYPE=kubectl|oc                           # CLI client type (default: kubectl)
IP_FAMILY=ipv4|ipv6|dual                         # IP family (default: ipv4)
```

## Coding Conventions

- **Package:** `io.skodjob.kubetest4j` (all modules)
- **Test naming:** `*Test.java` for unit tests (Surefire), `*IT.java` for integration tests (Failsafe)
- **Code quality:** Checkstyle + SpotBugs enforced in CI; fix violations before committing
- **Patterns:** Builder pattern for configuration objects, Singleton for managers, fluent APIs for clients
- **Logging:** SLF4J with Log4J2 backend
- **Dependencies:** Fabric8 Kubernetes Client 7.x, JUnit Jupiter 6.x

### Required File Header (Checkstyle enforced)
Every `.java` file MUST start with this exact header:
```java
/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
```

### Checkstyle Rules (see `checkstyle.xml`)
- **Max line length:** 120 characters
- **Indentation:** 4 spaces, no tabs
- **No star imports** (`import foo.*` - warning)
- **No unused/redundant imports**
- **Javadoc:** Required on public methods and types (warning level)
- **Braces:** Opening brace at end of line (`eol` style), braces required (single-line statements allowed)
- **Naming:** Standard Java conventions enforced (ConstantName, MemberName, MethodName, etc.)

### Integration Tests
Integration tests (`*IT.java`) require a running Kubernetes cluster. CI uses Kind (via `helm/kind-action`). To run locally:
```bash
kind create cluster                       # Create local cluster
./mvnw verify -P integration              # Run integration tests
```

## Common Development Tasks

### Adding a new Kubernetes ResourceType
1. Create class in `kubernetes-resources/src/main/java/io/skodjob/kubetest4j/resources/`
2. Implement `ResourceType<T>` interface
3. Wrap the Fabric8 client for that resource
4. Implement `isReady()` with proper readiness checks
5. Register it via `KubeResourceManager.get().setResourceTypes(new YourType())`

**Complete example** - use this as a template (see `DeploymentType.java`):
```java
/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import java.util.function.Consumer;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.interfaces.ResourceType;

/**
 * Implementation of ResourceType for specific kubernetes resource
 */
public class DeploymentType implements ResourceType<Deployment> {
    private final MixedOperation<Deployment, DeploymentList,
        RollableScalableResource<Deployment>> client;

    /**
     * Constructor
     */
    public DeploymentType() {
        this.client = KubeResourceManager.get().kubeClient()
            .getClient().apps().deployments();
    }

    @Override
    public String getKind() { return "Deployment"; }

    @Override
    public Long getTimeoutForResourceReadiness() {
        return KubeTestConstants.GLOBAL_TIMEOUT;
    }

    @Override
    public MixedOperation<?, ?, ?> getClient() { return client; }

    @Override
    public void create(Deployment resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .resource(resource).create();
    }

    @Override
    public void update(Deployment resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .resource(resource).update();
    }

    @Override
    public void delete(Deployment resource) {
        client.inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public void replace(Deployment resource, Consumer<Deployment> editor) {
        Deployment toBeUpdated = client
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();
        editor.accept(toBeUpdated);
        update(toBeUpdated);
    }

    @Override
    public boolean isReady(Deployment resource) {
        return client.resource(resource).isReady();
    }

    @Override
    public boolean isDeleted(Deployment resource) {
        return resource == null;
    }
}
```

### Writing an integration test
Test classes use `@ResourceManager` on a base class for automatic cleanup, and `@TestVisualSeparator` for log readability. Example pattern:

```java
/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.test.integration;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.resources.NamespaceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ResourceManager          // Enables automatic resource cleanup after tests
@TestVisualSeparator      // Visual separators in test logs
class MyFeatureIT {

    static {
        // Register ResourceTypes that KRM uses for readiness checks
        KubeResourceManager.get().setResourceTypes(new NamespaceType());
    }

    @Test
    void testSomething() {
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder().withNewMetadata()
                .withName("my-test-ns").endMetadata().build()
        );
        assertNotNull(KubeResourceManager.get().kubeClient()
            .getClient().namespaces().withName("my-test-ns").get());
        // Resources cleaned up automatically after test
    }
}
```

### Writing a test with @KubernetesTest (junit-extension)
The `@KubernetesTest` annotation provides declarative namespace management, dependency injection, and log collection. This is the **higher-level** alternative to `@ResourceManager`. Example:

```java
/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.examples;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.KubeCmdClient;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectCmdKubeClient;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KubernetesTest(
    namespaces = {"my-test-ns"},             // Auto-created and cleaned up
    cleanup = CleanupStrategy.AUTOMATIC,
    namespaceLabels = {"test-type=example"},
    collectLogs = true,                      // Collect logs on failure
    logCollectionStrategy = LogCollectionStrategy.ON_FAILURE,
    logCollectionPath = "target/test-logs"
)
class MyFeatureIT {

    @InjectKubeClient                        // Field injection
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @InjectCmdKubeClient
    KubeCmdClient<?> cmdClient;

    @Test
    void testSomething() {
        ConfigMap cm = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test-config")
            .withNamespace("my-test-ns")     // Must match a declared namespace
            .endMetadata()
            .addToData("key", "value")
            .build();

        resourceManager.createResourceWithWait(cm);

        assertNotNull(client.getClient().configMaps()
            .inNamespace("my-test-ns").withName("test-config").get());
    }

    @Test
    void testWithParamInjection(
            @InjectKubeClient KubeClient paramClient,           // Parameter injection
            @InjectResourceManager KubeResourceManager paramMgr) {
        assertNotNull(paramClient);
        assertNotNull(paramMgr);
    }
}
```

**When to use which:**
- `@KubernetesTest` (junit-extension) - full-featured: auto namespace creation, DI, log collection, multi-context
- `@ResourceManager` (core) - lightweight: just resource tracking and cleanup, no namespace management or DI

See more examples in `junit-extension/src/test/java/io/skodjob/kubetest4j/examples/`.

### Adding a new annotation/injection
1. Define annotation in `junit-extension/.../annotations/`
2. Handle injection in `DependencyInjector`
3. Add integration test in `test-examples`

### Adding OpenShift-specific resources
1. Create class in `openshift-resources/src/main/java/io/skodjob/kubetest4j/resources/`
2. Use OpenShift-specific Fabric8 client APIs

## Module Dependencies (what depends on what)
```
kubetest4j (core)           <- everything depends on this
kubernetes-resources        <- depends on kubetest4j
openshift-resources         <- depends on kubetest4j
junit-extension             <- depends on kubetest4j
log-collector               <- depends on kubetest4j
metrics-collector           <- depends on kubetest4j
test-examples               <- depends on all modules (test scope)
```

## Key Constants (KubeTestConstants)
- `GLOBAL_TIMEOUT = 10 min`, `GLOBAL_TIMEOUT_MEDIUM = 5 min`, `GLOBAL_TIMEOUT_SHORT = 3 min`
- `GLOBAL_POLL_INTERVAL_SHORT = 5s`, `GLOBAL_POLL_INTERVAL_1_SEC = 1s`

## Reuse Existing Utilities (DO NOT reinvent)

Before writing new helper code, check if it already exists. These are the most commonly needed utilities:

**Key utility locations:**
- `kubetest4j/src/main/java/io/skodjob/kubetest4j/utils/` - PodUtils, JobUtils, KubeUtils, ImageUtils, LoggerUtils, SecurityUtils, KubeTestUtils, ResourceUtils
- `kubetest4j/src/main/java/io/skodjob/kubetest4j/wait/Wait.java` - Polling/waiting
- `kubetest4j/src/main/java/io/skodjob/kubetest4j/executor/Exec.java` - Command execution
- `kubetest4j/src/main/java/io/skodjob/kubetest4j/KubeTestConstants.java` - Timeouts, intervals

## Common Pitfalls (DO NOT)
- **Do NOT forget the file header** - every `.java` file needs the copyright header or checkstyle fails
- **Do NOT use star imports** - `import foo.*` triggers checkstyle warnings
- **Do NOT exceed 120 chars per line** - checkstyle enforces this
- **Do NOT use tabs** - use 4 spaces for indentation
- **Do NOT skip Javadoc on public methods/types** - checkstyle warns on missing Javadoc
- **Do NOT create resources without registering the ResourceType** - call `KubeResourceManager.get().setResourceTypes(...)` first if you need readiness checks
- **Do NOT forget to add `@ResourceManager`** on test classes (or a base class) - without it, resources won't be cleaned up automatically
- **Do NOT put new resource types in the wrong module** - K8s native resources go in `kubernetes-resources`, OpenShift in `openshift-resources`, core interfaces in `kubetest4j`

## Adopters
OpenDataHub, Strimzi, Debezium, StreamsHub - all use this for E2E testing.
