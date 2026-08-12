# Testing Guide

This guide covers how to write unit and integration tests for kubetest4j contributions, and how the CI pipeline runs them.

---

## Test Types

| Type | Pattern | Runner | When used |
|------|---------|--------|-----------|
| **Unit tests** | `*Test.java` | Maven Surefire | Fast, no cluster required |
| **Integration tests** | `*IT.java` | Maven Failsafe (`-P integration`) | Require a running Kubernetes cluster |

Unit tests are the default and run on every `./mvnw install`. Integration tests require a cluster and are opted into with the `integration` Maven profile.

---

## Unit Tests

### Scope

Write unit tests for:
- New `ResourceType<T>` implementations
- Utility classes (`PodUtils`, `JobUtils`, `KubeUtils`, etc.)
- Configuration parsing (`TestEnvironmentVariables`)
- Any new public method

### Fabric8 Mock Client

Use Fabric8's `@EnableKubernetesMockClient(crud = true)` to get an in-memory Kubernetes API server. The mock server supports full CRUD semantics (list, get, create, update, delete, watch) without a real cluster.

```java
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@EnableKubernetesMockClient(crud = true)
class MyResourceTypeTest {

    private KubernetesClient kubernetesClient; // injected by the extension
    private MyResourceType target;

    @BeforeEach
    void setup() {
        target = new MyResourceType(kubernetesClient.resources(MyResource.class));
    }

    @Test
    void testCrudOperations_create_resourceExists() {
        MyResource resource = new MyResourceBuilder()
            .withNewMetadata().withName("test-cr").withNamespace("default").endMetadata()
            .build();

        target.create(resource);

        MyResource created = kubernetesClient.resources(MyResource.class)
            .inNamespace("default").withName("test-cr").get();
        assertNotNull(created);
    }

    @Test
    void testIsDeleted_nullResource_returnsTrue() {
        assertTrue(target.isDeleted(null));
    }

    @Test
    void testIsDeleted_existingResource_returnsFalse() {
        assertFalse(target.isDeleted(new MyResource()));
    }
}
```

The field `kubernetesClient` is automatically injected by the `@EnableKubernetesMockClient` extension. The mock is scoped per-test method by default.

### Mocking KubeResourceManager

When testing code that calls `KubeResourceManager.get()` (utility classes, callbacks), use `MockedStatic`:

```java
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

class KubeUtilsTest {

    @Test
    void testLabelNamespace_callsApiWithCorrectLabel() {
        KubeResourceManager mockManager = mock(KubeResourceManager.class);
        KubeClient mockClient = mock(KubeClient.class);
        KubernetesClient mockK8sClient = mock(KubernetesClient.class);

        when(mockManager.kubeClient()).thenReturn(mockClient);
        when(mockClient.getClient()).thenReturn(mockK8sClient);
        // set up further mock chains as needed...

        try (MockedStatic<KubeResourceManager> mocked = Mockito.mockStatic(KubeResourceManager.class)) {
            mocked.when(KubeResourceManager::get).thenReturn(mockManager);

            KubeUtils.labelNamespace("my-ns", "key", "value");

            // verify the expected call was made
            verify(mockK8sClient.namespaces().withName("my-ns"), times(1)).edit(any());
        }
    }
}
```

### Spying on KubeResourceManager

For tests that need a partially real `KubeResourceManager` with some methods mocked:

```java
@BeforeEach
void setup() {
    KubeResourceManager.clearInstances(); // reset singleton state between tests
    kubeResourceManager = spy(KubeResourceManager.get());

    doReturn(mockKubeClient).when(kubeResourceManager).kubeClient();
    doReturn(true).when(kubeResourceManager).waitResourceCondition(any(), any());

    // Provide a test context so the resource stack key can be resolved
    ExtensionContext mockContext = mock(ExtensionContext.class);
    when(mockContext.getDisplayName()).thenReturn("mockTest");
    kubeResourceManager.setTestContext(mockContext);
}
```

Always call `KubeResourceManager.clearInstances()` in `@BeforeEach` to prevent singleton state from leaking between tests.

### Test Naming

Follow the pattern `methodName_condition_expectedResult`:

```java
@Test
void testIsReady_allReplicasAvailable_returnsTrue() { ... }

@Test
void testIsReady_noReplicasAvailable_returnsFalse() { ... }

@Test
void testCreate_namespaceAlreadyExists_doesNotThrow() { ... }
```

Use `@DisplayName` for human-readable descriptions in IDE output:

```java
@Test
@DisplayName("isReady() returns false when deployment has no available replicas")
void testIsReady_noReplicasAvailable_returnsFalse() { ... }
```

---

## Integration Tests

Integration tests run against a **real Kubernetes cluster** and verify end-to-end behaviour. They live in the `test-examples` module or in a module's `src/test/java` directory with `*IT.java` suffix.

### Prerequisites

