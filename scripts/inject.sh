#!/usr/bin/env bash
# usage: scripts/inject.sh key 71 | type "hello" | click 400 300
set -euo pipefail
cd "$(dirname "$0")/.."
exec docker run --rm \
  -e DISPLAY="${DISPLAY:-:1}" \
  -v /tmp/.X11-unix:/tmp/.X11-unix:rw \
  -v "$PWD/scripts:/scripts:ro" \
  --entrypoint java geoguessr:26.3-java25 /scripts/Inject.java "$@"
