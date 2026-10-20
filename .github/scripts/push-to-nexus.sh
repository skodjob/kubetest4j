#!/usr/bin/env bash
set -euo pipefail

export GPG_TTY="$(tty || true)"

# Always remove the imported signing key material, even if the deploy below fails.
cleanup() {
    rm -f signing.gpg
    gpg --batch --yes --delete-secret-keys >/dev/null 2>&1 || true
    gpg --batch --yes --delete-keys >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "${GPG_SIGNING_KEY}" | base64 -d > signing.gpg
gpg --batch --import signing.gpg

# 'set -e' propagates a failed deploy (e.g. HTTP 401) as a non-zero exit so the CI job fails
# instead of reporting green on a publish that never happened.
GPG_EXECUTABLE=gpg ./mvnw -DskipTests -s ./.github/scripts/settings.xml -P central-sonatype clean deploy
