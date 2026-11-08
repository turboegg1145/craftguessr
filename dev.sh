#!/usr/bin/env bash
# Control script for the Docker-based Fabric dev environment (Minecraft 26.3).
set -euo pipefail
cd "$(dirname "$0")"

DC="docker compose"
IMAGE="geoguessr:26.3-java25"
DEV_CT="geoguessr-dev"
CLIENT_CT="geoguessr-client"

c_blue=$'\033[1;34m'; c_dim=$'\033[2m'; c_red=$'\033[1;31m'; c_off=$'\033[0m'
say() { printf '%s==>%s %s\n' "$c_blue" "$c_off" "$*"; }
die() { printf '%s!!%s %s\n' "$c_red" "$c_off" "$*" >&2; exit 1; }

cmd_up() {
  say "building image + starting toolbox container"
  $DC up -d --build dev
  $DC exec dev bash -lc 'java -version 2>&1 | head -2; echo "gradle $(gradle --version | awk "/^Gradle /{print \$2}")"' || true
  say "ready. try: ./dev.sh build"
}

dev_up() { docker inspect -f '{{.State.Running}}' "$DEV_CT" 2>/dev/null | grep -q true || cmd_up >/dev/null; }

cmd_build() {
  dev_up
  say "gradlew build $*"
  $DC exec dev ./gradlew build --console=plain "$@"
  say "jar: build/libs/"
  ls -1 build/libs 2>/dev/null || true
}

cmd_test()   { dev_up; say "gradlew test"; $DC exec dev ./gradlew test --console=plain "$@"; }
cmd_gen()    { dev_up; say "gradlew genSources (heavy: ~1-3 GB RAM, several minutes)"; $DC exec dev ./gradlew genSources --console=plain "$@"; }
cmd_gradle() { dev_up; say "gradlew $*"; $DC exec dev ./gradlew "$@"; }
cmd_shell()  { dev_up; exec docker exec -it "$DEV_CT" bash; }

cmd_server() {
  dev_up
  mkdir -p run
  [ -f run/eula.txt ] || printf 'eula=true\n' > run/eula.txt
  say "runServer (ctrl-c to stop)"
  $DC exec dev ./gradlew runServer --console=plain "$@"
}

cmd_client() {
  say "runClient on the VM's X display ${HOST_DISPLAY:-:1} -- watch it in your existing VNC desktop (ctrl-c to quit)"
  docker rm -f "$CLIENT_CT" >/dev/null 2>&1 || true
  exec $DC run --rm --service-ports --name "$CLIENT_CT" client "$@"
}

cmd_clientvnc() {
  say "runClient on a private Xvfb inside the container -- open http://<vm-ip>:6083/vnc.html (ctrl-c to quit)"
  docker rm -f "$CLIENT_CT" >/dev/null 2>&1 || true
  exec $DC run --rm --service-ports --name "$CLIENT_CT" client-vnc "$@"
}

cmd_shot() {
  docker inspect -f '{{.State.Running}}' "$CLIENT_CT" 2>/dev/null | grep -q true || die "client container not running (./dev.sh client)"
  mkdir -p run
  local disp
  disp=$(docker exec "$CLIENT_CT" printenv DISPLAY 2>/dev/null || echo ":0")
  case "$disp" in
    :9*) # own Xvfb inside the container
      docker exec -e DISPLAY="$disp" "$CLIENT_CT" scrot -o /workspace/run/screenshot.png ;;
    *)   # the VM's X display, reachable through the mounted socket
      docker run --rm -e DISPLAY="$disp" -v /tmp/.X11-unix:/tmp/.X11-unix \
        -v "$(pwd)/run:/workspace/run" --entrypoint scrot "$IMAGE" -o /workspace/run/screenshot.png ;;
  esac
  say "screenshot -> $(pwd)/run/screenshot.png"
}

cmd_logs()  { $DC logs -f --tail=200 dev; }
cmd_ps()    { $DC ps; docker ps --filter "name=$CLIENT_CT" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'; }
cmd_stop()  { docker rm -f "$CLIENT_CT" >/dev/null 2>&1 || true; $DC down; say "stopped (caches kept)"; }
cmd_destroy() {
  $DC down -v --remove-orphans
  docker rmi -f geoguessr:26.3-java25 >/dev/null 2>&1 || true
  say "removed container, volumes and image. disk reclaimed."
}

cmd_help() {
  cat <<EOF
Docker Fabric dev env for Minecraft 26.3

  ./dev.sh up          build image & start the toolbox container
  ./dev.sh build       ./gradlew build   -> build/libs/*.jar
  ./dev.sh test        ./gradlew test
  ./dev.sh gw <args>   run any gradle task, e.g. ./dev.sh gw tasks
  ./dev.sh gen         ./gradlew genSources (IDE decompiled sources)
  ./dev.sh shell       bash inside the container
  ./dev.sh client      runClient on the VM's X display (:1) - visible in your VNC desktop
  ./dev.sh clientvnc   runClient on a container-only Xvfb, browser view on :6083
  ./dev.sh shot        screenshot the running client -> run/screenshot.png
  ./dev.sh server      ./gradlew runServer (EULA auto-accepted)
  ./dev.sh ps          show containers
  ./dev.sh logs        follow the toolbox logs
  ./dev.sh stop        remove containers (caches/volumes kept)
  ./dev.sh destroy     remove containers + volumes + image
EOF
}

case "${1:-help}" in
  up) cmd_up ;;
  build) shift; cmd_build "$@" ;;
  test) shift; cmd_test "$@" ;;
  gw) shift; cmd_gradle "$@" ;;
  gen) shift; cmd_gen "$@" ;;
  shell) cmd_shell ;;
  client) shift; cmd_client "$@" ;;
  clientvnc) shift; cmd_clientvnc "$@" ;;
  shot) cmd_shot ;;
  server) shift; cmd_server "$@" ;;
  ps) cmd_ps ;;
  logs) cmd_logs ;;
  stop) cmd_stop ;;
  destroy) cmd_destroy ;;
  *) cmd_help ;;
esac
