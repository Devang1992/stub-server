#!/usr/bin/env bash
cd "$(dirname "$0")"
if [ -f qa-hub.pid ]; then kill "$(cat qa-hub.pid)" 2>/dev/null && echo "Stopped"; rm -f qa-hub.pid; else echo "Not running"; fi
