# Security Policy

## Supported Versions

We release patches for security vulnerabilities only for the latest released version of the library. To ensure you are receiving the latest security updates, please update to the latest version of the library.

| Version        | Supported          |
| -------------- | ------------------ |
| Latest release | :white_check_mark: |
| Older versions | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability, please email to security[at]skodjob.io. All security vulnerabilities will be promptly addressed.

### Reporting Guidelines

To help us address the issue as quickly as possible, please include the following details in your report:
- A description of the vulnerability and its potential impact.
- Detailed steps to reproduce the vulnerability.
- Any potential mitigations or workarounds.

We kindly ask you to refrain from publicly disclosing the vulnerability until we have resolved it.

### Response Process

Upon receiving your report, we will:
1. Acknowledge the receipt of your report within 72 hours.
2. Investigate and validate the reported vulnerability.
3. Provide you with an estimated timeline for the fix.
4. Notify you when the vulnerability is fixed.

We are committed to keeping our users safe and will do our utmost to address all security vulnerabilities in a timely manner.

## Security Expectations

kubetest4j is a **testing library** intended for use in CI/CD pipelines
and local development environments. It is NOT designed for production
workloads.

### What users CAN expect:

- The library does not exfiltrate data or make network calls beyond
  the configured Kubernetes API servers
- Temporary files (kubeconfigs generated from URL+token) are cleaned
  up via JVM shutdown hooks
- Container image references in CI are pinned by SHA digest
- Dependencies are monitored for known vulnerabilities (Dependabot, Snyk)
- Static analysis (SpotBugs, SonarCloud, CodeQL) runs on every PR

### What users CANNOT expect:

- The library executes kubectl/oc commands and creates/deletes
  Kubernetes resources — it requires cluster access with appropriate
  RBAC permissions
- The library does not encrypt kubeconfig files or bearer tokens at
  rest — these are consumed from environment variables or files as
  provided
- The library is not hardened against malicious test code — it trusts
  the test author

## Threat Model

### Trust Boundaries

1. **Test author <-> Library**: The library trusts test code completely.
   Test authors are developers with cluster access.
2. **Library <-> Kubernetes API**: Communication uses HTTPS via Fabric8
   client. Authentication via kubeconfig or bearer token. Certificate
   verification is not disabled.
3. **Library <-> Local filesystem**: Temporary kubeconfigs are written
   with default permissions and deleted on JVM shutdown.

### Threats Considered

| Threat | Mitigation |
|--------|------------|
| Dependency vulnerability | Dependabot + Snyk monitoring |
| Code vulnerability | SpotBugs + SonarCloud + CodeQL on every PR |
| Malicious dependency | Container image SHA pinning, Scorecard analysis |
| Credential leakage | Temp kubeconfig cleanup, no logging of tokens |
| Supply chain attack | Signed releases (GPG), pinned CI action SHAs |

### Common Weakness Countermeasures

- **CWE-78 (Command injection)**: CLI commands built via the `Exec`
  utility with argument arrays, not string concatenation
- **CWE-377 (Insecure temp file)**: Temp kubeconfigs created with
  `Files.createTempFile` and deleted via shutdown hook
- **CWE-502 (Deserialization)**: YAML parsing delegated to Fabric8
  client (SnakeYAML with safe defaults)

## Hardening

The following hardening mechanisms are in place:

- **CI warnings as errors**: `failOnWarnings=true` in Maven compiler
  configuration
- **Checkstyle enforcement**: Code style violations fail the build
- **SpotBugs**: Static bug detection runs on every build
- **CodeQL**: GitHub security scanning enabled via default setup
- **SonarCloud**: Quality gate enforcing >80% test coverage on new code
- **Pinned dependencies**: CI actions pinned by SHA, container images
  pinned by digest
- **Signed releases**: All Maven Central artifacts are GPG-signed

## Security Resources

- [OWASP Top Ten](https://owasp.org/www-project-top-ten/)
- [CVE Details](https://www.cvedetails.com/)
- [National Vulnerability Database](https://nvd.nist.gov/)

Thank you for helping us keep kubetest4j secure!
