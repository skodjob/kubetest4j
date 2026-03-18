# Kubetest4j
Library for easy testing of Kubernetes deployments and operators using Fabric8 API.

[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/skodjob/kubetest4j/badge)](https://scorecard.dev/viewer/?uri=github.com/skodjob/kubetest4j)
[![Build](https://github.com/skodjob/kubetest4j/actions/workflows/build.yaml/badge.svg?branch=main)](https://github.com/skodjob/kubetest4j/actions/workflows/build.yaml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=skodjob_kubetest4j&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=skodjob_kubetest4j)
[![GitHub Release](https://img.shields.io/github/v/release/skodjob/kubetest4j)](https://github.com/skodjob/kubetest4j/releases)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.skodjob.kubetest4j/kubetest4j)](https://central.sonatype.com/search?q=io.skodjob.kubetest4j)

## Key Features

- **Automatic resource lifecycle** - Every resource created via [KubeResourceManager](kubetest4j/src/main/java/io/skodjob/kubetest4j/resources/KubeResourceManager.java) is automatically cleaned up after each test, whether it passes or fails
- **Declarative testing** - Use `@KubernetesTest` for annotation-driven namespace management, dependency injection, and log collection ([details](junit-extension/README.md))
- **Multi-cluster support** - Test across multiple Kubernetes/OpenShift clusters simultaneously with per-context resource managers
- **Built-in clients** - Auto-configured Fabric8 Kubernetes client and kubectl/oc CLI wrapper
- **Log & metrics collection** - Collect pod logs on failure ([log-collector](log-collector/README.md)) and scrape Prometheus metrics ([metrics-collector](metrics-collector/README.md))
- **Visual test separation** - ASCII separators in test logs for better readability
- **Cluster utilities** - [Helpers](kubetest4j/src/main/java/io/skodjob/kubetest4j/utils) for pod readiness, job management, OLM operations, and more

**Choose your approach:**
- **`@KubernetesTest`** (junit-extension) - Declarative, batteries-included. Best for most users.
- **`@ResourceManager`** (core) - Maximum control, custom setup. Use individual modules directly.

## Getting Started

See the **[Quickstart Guide](docs/QUICKSTART.md)** to get your first Kubernetes test running in under 5 minutes.

### Available modules

| Module | Artifact | Description |
|--------|----------|-------------|
| Core | `kubetest4j` | Resource manager, clients, utilities |
| K8s Resources | `kubernetes-resources` | ResourceType implementations for native K8s resources |
| OpenShift Resources | `openshift-resources` | ResourceType implementations for OpenShift/OLM resources |
| JUnit Extension | `junit-extension` | Declarative `@KubernetesTest` annotation with DI and log collection |
| Log Collector | `log-collector` | Pod log/description/YAML collection utility |
| Metrics Collector | `metrics-collector` | Prometheus metrics scraping from pods |

All modules are published to Maven Central under `io.skodjob.kubetest4j`.

## Documentation

| Document | Description |
|----------|-------------|
| [Quickstart Guide](docs/QUICKSTART.md) | Get started in 5 minutes (Maven & Gradle) |
| [Core Module](kubetest4j/README.md) | KubeResourceManager, clients, multi-context, ResourceTypes, utilities |
| [JUnit Extension](junit-extension/README.md) | Full `@KubernetesTest` reference, annotations, multi-context, log collection |
| [Log Collector](log-collector/README.md) | Pod log collection configuration and usage |
| [Metrics Collector](metrics-collector/README.md) | Prometheus metrics scraping |
| [Comparison with Alternatives](docs/COMPARISON.md) | How kubetest4j compares to Testcontainers, Fabric8, Arquillian, JKube |
| [Examples (core)](test-examples/src/test/java/io/skodjob/kubetest4j/test/integration) | Integration test examples using `@ResourceManager` |
| [Examples (JUnit ext)](junit-extension/src/test/java/io/skodjob/kubetest4j/examples/) | Integration test examples using `@KubernetesTest` |

## Config environment variables
| Variable | Description |
|----------|-------------|
| `ENV_FILE` | Path to YAML file with environment variable values |
| `KUBE_URL` | URL of the cluster (API URL) |
| `KUBE_TOKEN` | Token for cluster access |
| `KUBECONFIG` | Path to kubeconfig (overrides URL/token) |
| `CLIENT_TYPE` | Switch between `kubectl` or `oc` (default: `kubectl`) |
| `KUBE_URL_XXX` | URL for additional cluster (suffix like PROD, DEV, TEST) |
| `KUBE_TOKEN_XXX` | Token for additional cluster |
| `KUBECONFIG_XXX` | Kubeconfig for additional cluster |

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
