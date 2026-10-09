# kubetest4j Security Self-Assessment

**Last reviewed:** 2026-10-08
**Assessment type:** OpenSSF OSPS Self-Assessment ([ossf/security-assessments](https://github.com/ossf/security-assessments))
**Target maturity:** OSPS Baseline Level 2 (supports OSPS-SA-03.01)
**Status:** Maintainer-reviewed. Supersedes the inline threat model in
[SECURITY.md](../../SECURITY.md), which remains the authoritative reporting policy.

## 1. Project Overview and Scope

kubetest4j is a **Java testing library** for integration and end-to-end testing of
applications and operators on Kubernetes and OpenShift. It provides declarative,
annotation-based tests (`@KubernetesTest`), automatic resource lifecycle management
and cleanup (`KubeResourceManager`), and failure diagnostics (log/metrics collection).
It is built on the Fabric8 Kubernetes client and JUnit 5.

**Actors**

- **Test author** — a developer who writes and runs tests using the library. The
  library executes the author's test code and is **fully trusted** by it.
- **Kubernetes / OpenShift API server** — the external system the library drives via
  the Fabric8 client and `kubectl`/`oc` CLIs.
- **CI runner / local workstation** — the host that runs the tests, provides
  credentials via environment variables or kubeconfig, and holds temporary files.
- **Container registries** — referenced by image coordinates used in test resources.

**Main flows**

1. A test declares clusters/namespaces/resources via annotations; the JUnit extension
   wires up clients and a resource manager.
2. The library authenticates to the cluster (kubeconfig, or API URL +
   bearer token configured via environment variables) and creates/read/updates/deletes
   resources, waiting for readiness.
3. On completion (including failure) it collects diagnostics and performs LIFO cleanup
   of tracked resources.

**In scope:** the runtime behavior of the core library that handles credentials,
cluster communication, process execution, YAML ingestion, and temporary files.

**Out of scope:** the security of the Kubernetes/OpenShift clusters under test, the
test author's own code, and third-party CI infrastructure.

**Security goals:** do not exfiltrate data or make network calls beyond the configured
cluster; clean up credential-bearing temporary files; avoid introducing injection or
deserialization weaknesses in the library's own code.

**Non-goals:** kubetest4j is a **test tool, not a production runtime**. It is not
hardened against malicious test code, and it deliberately favors convenience against
ephemeral test clusters over production-grade transport hardening (see §3).

## 2. Development Practices and Governance

- **Maintainers & rights** — Three maintainers (see [GOVERNANCE.md](../../GOVERNANCE.md)):
  David Kornel (project lead), Jakub Stejskal, Lukas Kral. At least two hold admin
  access to the repository, CI secrets, and Maven Central publishing credentials for
  continuity.
- **Code review** — Changes to `main` require at least one approving review from another
  maintainer (GOVERNANCE.md; enforced by a branch ruleset). Default owners are declared
  in [.github/CODEOWNERS](../../.github/CODEOWNERS).
- **CI gates** — Every PR runs build on JDK 21 and 25, `verify`, Checkstyle, SpotBugs,
  SonarCloud (quality gate enforcing >80% coverage on new code), CodeQL (SAST), Snyk
  (SCA/license), and GitGuardian secret scanning.
- **Contributor identity** — All commits must be DCO `Signed-off-by`; a DCO bot gates
  PRs ([CONTRIBUTING.md](../../CONTRIBUTING.md)).
- The `main` ruleset requires at least one non-author maintainer approval, which is the
  intended policy and satisfies the Baseline. At the repository-settings level the OSPS
  scanner currently reports `AC-04.01` (default workflow token permissions), `BR-07.01`
  (secret-scanning push protection), and `QA-03.01` (required status checks) as unmet;
  these are planned repository-hardening follow-ups.

## 3. Security Controls and Threat Considerations

**Authentication, authorization, secrets**

- The library authenticates with credentials the caller supplies (kubeconfig,
  `KUBE_URL`/`KUBE_TOKEN` env vars, or an API URL + bearer token). Authorization is
  entirely the cluster's RBAC; the library adds none.
- Bearer tokens are consumed from the caller's environment/files and are not logged.
- For the API URL + token path, a kubeconfig is generated on disk so external
  `kubectl`/`oc` commands can authenticate (`KubeClient.generateTempKubeconfig`,
  `KubeClient.java:413`).

**Trust boundaries & untrusted input**

- *Author ↔ library:* full trust by design — the library runs the author's code.
- *Library ↔ API:* HTTPS via Fabric8. On the kubeconfig/env path, TLS verification
  follows the kubeconfig. On the `KubeClient(apiUrl, token)` / `fromUrlAndToken()`
  convenience path, **TLS certificate and hostname verification are intentionally
  disabled** (`withTrustCerts(true)`, `withDisableHostnameVerification(true)` at
  `KubeClient.java:98-99`; `--insecure-skip-tls-verify=true` at `KubeClient.java:427`)
  to support ephemeral test clusters with self-signed certificates.
- *Library ↔ filesystem:* the generated kubeconfig holds a token and is written to a
  deterministic path under `System.getProperty("user.dir")`
  (`KubeTestEnv.USER_PATH`, `KubeTestEnv.java:39`) with default file permissions, then
  removed on JVM shutdown via `Files.deleteIfExists` (`KubeClient.java:46-50`).

**Top threats**

| Threat | Attack | Impact | Mitigation |
| --- | --- | --- | --- |
| TLS MITM on URL+token path | Attacker intercepts runner↔API traffic | Bearer-token capture, cluster access | Documented trade-off; use a kubeconfig with a trusted CA for verified TLS |
| Credential-bearing temp kubeconfig | Local user reads the kubeconfig on a shared host | Token disclosure | Shutdown-hook cleanup; ephemeral on CI. **Gap:** predictable path in `user.dir`, no `0600` hardening |
| Command injection (CWE-78) | Crafted input reaches a shell | Arbitrary command execution | `Exec` builds commands as argument lists, not shell strings (`executor/Exec.java`) |
| Unsafe deserialization (CWE-502) | Malicious YAML manifest | Code execution during parse | YAML parsing delegated to the Fabric8 client (SnakeYAML safe defaults) |
| Dependency / supply-chain vulnerability | Vulnerable or malicious dependency or action | Compromise of builds or consumers | Dependabot + Snyk (SCA); SHA-pinned CI actions; image digest pinning; Scorecard; harden-runner |
| Code vulnerability | Bug introduced in library code | Weakness shipped to users | SpotBugs + SonarCloud + CodeQL on every PR; fuzzing |

**Known gaps / planned work**

- TLS verification is disabled on the URL+token path. This is an accepted, intentional
  trade-off for a test library that targets ephemeral clusters with self-signed
  certificates; callers needing verified TLS use a kubeconfig backed by a trusted CA.
  Documented here and in SECURITY.md.
- Temporary kubeconfig uses a predictable path under `user.dir` with default
  permissions. On a shared host this is a minor local-disclosure consideration; possible
  hardening is a per-process secure temp directory with `0600` permissions. (SECURITY.md's
  CWE-377 note refers to `Files.createTempFile`, which is used for crypto temp material in
  `security/OpenSsl.java` and `utils/SecurityUtils.java`, not for the kubeconfig.)
- No SBOM or signed GitHub-release provenance yet (Maven Central artifacts are
  GPG-signed). Planned as a supply-chain hardening follow-up.

## 4. Incident Response and Vulnerability Management

- **Reporting** — via GitHub private vulnerability reporting (enabled) or email, per
  [SECURITY.md](../../SECURITY.md).
- **Triage & disclosure** — acknowledgement within 72 hours; investigation, estimated
  fix timeline, and notification on fix (SECURITY.md "Response Process"). Confirmed
  vulnerabilities are published as GitHub Security Advisories and listed in release
  notes' Security section.
- **Past advisories** — none to date (0 published advisories).

## 5. Dependencies and Supply Chain

- **Selection & updates** — Maven-managed dependencies (Fabric8 Kubernetes client 8.x,
  JUnit Jupiter 6.x). Updates are automated via [Dependabot](../../.github/dependabot.yml)
  and monitored by Snyk.
- **SCA / SAST** — Snyk (SCA + license), CodeQL, SpotBugs, SonarCloud; fuzzing workflows
  (`.github/workflows/fuzz_*.yml`).
- **Build & release** — Automated pipeline (`.github/workflows/publish.yaml`) publishes
  **GPG-signed** artifacts to Maven Central; CI actions are pinned by commit SHA and the
  release job hardens the runner (step-security/harden-runner).
- **Pending** — SBOM generation and signed GitHub-release provenance, planned as
  supply-chain hardening follow-ups.

---

*This assessment is a point-in-time document. Re-review when the credential handling,
transport configuration, or release pipeline changes.*
