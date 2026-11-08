#!/usr/bin/env bash
# Runs the dev Minecraft client inside a virtual X server and exposes the
# screen over VNC (5900) / noVNC (6080 in the container, published by compose).
set -euo pipefail

export DISPLAY="${DISPLAY:-:99}"
: "${MC_SCREEN:=1600x900x24}"
: "${NOVNC_PORT:=6080}"

echo "[client] virtual display ${DISPLAY} (${MC_SCREEN})"

# Clean up any stale X lock from a previous run in the same container.
rm -f "/tmp/.X${DISPLAY#:}-lock" 2>/dev/null || true
Xvfb "$DISPLAY" -screen 0 "$MC_SCREEN" -nolisten tcp -ac +extension GLX +render &
XVFB_PID=$!

X_SOCKET="/tmp/.X11-unix/X${DISPLAY#:}"
for _ in $(seq 1 100); do
  if [ -S "$X_SOCKET" ]; then break; fi
  sleep 0.2
done
[ -S "$X_SOCKET" ] || { echo "[client] Xvfb failed to start (no $X_SOCKET)"; exit 1; }
echo "[client] Xvfb up (pid ${XVFB_PID})"
glxinfo -B 2>/dev/null | sed -n '1,6p' || true

# VNC server + browser client (open http://<vm-ip>:6080/vnc.html)
x11vnc -display "$DISPLAY" -forever -shared -nopw -rfbport 5900 -quiet -bg -o /tmp/x11vnc.log || true
websockify --web=/usr/share/novnc "$NOVNC_PORT" localhost:5900 >/tmp/websockify.log 2>&1 &
echo "[client] noVNC: http://<vm-ip>:${NOVNC_PORT}/vnc.html  (raw VNC on 5900)"

cleanup() { kill "$XVFB_PID" 2>/dev/null || true; }
trap cleanup EXIT

# Hand over to Gradle; args are forwarded, e.g. `... --args="--username Dev"`.
if [ -n "${CRAFTGUESSR_LAUNCH:-}" ]; then
  # Pre-built launch command (used to run the client while Gradle is busy elsewhere).
  exec bash -lc "$CRAFTGUESSR_LAUNCH"
fi
exec ./gradlew runClient --console=plain "$@"
