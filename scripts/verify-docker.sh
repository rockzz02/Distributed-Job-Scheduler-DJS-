#!/usr/bin/env sh
set -eu

COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-djs}"
HEALTH_URL="${DJS_HEALTH_URL:-http://localhost:8080/actuator/health}"
export COMPOSE_PROJECT_NAME

echo "Validating docker-compose.yml"
docker compose config --quiet

echo "Building and starting local stack"
docker compose up --build -d

echo "Waiting for application health at $HEALTH_URL"
i=0
while [ "$i" -lt 60 ]; do
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    echo "DJS is healthy"
    docker compose ps
    exit 0
  fi
  i=$((i + 1))
  sleep 2
done

echo "DJS did not become healthy in time"
docker compose ps
docker compose logs --tail=200 djs-app
exit 1