- A running cluster (Kind, Minikube, OpenShift, or remote)
- `kubectl` pointing to the cluster, or `KUBE_URL` + `KUBE_TOKEN` configured

Quick local setup with Kind:

```bash
kind create cluster
kubectl cluster-info
```

### Running Integration Tests

```bash
# All integration tests
./mvnw verify -P integration

# Single module
./mvnw verify -P integration -pl test-examples

# Single test class
./mvnw verify -P integration -pl test-examples -Dit.test=ResourceBatchIT
```

### Writing an Integration Test with `@KubernetesTest`

Extend an abstract base class that handles common setup, or use `@KubernetesTest` directly:

```java
import io.fabric8.kubernetes.api.model.Namespace;
import io.skodjob.kubetest4j.annotations.ClassNamespace;
import io.skodjob.kubetest4j.annotations.CleanupStrategy;
import io.skodjob.kubetest4j.annotations.InjectKubeClient;
import io.skodjob.kubetest4j.annotations.InjectResourceManager;
import io.skodjob.kubetest4j.annotations.KubernetesTest;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KubernetesTest(
    cleanup = CleanupStrategy.AUTOMATIC,
    resourceTypes = {NamespaceType.class, DeploymentType.class}
)
class MyFeatureIT {

    @ClassNamespace(name = "my-feature-test")
    static Namespace testNs;

    @InjectKubeClient
    KubeClient client;

    @InjectResourceManager
    KubeResourceManager resourceManager;

    @Test
    void testMyFeature_resourceCreated_isAvailableOnCluster() {
        // ... create resources, assert they exist, framework cleans up automatically
    }
}
```

### Writing an Integration Test with `@ResourceManager`

Use the core-only approach when you need maximum control or are not using the JUnit extension:

```java
import io.skodjob.kubetest4j.annotations.ResourceManager;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.resources.NamespaceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ResourceManager
@TestVisualSeparator
class MyCoreFeatureIT {

    static {
        KubeResourceManager.get().setResourceTypes(new NamespaceType());
    }

    @Test
    void testNamespaceCreated_isAvailableOnCluster() {
        KubeResourceManager.get().createResourceWithWait(
            new NamespaceBuilder().withNewMetadata().withName("core-test").endMetadata().build()
        );
        assertNotNull(KubeResourceManager.get().kubeClient()
            .getClient().namespaces().withName("core-test").get());
        // namespace deleted automatically after test
    }
}
```

### Integration Test Naming Conventions

Integration test classes end in `IT` and test methods follow the same naming convention as unit tests:

```
testFeatureName_condition_expectedResult
```

For example:
- `testResourceCreation_namespaceSpecified_resourceAppearsOnCluster`
- `testCleanup_testFails_resourcesStillDeleted`

---

## CI Pipeline

The CI pipeline runs on every push and pull request.

### Checks on Every PR

| Check | How it runs |
|-------|-------------|
| **Build** | `./mvnw install` on Java 21 and 25 |
| **SpotBugs** | `./mvnw spotbugs:spotbugs` |
| **Checkstyle** | Enforced by `./mvnw install` (error level) |
| **Unit tests** | Part of `./mvnw install` |
| **Integration tests** | `./mvnw verify -P integration` against a Kind cluster |
| **SonarCloud** | Requires >80% test coverage on new code |
| **CodeQL** | Security vulnerability scanning |

### Running CI Checks Locally

Before opening a PR, run the same checks CI runs:

```bash
# Build and unit tests
./mvnw install

# Checkstyle only
./mvnw checkstyle:check

# SpotBugs
./mvnw spotbugs:spotbugs

# Integration tests (requires a running cluster)
./mvnw verify -P integration

# Single module integration tests
./mvnw verify -P integration -pl test-examples
```

### Coverage Requirements

SonarCloud enforces **>80% test coverage** on new code. Coverage is collected from `./mvnw verify -P integration`, which includes both unit tests and integration tests via JaCoCo.

If you add a new class or method, include unit tests that cover the main code paths. Integration tests count toward coverage as well, but require a cluster.

---

## Checkstyle Requirements

Every `.java` file must start with the project copyright header:

```java
/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
```

Other enforced rules:

- Max 120 characters per line
- 4-space indentation (no tabs)
- No star imports (`import foo.*`)
- No unused imports
- Javadoc required on all public methods and types
- Opening brace at end of line
- Standard Java naming conventions

Run checkstyle before committing:

```bash
./mvnw checkstyle:check -pl <module>
```

---

## Related Documentation

- [Contributing Guide](../CONTRIBUTING.md) — PR process, DCO, testing policy
- [Architecture](../ARCHITECTURE.md) — Module structure and key abstractions
- [Resource Types](RESOURCE-TYPES.md) — How to implement and test new `ResourceType` classes
- [Quickstart Guide](QUICKSTART.md) — Getting a cluster connected in under 5 minutes
