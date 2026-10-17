#!/usr/bin/env bash
# Builds the UI into the backend, then packages one runnable JAR.
set -euo pipefail
cd "$(dirname "$0")"
(cd frontend && npm ci && npm run build)
(cd backend && mvn -q clean package -DskipTests)
echo "Built backend/target/qa-hub.jar"
