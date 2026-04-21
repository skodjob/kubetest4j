# AGENTS.md - AI Coding Agent Guide for kubetest4j

## Project Overview

**kubetest4j** is a Java testing library for Kubernetes and OpenShift clusters. It provides declarative, annotation-based testing with automatic resource management, multi-context cluster support, and integrated log/metrics collection.

- **Organization:** skodjob
- **License:** Apache 2.0
- **Java:** 21+ (CI tests on 21 and 25)
- **Repository:** https://github.com/skodjob/kubetest4j

## Build & Test Commands

```bash
./mvnw install                        # Build all modules
./mvnw install -pl kubetest4j         # Build single module
./mvnw test -pl <module>              # Unit tests for a module
./mvnw checkstyle:check -pl <module>  # Verify checkstyle compliance
./mvnw spotbugs:spotbugs              # Static analysis
./mvnw verify -P integration          # Integration tests (requires cluster)
```

**Before committing, always run `./mvnw checkstyle:check` on changed modules.** Checkstyle is enforced as an error in CI.

## CI Pipeline & PR Checks

PRs are reviewed by **CodeRabbit** (AI reviewer) and quality-gated by **SonarCloud**:
- **SonarCloud:** New code must have **>80% test coverage** (enforced). JaCoCo coverage is collected from `./mvnw verify -P integration` — both unit tests (`*Test.java`) and integration tests (`*IT.java`) count toward coverage.
- **CI builds:** `./mvnw install` + `./mvnw spotbugs:spotbugs` on Java 21 and 25.
- **CodeQL:** GitHub default setup for security vulnerability scanning (enabled in repo settings, no workflow file).
- **Scorecard:** OpenSSF supply-chain security analysis (`scorecard.yml`).
- **Verification:** SonarCloud scan runs on a separate workflow (`verify.yaml`).

When writing new code, add unit tests to cover it. Integration tests running against a real Kind cluster provide additional coverage.

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

**Module dependencies:** everything depends on `kubetest4j` (core). `test-examples` depends on all modules (test scope only).

## Architecture & Key Abstractions

### ResourceType<T> (`kubetest4j/.../interfaces/ResourceType.java`)
Core interface for Kubernetes resource lifecycle. Implement `getKind()`, `create()`, `update()`, `delete()`, `replace()`, `isReady()`, `isDeleted()`, `getTimeoutForResourceReadiness()`.

**To add a new resource type:** follow the pattern in `kubernetes-resources/.../resources/DeploymentType.java`.

