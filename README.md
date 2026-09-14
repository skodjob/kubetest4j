<h1 align="center">
  <img src="docs/assets/kubetest4j-logo-sloth-hanging-teal.png" alt="kubetest4j" width="560">
</h1>

<p align="center"><strong>Kubernetes and OpenShift testing for Java</strong></p>

<p align="center">
  <a href="https://central.sonatype.com/search?q=io.skodjob.kubetest4j"><img src="https://img.shields.io/maven-central/v/io.skodjob.kubetest4j/kubetest4j?style=flat-square&amp;label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://github.com/skodjob/kubetest4j/actions/workflows/build.yaml"><img src="https://img.shields.io/github/actions/workflow/status/skodjob/kubetest4j/build.yaml?branch=main&amp;style=flat-square&amp;label=build" alt="Build"></a>
  <a href="docs/QUICKSTART.md"><img src="https://img.shields.io/badge/Java-21%2B-007396?style=flat-square" alt="Java 21+"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square" alt="Apache License 2.0"></a>
</p>

<p align="center">
  <a href="https://www.bestpractices.dev/projects/13456"><img src="https://www.bestpractices.dev/projects/13456/badge" alt="OpenSSF Best Practices"></a>
  <a href="https://scorecard.dev/viewer/?uri=github.com/skodjob/kubetest4j"><img src="https://api.scorecard.dev/projects/github.com/skodjob/kubetest4j/badge" alt="OpenSSF Scorecard"></a>
  <a href="https://sonarcloud.io/summary/new_code?id=skodjob_kubetest4j"><img src="https://sonarcloud.io/api/project_badges/measure?project=skodjob_kubetest4j&amp;metric=alert_status" alt="Quality Gate Status"></a>
  <a href="https://github.com/skodjob/kubetest4j/releases"><img src="https://img.shields.io/github/v/release/skodjob/kubetest4j?style=flat-square" alt="GitHub Release"></a>
  <a href="https://snyk.io/test/github/skodjob/kubetest4j"><img src="https://snyk.io/test/github/skodjob/kubetest4j/badge.svg" alt="Known Vulnerabilities"></a>
</p>

<p align="center">
  <a href="docs/QUICKSTART.md"><strong>Getting started</strong></a> ·
  <a href="#documentation">Documentation</a> ·
  <a href="junit-extension/src/test/java/io/skodjob/kubetest4j/examples/">Examples</a> ·
  <a href="ROADMAP.md">Roadmap</a> ·
  <a href="https://github.com/skodjob/kubetest4j/discussions">Discussions</a>
</p>

kubetest4j is a Java library for integration and end-to-end testing of Kubernetes applications and operators.
Built on **Fabric8** and **JUnit**, it brings resource lifecycle management, namespace isolation, and failure
diagnostics to your test suite.

## Features

- **Declarative tests** — Configure namespaces, client injection, and log collection with `@KubernetesTest`.
- **Automatic cleanup** — Track resources through `KubeResourceManager` and clean them up after tests,
  including failed tests.
- **Resource readiness** — Wait for Kubernetes and OpenShift resources with built-in or custom readiness checks.
- **Failure diagnostics** — Collect pod logs, resource descriptions, and YAML when tests fail.
  Scrape Prometheus metrics with the metrics collector.
- **Multiple clusters** — Use separate clients and resource managers for each Kubernetes or OpenShift context.
- **Familiar APIs** — Work with Fabric8 builders, YAML manifests, and kubectl/oc commands.

## Getting started

Start with the **[Quickstart Guide](docs/QUICKSTART.md)** for Maven and Gradle setup and your first test.
You need **Java 21+**, a Kubernetes cluster, and kubectl configured to access it.

Use **`@KubernetesTest`** for namespace management, dependency injection, and automatic cleanup.
For suites with custom lifecycle setup, the core module provides **[`@ResourceManager`](kubetest4j/README.md)**
for resource tracking and cleanup.

Explore the [JUnit extension examples](junit-extension/src/test/java/io/skodjob/kubetest4j/examples/)
for namespace isolation, YAML injection, log collection, and multiple clusters, or the
[core examples](test-examples/src/test/java/io/skodjob/kubetest4j/test/integration).

## Documentation

| Guide | Topics |
|-------|--------|
| [JUnit Extension](junit-extension/README.md) | Annotations, namespace management, injection, and test lifecycle |
| [Configuration](docs/CONFIGURATION.md) | Cluster access, environment variables, system properties, and YAML configuration |
| [Resource Types](docs/RESOURCE-TYPES.md) | Supported Kubernetes/OpenShift resources and custom readiness checks |
| [Resource Batches](docs/RESOURCE-BATCH.md) | Resource grouping and cleanup order |
| [Architecture](ARCHITECTURE.md) | Modules, resource management, and threading |
| [Testing Guide](docs/TESTING.md) | Building the library, unit tests, integration tests, and CI checks |
| [Comparison](docs/COMPARISON.md) | Choosing a Kubernetes testing approach |

<details>
<summary><strong>Modules and reference documentation</strong></summary>

All modules are available on Maven Central under `io.skodjob.kubetest4j`.
Start with `junit-extension` and `kubernetes-resources`.

| Artifact | Purpose |
|----------|---------|
| [`junit-extension`](junit-extension/README.md) | Declarative testing with `@KubernetesTest` |
| [`kubetest4j`](kubetest4j/README.md) | Resource manager, clients, waits, and utilities |
| [`kubernetes-resources`](docs/RESOURCE-TYPES.md) | Native Kubernetes resource types |
| [`openshift-resources`](docs/RESOURCE-TYPES.md) | OpenShift and OLM resource types |
| [`log-collector`](log-collector/README.md) | Pod logs, resource descriptions, and YAML collection |
| [`metrics-collector`](metrics-collector/README.md) | Prometheus metrics scraping |

</details>

## Adopters

kubetest4j is used in end-to-end testing by:

- **Strimzi** — [Kafka Operator](https://github.com/strimzi/strimzi-kafka-operator/tree/main/systemtest)
  and [Kafka Access Operator](https://github.com/strimzi/kafka-access-operator/tree/main/systemtest)
- **Debezium** — [Debezium Operator](https://github.com/debezium/debezium-operator/tree/main/systemtests)
- **StreamsHub** — [Streams E2E](https://github.com/streamshub/streams-e2e),
  [MCP](https://github.com/streamshub/streamshub-mcp),
  and [Console for Apache Kafka®](https://github.com/streamshub/console)
- **Kroxylicious** — [Kroxylicious](https://github.com/kroxylicious/kroxylicious)

## Contributing

Bug fixes, documentation, examples, and new resource types are welcome.
See the **[Contributing Guide](CONTRIBUTING.md)** for the development workflow and the
**[Testing Guide](docs/TESTING.md)** for build and test commands.

Ask questions in [Discussions](https://github.com/skodjob/kubetest4j/discussions),
[report an issue](https://github.com/skodjob/kubetest4j/issues), or help shape the [roadmap](ROADMAP.md).

## Maintainers

- [David Kornel](https://github.com/kornys) <kornys@outlook.com>
- [Lukas Kral](https://github.com/im-konge) <lukywill16@gmail.com>
- [Jakub Stejskal](https://github.com/Frawless) <xstejs24@gmail.com>
