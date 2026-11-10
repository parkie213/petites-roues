#!/bin/sh
# Runs the journeys with their screenshots compared, in Playwright's Docker image so text and
# images render exactly as they do for everyone else and in CI. Pass --update-snapshots to remake
# the reference screenshots after an intended change; other arguments go to `playwright test`.
set -e
web=$(cd "$(dirname "$0")/.." && pwd)
version=$(node -p 'require("@playwright/test/package.json").version')
exec docker run --rm --ipc=host \
  --user "$(id -u):$(id -g)" --env HOME=/tmp --env CI --env SCREENSHOTS=1 \
  --volume "$(dirname "$web"):/work" --workdir /work/web \
  "mcr.microsoft.com/playwright:v$version-noble" npx playwright test "$@"
