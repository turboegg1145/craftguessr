#!/usr/bin/env bash
# Rebuild + restart the dev server and headless client in one go.
# Usage: bash scripts/dev-cycle.sh [--no-build] [--no-client]
set -u
cd "$(dirname "$0")/.." || exit 1
ROOT=$(pwd)
BUILD=1
CLIENT=1
SERVER=1
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    --no-client) CLIENT=0 ;;
    --client-only) SERVER=0 ;;
  esac
done

if [ "$BUILD" = 1 ]; then
  ./dev.sh gw compileJava >/tmp/mc_build.log 2>&1 || { tail -30 /tmp/mc_build.log; exit 1; }
  echo "build ok"
fi

if [ "$SERVER" = 1 ]; then
# stop the old server
pgrep -f 'Knot[S]erver' | xargs -r kill 2>/dev/null
sleep 3

# console fifo with a persistent writer
rm -f /tmp/mc_cmd
mkfifo /tmp/mc_cmd
setsid bash -c 'sleep 1000000 > /tmp/mc_cmd 2>/dev/null' < /dev/null &
sleep 1
setsid bash -c "cd '$ROOT' && bash scripts/server-direct.sh < /tmp/mc_cmd > /tmp/mc_server.log 2>&1" < /dev/null &
echo "server starting..."
for _ in $(seq 1 60); do
  grep -q "CraftGuessr 0.1.0 ready" /tmp/mc_server.log 2>/dev/null && break
  sleep 2
done
grep -a "CraftGuessr 0.1.0 ready" /tmp/mc_server.log | sed 's/\x1b\[[0-9;]*m//g' | tail -1
fi

if [ "$CLIENT" = 0 ]; then
  exit 0
fi

# stale keybindings from older builds would shadow the new defaults
if [ -f run/options.txt ]; then
  grep -v '^key_key.craftguessr' run/options.txt > run/options.txt.tmp && mv run/options.txt.tmp run/options.txt
fi
docker rm -f geoguessr-client >/dev/null 2>&1
LAUNCH='cd /workspace/run && /opt/java/openjdk/bin/java -Xmx1200m -Dfabric.dli.config=/workspace/.gradle/loom-cache/launch.cfg -Dfabric.dli.env=client -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient @/workspace/build/loom-cache/argFiles/runClient --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -Dcraftguessr.softwareGL=1 net.fabricmc.devlaunchinjector.Main --quickPlayMultiplayer dev:25565 --width 1280 --height 720 --username Dev'
setsid docker compose run -e CRAFTGUESSR_LAUNCH="$LAUNCH" -e MC_SCREEN=1280x720x24 --rm --service-ports --name geoguessr-client client-vnc > /tmp/mc_client.log 2>&1 < /dev/null &
echo "client starting..."
for _ in $(seq 1 90); do
  grep -q "Dev\[" /tmp/mc_server.log 2>/dev/null && break
  sleep 2
done
sleep 2
printf 'op Dev\ndifficulty peaceful\ntime set day\n' > /tmp/mc_cmd
grep -a "logged in with entity" /tmp/mc_server.log | tail -1 | sed 's/\x1b\[[0-9;]*m//g'
echo "ready"
