#!/usr/bin/env bash
# "Android stays green" gate. Usage: android-green.sh [fast]
# fast skips :app:testDebugUnitTest. GATE_DRY_RUN=1 only prints the Gradle command (schema check still runs).
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE/../.."
TASKS=(:app:assembleDebug)
[ "${1:-}" = "fast" ] || TASKS+=(:app:testDebugUnitTest)
if [ "${GATE_DRY_RUN:-}" = "1" ]; then
  echo "DRY RUN: ./gradlew ${TASKS[*]}"
else
  ./gradlew "${TASKS[@]}"
fi
bash "$HERE/schema-check.sh"