### KubeResourceManager (`kubetest4j/.../resources/KubeResourceManager.java`)
Singleton managing resource lifecycle and LIFO-stack cleanup:
- `KubeResourceManager.get()` / `getForContext("name")` — default or per-context instance
- `createResourceWithWait(T)` / `createResourceWithoutWait(T)` / `createResourceAsyncWait(T)`
- `replaceResourceWithRetries(T, Consumer<T>)` — auto-retries on 409 conflicts
- `deleteResources()` / `deleteResourceWithWait(T)` — cleanup methods
- Tracks resources per `contextId + testDisplayName` in `STORED_RESOURCES`
- Uses `Semaphore` (default 50) for async operation throttling
- Uses virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`)
- Global callbacks: `addCreateCallback()` / `addDeleteCallback()` — fire on resource create/delete

**Thread safety:** `ConcurrentHashMap` for instances/caches, `CopyOnWriteArrayList` for callbacks, `ThreadLocal` for cluster context, `Stack` for per-test resource tracking.

### KubeClient (`kubetest4j/.../clients/KubeClient.java`)
Wrapper around Fabric8 `KubernetesClient`:
- `new KubeClient()` — auto-configure from env vars/kubeconfig
- `new KubeClient(kubeconfigPath)` — from kubeconfig file
- `KubeClient.fromUrlAndToken(url, token)` — from API URL + bearer token (generates temp kubeconfig cleaned up via JVM shutdown hook)

### KubeCmdClient / Kubectl / Oc (`kubetest4j/.../clients/cmdClient/`)
Abstraction for kubectl/oc CLI operations. `BaseCmdKubeClient` is the base; `Kubectl` and `Oc` provide implementation-specific behavior (e.g., `oc new-project` vs `kubectl create namespace`).

### JUnit Extension (`junit-extension/.../KubernetesTestExtension.java`)

**`@KubernetesTest` annotation** (see `junit-extension/.../annotations/KubernetesTest.java` for full attribute list):
- `resourceTypes` — declarative ResourceType registration
- `cleanup` — `CleanupStrategy.AUTOMATIC` or `MANUAL`
- `collectLogs` / `logCollectionStrategy` / `logCollectionPath` / `collectPreviousLogs` — log collection config
- `collectNamespacedResources` / `collectClusterWideResources` — what to collect
- `storeYaml` / `yamlStorePath` — persist created resource YAMLs
- `visualSeparatorChar` / `visualSeparatorLength` — log formatting

The annotation is `@Inherited`. `resourceTypes` has special merge behavior: child class inherits parent's `resourceTypes` if not explicitly overridden (see `resolveResourceTypes()` in `KubernetesTestExtension`).

**Namespace annotations** (on fields/parameters, not in `@KubernetesTest`):
- `@ClassNamespace(name, labels[], annotations[], kubeContext)` — class-level (static fields), created in beforeAll, deleted in afterAll. Pre-existing namespaces are protected (used but never deleted).
- `@MethodNamespace(prefix, labels[], annotations[], kubeContext)` — per-test-method (instance fields/parameters), auto-generated names (`<prefix>-<method>-<index>`, max 63 chars DNS-1123 compliant).

**Injection annotations** (all support `kubeContext` for multi-context):
- `@InjectKubeClient`, `@InjectCmdKubeClient`, `@InjectResourceManager`
- `@InjectResource(value, type, name, waitForReady, kubeContext)` — loads and creates resources from YAML files

**`@ResourceManager` annotation** (core module) — lightweight alternative to `@KubernetesTest`. Just resource tracking and LIFO cleanup, no namespace management or DI. Use on test classes or a base class.

**`@TestVisualSeparator`** — adds visual log separators between tests. Used on almost all test classes.

### Key Extension Services (junit-extension internals)
- `KubernetesTestExtension` — main JUnit lifecycle (beforeAll/afterAll/beforeEach/afterEach) + exception handlers
- `DependencyInjector` — handles field and parameter injection for all `@Inject*` and `@MethodNamespace` annotations
- `ClassNamespaceService` / `MethodNamespaceService` — namespace lifecycle management
- `ContextStoreHelper` — centralized `ExtensionContext.Store` access (all state stored here)
- `ExceptionHandlerDelegate` — exception handling + log collection on failure + cleanup (safety net for afterEach/afterAll failures)
- `LogCollectionService` — multi-context log collection with label-based namespace discovery
- `ConfigurationService` — parses `@KubernetesTest` annotation into `TestConfig` record

### Wait Utility (`kubetest4j/.../wait/Wait.java`)
Polling-based wait: `Wait.until(description, pollMs, timeoutMs, BooleanSupplier)` and async variant `Wait.untilAsync()`.

## Environment Variables

```
KUBE_URL, KUBE_TOKEN, KUBECONFIG                # Default context
KUBE_URL_<SUFFIX>, KUBE_TOKEN_<SUFFIX>           # Additional contexts (suffix = context ID)
KUBECONFIG_<SUFFIX>                              # Kubeconfig per context
CLIENT_TYPE=kubectl|oc                           # CLI client type (default: kubectl)
IP_FAMILY=ipv4|ipv6|dual                         # IP family (default: ipv4)
```

## Coding Conventions

- **Package:** `io.skodjob.kubetest4j` (all modules)
- **Test naming:** `*Test.java` for unit tests (Surefire), `*IT.java` for integration tests (Failsafe)
- **Code quality:** Checkstyle + SpotBugs enforced in CI; fix violations before committing
- **Code coverage:** New code must have >80% test coverage (enforced by SonarCloud). JaCoCo coverage is collected from `./mvnw verify -P integration`, which includes both unit and integration tests. Write unit tests for new code; integration tests running against a real cluster provide additional coverage.
- **Patterns:** Builder pattern for configuration, Singleton for managers, fluent APIs, records for immutable data (TestConfig, ResourceItem, ResourceCondition, ExecResult, CertAndKey, etc.)
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
- **No star imports** (`import foo.*` — error)
- **No unused/redundant imports** — error
- **Javadoc:** Required on public methods and types (warning level)
- **Braces:** Opening brace at end of line, braces required (single-line statements allowed)
- **Naming:** Standard Java conventions enforced

### Logging Conventions
```java
private static final Logger LOGGER = LoggerFactory.getLogger(MyClass.class);
```

- **Use `LoggerUtils`** for resource operations (`LoggerUtils.logResource("Creating", resource)`) and visual separators (`LoggerUtils.logSeparator()`)
- **Use direct `LOGGER` calls** for everything else (info=lifecycle, debug=internal state, warn=non-fatal, error=critical+exception)
- **Do NOT** use `System.out.println`, TRACE level in junit-extension, custom resource log formatting, or hardcoded separator strings

### Test Infrastructure
- **Unit tests:** Use Fabric8 `@EnableKubernetesMockClient(crud = true)` for mock K8s server, Mockito for mocking
- **Integration tests:** Require a running cluster. CI uses Kind (`helm/kind-action`). Run locally with `kind create cluster && ./mvnw verify -P integration`
- **Static mocking:** Use `MockedStatic<KubeResourceManager>` when testing utilities that call `KubeResourceManager.get()`

## Key Constants (KubeTestConstants)
- Timeouts: `GLOBAL_TIMEOUT = 10 min`, `GLOBAL_TIMEOUT_MEDIUM = 5 min`, `GLOBAL_TIMEOUT_SHORT = 3 min`
- Poll intervals: `GLOBAL_POLL_INTERVAL_LONG = 15s`, `GLOBAL_POLL_INTERVAL_MEDIUM = 10s`, `GLOBAL_POLL_INTERVAL_SHORT = 5s`, `GLOBAL_POLL_INTERVAL_1_SEC = 1s`
- Other: `GLOBAL_STABILITY_TIME = 1 min`, `DEFAULT_CONTEXT_NAME = "primary"`, `DEFAULT_MAX_CONCURRENT_OPERATIONS = 50`

## Reuse Existing Utilities (DO NOT reinvent)

Before writing new helper code, check if it already exists:
- `kubetest4j/.../utils/` — PodUtils, JobUtils, KubeUtils, ImageUtils, LoggerUtils, SecurityUtils, KubeTestUtils, ResourceUtils
- `kubetest4j/.../wait/Wait.java` — polling/waiting
- `kubetest4j/.../executor/Exec.java` — command execution (with ExecBuilder fluent API)
- `kubetest4j/.../security/` — CertAndKeyBuilder, OpenSsl, SecurityUtils for certificate management
- `kubetest4j/.../KubeTestConstants.java` — timeouts, intervals, defaults

## Common Development Tasks

### Adding a new Kubernetes ResourceType
1. Create class in `kubernetes-resources/src/main/java/io/skodjob/kubetest4j/resources/`
2. Implement `ResourceType<T>` — use `DeploymentType.java` as a template
3. Register via `@KubernetesTest(resourceTypes = {YourType.class})` or `KubeResourceManager.get().setResourceTypes(new YourType())`

### Writing tests
- **With `@KubernetesTest`** (junit-extension) — full-featured: namespace annotations, DI, log collection, multi-context. See examples in `junit-extension/src/test/java/io/skodjob/kubetest4j/examples/`
- **With `@ResourceManager`** (core) — lightweight: just resource tracking and LIFO cleanup
- Always add `@TestVisualSeparator` for log readability

### Adding a new annotation/injection
1. Define annotation in `junit-extension/.../annotations/`
2. Handle injection in `DependencyInjector`
3. Add integration test in `test-examples`

## Common Pitfalls (DO NOT)
- **Do NOT forget the file header** — every `.java` file needs the copyright header or checkstyle fails
- **Do NOT use star imports** — checkstyle error
- **Do NOT exceed 120 chars per line** — checkstyle error
- **Do NOT use tabs** — use 4 spaces
- **Do NOT skip Javadoc on public methods/types** — checkstyle warns
- **Do NOT create resources without registering the ResourceType** — call `setResourceTypes(...)` first if you need readiness checks
- **Do NOT forget `@ResourceManager`** on test classes (or a base class) — without it, resources won't be cleaned up
- **Do NOT put resource types in the wrong module** — K8s native in `kubernetes-resources`, OpenShift in `openshift-resources`, interfaces in `kubetest4j`
- **Do NOT use `deleteOnExit()`** — use explicit shutdown hooks with `Files.deleteIfExists()` for temp file cleanup
- **Do NOT fire callbacks/listeners on failed operations** — callbacks should only execute after successful API calls

## Adopters
OpenDataHub, Strimzi, Debezium, StreamsHub — all use this for E2E testing.
