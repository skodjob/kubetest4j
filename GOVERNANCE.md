# Governance

## Decision-Making Process

Decisions are made by consensus among maintainers. For significant changes
(new modules, breaking API changes, dependency upgrades), a PR is opened and
discussed. Any maintainer can approve, but changes require at least one
approving review from another maintainer before merging.

For minor changes (documentation, typos, small bug fixes), a single
maintainer approval is sufficient.

If consensus cannot be reached, the project lead makes the final decision.

## Roles

### Project Lead

- Final decision authority when consensus cannot be reached
- Release management
- Security response coordination

### Maintainer

- Full write access to the repository
- Code review and PR approval authority
- Can merge PRs and create releases
- Responsible for upholding code quality standards

### Contributor

- Anyone who submits a PR, files an issue, or participates in discussions
- PRs require maintainer review before merge

## Current Maintainers

| Name           | GitHub | Role |
|----------------|--------|------|
| David Kornel   | @kornys | Project Lead |
| Jakub Stejskal | @Frawless | Maintainer |
| Lukas Kral     | @im-konge | Maintainer |

## Access Continuity

At least two maintainers have admin access to the GitHub repository,
CI secrets, and Maven Central publishing credentials. The project can
continue to operate (create issues, accept changes, and publish releases)
if any single person becomes unavailable.

## Changing Governance

Changes to this governance model require agreement from all active
maintainers.
