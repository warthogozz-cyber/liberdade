#!/usr/bin/env bash
# Exercises deploy.sh against real containers: a healthy first deploy, a bad release that must roll back,
# and a bad first deploy that has nothing to roll back to. Needs docker and a built API image.
# Usage: deploy-test.sh [good-api-image]   (default: liberdade-api-ci:local)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY="$(dirname "$HERE")"
GOOD_BASE="${1:-liberdade-api-ci:local}"
IMAGE=liberdade-api-ci
GOOD_TAG="sha-$(printf 'a%.0s' {1..40})"
BAD_TAG="sha-$(printf 'b%.0s' {1..40})"
WORK="$(mktemp -d)"
TAGS_FILE="$WORK/root/tags.env"
export CI_ENV_FILE="$WORK/api.env"
export LIBERDADE_ENV_SOURCE=file
export LIBERDADE_SKIP_CADDY=1
export LIBERDADE_COMPOSE_EXTRA="$HERE/compose.ci.yaml"
FAILURES=0

dc() {
  docker compose --project-directory "$DEPLOY" -f "$DEPLOY/compose.yaml" -f "$HERE/compose.ci.yaml" \
    --env-file "$TAGS_FILE" "$@"
}

cleanup() {
  dc down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

check() {
  local what="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "ok   - $what"
  else
    echo "FAIL - $what (expected '$expected', got '$actual')" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

container_health() {
  local cid
  cid="$(dc ps -q api-production)"
  if [ -z "$cid" ]; then
    echo none
  else
    docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid"
  fi
}

tag_in_file() {
  grep -E "^PRODUCTION_TAG=" "$TAGS_FILE" | cut -d= -f2- || true
}

run_deploy() {
  local tag="$1" timeout="$2" code=0
  HEALTH_TIMEOUT_SECONDS="$timeout" LIBERDADE_ROOT="$WORK/root" bash "$DEPLOY/deploy.sh" production "$tag" "$DEPLOY" 2>&1 | tee "$WORK/deploy.log" || code=${PIPESTATUS[0]}
  return "$code"
}

fresh_root() {
  dc rm -sf api-production >/dev/null 2>&1 || true
  rm -rf "$WORK/root"
  mkdir -p "$WORK/root"
  printf 'API_IMAGE=%s\n' "$IMAGE" >"$TAGS_FILE"
}

# Test values only, no secrets.
cat >"$CI_ENV_FILE" <<ENVFILE
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/liberdade
SPRING_DATASOURCE_USERNAME=liberdade
SPRING_DATASOURCE_PASSWORD=ci-only
GOOGLE_CLIENT_ID=ci-client
GOOGLE_CLIENT_SECRET=ci-secret
WEB_ORIGIN=https://app.example.com
WEBAUTHN_RP_ID=example.com
ENVFILE

docker tag "$GOOD_BASE" "$IMAGE:$GOOD_TAG"
docker build -q -f "$HERE/Dockerfile.unhealthy" --build-arg "BASE_IMAGE=$GOOD_BASE" -t "$IMAGE:$BAD_TAG" "$HERE" >/dev/null

fresh_root
dc up -d --wait db

echo "== scenario 1: first deploy, healthy"
code=0
run_deploy "$GOOD_TAG" 120 || code=$?
check "exit code" 0 "$code"
check "tags.env has the good tag" "$GOOD_TAG" "$(tag_in_file)"
check "container is healthy" healthy "$(container_health)"

echo "== scenario 2: bad release rolls back to the good one"
code=0
run_deploy "$BAD_TAG" 30 || code=$?
check "exit code" 1 "$code"
check "output says it rolled back" "rolled back to $GOOD_TAG" "$(grep -x "rolled back to .*" "$WORK/deploy.log" || true)"
check "tags.env is back on the good tag" "$GOOD_TAG" "$(tag_in_file)"
check "container is healthy again" healthy "$(container_health)"

echo "== scenario 3: bad first deploy, nothing to roll back to"
fresh_root
code=0
run_deploy "$BAD_TAG" 30 || code=$?
check "exit code" 1 "$code"
check "tags.env has no production tag" "" "$(tag_in_file)"
check "no container left running" none "$(container_health)"

if [ "$FAILURES" -ne 0 ]; then
  echo "$FAILURES check(s) failed" >&2
  exit 1
fi
echo "all deploy scenarios passed"
