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

### Testing

Every feature enhancement should be thoroughly tested. This includes writing both unit tests and integration tests.

1. **Unit Tests**

   Ensure that you write unit tests for any new functionality you add. Place these tests in the appropriate test files within the main project directory. Unit tests should cover individual units of code to ensure they work as expected.

2. **Integration Tests**

   Add relevant tests to the `test-examples` module to verify that the new features work correctly within the overall system. Integration tests should ensure that different parts of the application work together as intended.

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
