# Contributing to Kubetest4j

First off, thanks for taking the time to contribute! 🎉👍 The following is a set of guidelines for contributing to the Kubetest4j repository.

## Code of Conduct

By participating in this project, you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md).

## How to Contribute

### Reporting Bugs

If you find a bug, please report it by opening an issue. When opening an issue, include:
- A clear and descriptive title.
- A detailed description of the problem.
- Steps to reproduce the issue.
- Any relevant logs or screenshots.

### Suggesting Enhancements

We welcome suggestions to improve the project. When suggesting enhancements, please:
- Use a clear and descriptive title.
- Provide a detailed explanation of the enhancement.
- Explain why this enhancement would be useful.

### Pull Requests

We welcome pull requests. If you are planning a major change, please open an issue first to discuss your plans. This helps avoid duplicate efforts and ensures that your contributions align with the project's goals.

When you are ready to submit your pull request, please ensure that you:
- Follow the existing code style and conventions.
- Test your changes thoroughly.
- Provide a detailed description of your changes in the pull request.

### Developer Certificate of Origin (DCO)

All commits must be signed off to certify that you have the right to
submit the code under the project's open source license. Add a
`Signed-off-by` line to your commit message:

    Signed-off-by: Your Name <your.email@example.com>

You can do this automatically with `git commit -s`. A DCO bot checks
all PRs for sign-off compliance.

### Testing Policy

All contributions **must** include tests for new functionality and bug fixes.
This is a requirement, not a suggestion — PRs without adequate tests will
not be merged.

1. **Unit Tests** (`*Test.java`)

   Every new feature, class, or method must have corresponding unit tests.
   Place these in the appropriate module's `src/test/java` directory. Use
   Fabric8 mock client (`@EnableKubernetesMockClient`) for Kubernetes API
   interactions.

2. **Integration Tests** (`*IT.java`)

   For features that interact with a real cluster, add integration tests
   to the `test-examples` module. These run against a Kind cluster in CI.

3. **Regression Tests**

   Bug fixes must include a test that reproduces the bug and verifies the
   fix. At least 50% of bugs fixed must have a corresponding regression
   test (enforced by maintainer review).

4. **Coverage**

   SonarCloud enforces >80% test coverage on new code. Check coverage
   locally with `./mvnw verify -P integration` (JaCoCo report).

### Style Guide

Please follow the existing code style and conventions used in the project. This helps to maintain a consistent codebase.

### Release Notes

Each GitHub release **must** include human-readable, categorized release notes — not just the auto-generated list of merged PRs. This is required by the [OpenSSF Best Practices](https://www.bestpractices.dev/en/criteria/0) `release_notes` criterion.

Use the following structure:

```
## Summary
One to three sentences describing the overall theme of this release.

### Highlights
- Notable new features or improvements

### Bug Fixes
- Bugs resolved in this release

### Dependency Updates
- Summary of updated dependencies

### Security
- List any CVEs fixed, or state: "No known CVEs were fixed in this release."
```

The **Security** section is mandatory. If a release fixes a publicly known vulnerability that already had a CVE assignment, it **must** be listed explicitly ([`release_notes_vulns`](https://www.bestpractices.dev/en/criteria/0#release_notes_vulns) criterion).

## Additional Resources

- [GitHub Help](https://help.github.com/)
- [Understanding the GitHub Flow](https://guides.github.com/introduction/flow/)

Thank you for contributing to Kubetest4j!
