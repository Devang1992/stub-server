#!/usr/bin/env bash
# Run from the folder that holds qa-hub.jar (and optionally ./config/*.yml overrides).
set -euo pipefail
cd "$(dirname "$0")"
PORT="${PORT:-8080}"
if [ -f qa-hub.pid ] && kill -0 "$(cat qa-hub.pid)" 2>/dev/null; then
  echo "Already running (pid $(cat qa-hub.pid))"; exit 0
fi
nohup java -Xms128m -Xmx512m -jar qa-hub.jar --server.port="$PORT" > qa-hub.log 2>&1 &
echo $! > qa-hub.pid
echo "Started on port $PORT (pid $!). Logs: $(pwd)/qa-hub.log"
