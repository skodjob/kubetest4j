# Configuration Reference

All configuration values in kubetest4j are resolved in the following priority order (first match wins):

1. **Environment variable** (`export KUBE_URL=...`)
2. **JVM system property** (`-DKUBE_URL=...` passed via Maven `-D` or JVM args)
3. **YAML config file** (`config.yaml` in the project root, or any path set by `ENV_FILE`)
4. **Built-in default**

This means you can override any config value at runtime without changing code, and you can commit a `config.yaml` with team defaults that individual developers can override via environment variables.

---

## Passing Config via Maven

Any variable can be passed as a Maven system property:

```bash
./mvnw test \
  -DKUBE_URL=https://api.my-cluster:6443 \
  -DKUBE_TOKEN=my-bearer-token \
  -DCLIENT_TYPE=oc
```

---

## YAML Config File

Create `config.yaml` in the project root (next to `pom.xml`) to set defaults for your team:

```yaml
KUBE_URL: https://api.my-cluster:6443
KUBE_TOKEN: my-bearer-token
CLIENT_TYPE: kubectl
IP_FAMILY: ipv4
```

Use a different file path by setting the `ENV_FILE` environment variable:

```bash
export ENV_FILE=/home/ci/cluster-config.yaml
```

The YAML file must be a flat key-value map where every key is the all-caps name of the configuration variable.

---

## Connection Variables

### Default cluster (primary context)

| Variable | Description | Default |
|----------|-------------|---------|
| `KUBECONFIG` | Path to a kubeconfig file. Takes precedence over `KUBE_URL` + `KUBE_TOKEN` when set. | auto-detect |
| `KUBE_URL` | Kubernetes API server URL (e.g. `https://api.my-cluster:6443`) | — |
| `KUBE_TOKEN` | Bearer token for cluster authentication | — |

When neither `KUBECONFIG` nor `KUBE_URL`/`KUBE_TOKEN` are set, the Fabric8 client falls back to the default kubeconfig location (`~/.kube/config`) and the current kubeconfig context.

### Additional contexts (multi-cluster)

To configure a second cluster, append a suffix to the variable name. The suffix becomes the context ID used in `@InjectKubeClient(kubeContext = "...")` and `@ClassNamespace(kubeContext = "...")`.

```bash
# Second cluster via URL + token
export KUBE_URL_STAGING=https://api.staging:6443
export KUBE_TOKEN_STAGING=staging-token

# Third cluster via kubeconfig
export KUBECONFIG_PRODUCTION=/path/to/prod.kubeconfig

# Fourth cluster via kubeconfig
export KUBECONFIG_DEVELOPMENT=/path/to/dev.kubeconfig
```

Context IDs are derived from the suffix in **lowercase**: `KUBE_URL_STAGING` → context `"staging"`, `KUBECONFIG_PRODUCTION` → context `"production"`.

Use them in tests:

```java
@InjectKubeClient(kubeContext = "staging")
KubeClient stagingClient;

@ClassNamespace(name = "prod-ns", kubeContext = "production")
static Namespace prodNamespace;
```

---

## Client Type

| Variable | Values | Default | Description |
|----------|--------|---------|-------------|
| `CLIENT_TYPE` | `kubectl`, `oc` | `kubectl` | Selects the CLI client for `KubeCmdClient`. Use `oc` for OpenShift clusters. |

---

## IP Family

| Variable | Values | Default | Description |
|----------|--------|---------|-------------|
| `IP_FAMILY` | `ipv4`, `ipv6`, `dual` | `ipv4` | IP family of the cluster under test. Used by `KubeUtils` when filtering node/service IPs. |

---

## Config File Location

| Variable | Default | Description |
|----------|---------|-------------|
| `ENV_FILE` | `./config.yaml` | Path to the YAML config file. If the file does not exist, the framework logs a message and continues. |

---

## Saving Resolved Config for Debugging

After a test run you can dump all resolved configuration values to a YAML file for auditing or reproducing CI failures:

```java
TestEnvironmentVariables env = new TestEnvironmentVariables();
env.logEnvironmentVariables(); // logs to SLF4J at INFO
env.saveConfigurationFile("target/test-run-config");
// writes target/test-run-config/config.yaml
```

This file contains only the values that were actually read during that run.

---

## Example: CI Pipeline Setup

A typical CI configuration for a Kind cluster:

```bash
# .github/env or workflow env block
KUBECONFIG: /home/runner/.kube/config
CLIENT_TYPE: kubectl
```

For an OpenShift cluster with URL + token:

```bash
KUBE_URL: ${{ secrets.OCP_API_URL }}
KUBE_TOKEN: ${{ secrets.OCP_TOKEN }}
CLIENT_TYPE: oc
```

For multi-cluster testing in CI:

```bash
KUBECONFIG: /home/runner/.kube/config
KUBECONFIG_STAGING: /home/runner/.kube/staging.config
KUBECONFIG_PRODUCTION: /home/runner/.kube/prod.config
```

---

## Example: Local Development

For daily development work, set variables in your shell profile or use a `config.yaml` committed to a local branch:

```yaml
# config.yaml (git-ignored)
KUBE_URL: https://api.local-cluster:6443
KUBE_TOKEN: developer-token
CLIENT_TYPE: kubectl
```

Or pass them per-run:

```bash
./mvnw verify -P integration \
  -DKUBE_URL=https://api.local:6443 \
  -DKUBE_TOKEN=my-token
```

---

## Related Documentation

- [Quickstart Guide](QUICKSTART.md) — Getting cluster access configured in under 5 minutes
- [Core Module](../kubetest4j/README.md) — `KubeClient` and `KubeCmdClient` construction
- [JUnit Extension](../junit-extension/README.md) — Multi-context annotation usage
