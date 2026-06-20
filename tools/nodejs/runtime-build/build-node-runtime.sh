#!/usr/bin/env sh
set -eu

LOCK_FILE="${1:-tools/nodejs/runtime-build/runtime-build.lock.json}"
REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"

if [ ! -f "$REPO_ROOT/$LOCK_FILE" ]; then
  echo "Missing runtime build lock file: $LOCK_FILE" >&2
  exit 1
fi

node "$REPO_ROOT/tools/nodejs/runtime-build/verify-runtime-build-plan.js" \
  --repo-root "$REPO_ROOT" \
  --lock-file "$REPO_ROOT/$LOCK_FILE"

echo "Plan-only mode. Executable runtime build remains blocked until S9-04 artifact sources and signing inputs are finalized."
