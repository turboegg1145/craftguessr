#!/usr/bin/env bash
# Runs the dev dedicated server without Gradle (so a client build can run at the same time).
set -euo pipefail
exec docker exec -i -w /workspace/run geoguessr-dev /opt/java/openjdk/bin/java \
  -Dfabric.dli.config=/workspace/.gradle/loom-cache/launch.cfg \
  -Dfabric.dli.env=server \
  -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer \
  @/workspace/build/loom-cache/argFiles/runServer \
  --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED \
  -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant \
  net.fabricmc.devlaunchinjector.Main nogui
