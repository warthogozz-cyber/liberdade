#!/usr/bin/env bash
# Usage: deploy.sh <staging|production> <tag> <release-dir>
# Exit 0: the new version is healthy. Exit 1: rolled back to the previous version, or there was none.
# Never prints the environment or the contents of the secret file.
#
# Test-only variables: LIBERDADE_ROOT (default /opt/liberdade), LIBERDADE_ENV_SOURCE (ssm, the default,
# or file: the env file already exists), LIBERDADE_COMPOSE_EXTRA (one more Compose file),
# LIBERDADE_SKIP_CADDY=1, LIBERDADE_ENV_DIR (default /run/liberdade), HEALTH_TIMEOUT_SECONDS (default 180).
# ROLLBACK_TIMEOUT_SECONDS (default 180) bounds the wait after a rollback.
# LOCK_WAIT_SECONDS (default 300) bounds the wait for another deploy holding the host lock.
set -euo pipefail

ROOT="${LIBERDADE_ROOT:-/opt/liberdade}"
ENV_SOURCE="${LIBERDADE_ENV_SOURCE:-ssm}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT_SECONDS:-180}"
ROLLBACK_TIMEOUT="${ROLLBACK_TIMEOUT_SECONDS:-180}"
LOCK_WAIT="${LOCK_WAIT_SECONDS:-300}"
ENV_DIR="${LIBERDADE_ENV_DIR:-/run/liberdade}"
TAGS="$ROOT/tags.env"

die() {
  echo "deploy: $*" >&2
  exit 1
}

# 1. Arguments and tag format.
[ "$#" -eq 3 ] || die "usage: deploy.sh <staging|production> <tag> <release-dir>"
ENVIRONMENT="$1"
TAG="$2"
RELEASE="$3"
case "$ENVIRONMENT" in
  staging) TAG_KEY=STAGING_TAG ;;
  production) TAG_KEY=PRODUCTION_TAG ;;
  *) die "environment must be staging or production" ;;
esac
SERVICE="api-$ENVIRONMENT"
[[ "$TAG" =~ ^sha-[0-9a-f]{40}$ ]] || die "tag must look like sha-<40 hex characters>"
if ! [ -f "$RELEASE/compose.yaml" ] || ! [ -f "$RELEASE/Caddyfile" ]; then
  die "release folder needs compose.yaml and Caddyfile"
fi
case "$ENV_SOURCE" in ssm | file) ;; *) die "LIBERDADE_ENV_SOURCE must be ssm or file" ;; esac
[[ "$HEALTH_TIMEOUT" =~ ^[0-9]+$ ]] || die "HEALTH_TIMEOUT_SECONDS must be a number"
[[ "$ROLLBACK_TIMEOUT" =~ ^[0-9]+$ ]] || die "ROLLBACK_TIMEOUT_SECONDS must be a number"
[[ "$LOCK_WAIT" =~ ^[0-9]+$ ]] || die "LOCK_WAIT_SECONDS must be a number"

# One deploy at a time: both environments share tags.env and the Caddyfile.
mkdir -p "$ROOT/releases"
exec 9>"$ROOT/.deploy.lock"
flock -w "$LOCK_WAIT" 9 || die "another deploy is still running after ${LOCK_WAIT}s"

dc() {
  local -a files=(-f "$ROOT/compose.yaml")
  if [ -n "${LIBERDADE_COMPOSE_EXTRA:-}" ]; then
    files+=(-f "$LIBERDADE_COMPOSE_EXTRA")
  fi
  docker compose --project-directory "$ROOT" "${files[@]}" --env-file "$TAGS" "$@"
}

get_tag() {
  local line
  line="$(grep -E "^$1=" "$TAGS" || true)"
  printf '%s' "${line#*=}"
}

# Rewrites tags.env atomically; with a value, sets the key, without one, removes it.
set_tag() {
  local tmp
  tmp="$(mktemp "$ROOT/tags.env.XXXXXX")"
  grep -vE "^$1=" "$TAGS" >"$tmp" || true
  if [ "$#" -ge 2 ]; then
    printf '%s=%s\n' "$1" "$2" >>"$tmp"
  fi
  mv -f "$tmp" "$TAGS"
}

# Waits for the container health status. Fails fast on unhealthy and at the deadline.
wait_healthy() {
  local deadline=$((SECONDS + $1)) cid status
  while :; do
    cid="$(dc ps -q "$SERVICE")"
    status=none
    if [ -n "$cid" ]; then
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid")"
    fi
    case "$status" in
      healthy) return 0 ;;
      unhealthy) return 1 ;;
    esac
    [ "$SECONDS" -lt "$deadline" ] || return 1
    sleep 2
  done
}

# 2. Validate the release's Compose file. Nothing is installed on the host until step 3 has succeeded.
docker compose -f "$RELEASE/compose.yaml" config -q
touch "$TAGS"

# 3. Environment file from SSM, one NAME='value' line per parameter. Prints nothing about values.
# Single quotes are literal in the Compose dotenv format; unquoted, it would expand $VAR, cut at " #" and
# trim whitespace, silently corrupting passwords. A value that cannot be single-quoted fails the deploy,
# and the message names only the parameter.
if [ "$ENV_SOURCE" = ssm ]; then
  (
    umask 077
    mkdir -p "$ENV_DIR"
    tmp="$(mktemp "$ENV_DIR/$ENVIRONMENT.env.XXXXXX")"
    trap 'rm -f "$tmp"' EXIT
    last="(none)"
    aws ssm get-parameters-by-path --path "/liberdade/$ENVIRONMENT/" --with-decryption --recursive \
      --query 'Parameters[].[Name,Value]' --output text |
      while IFS= read -r line; do
        if [[ "$line" != *$'\t'* ]]; then
          echo "deploy: unexpected SSM output after parameter $last (a value with a line break?)" >&2
          exit 1
        fi
        name="${line%%$'\t'*}"
        name="${name##*/}"
        value="${line#*$'\t'}"
        if [[ "$value" == *"'"* || "$value" == *$'\r'* ]]; then
          echo "deploy: the value of parameter $name has a single quote or a carriage return" >&2
          exit 1
        fi
        printf "%s='%s'\n" "$name" "$value"
        last="$name"
      done >"$tmp"
    [ -s "$tmp" ] || {
      echo "deploy: no SSM parameters under /liberdade/$ENVIRONMENT/" >&2
      exit 1
    }
    chmod 600 "$tmp"
    # Keep the current file for a rollback (same 0600 mode, from umask 077). A first deploy has none.
    if [ -f "$ENV_DIR/$ENVIRONMENT.env" ]; then
      cp -p "$ENV_DIR/$ENVIRONMENT.env" "$ENV_DIR/$ENVIRONMENT.env.previous"
    else
      rm -f "$ENV_DIR/$ENVIRONMENT.env.previous"
    fi
    mv -f "$tmp" "$ENV_DIR/$ENVIRONMENT.env"
  )
fi

# Install the host files, keeping the previous ones for a rollback. cp rewrites the same inode, which keeps
# the single-file bind mount in Caddy pointing at the new content.
HOST_FILES=(compose.yaml Caddyfile)
rm -rf "$ROOT/.previous"
mkdir -p "$ROOT/.previous"
for f in "${HOST_FILES[@]}"; do
  if [ -f "$ROOT/$f" ]; then
    cp -f "$ROOT/$f" "$ROOT/.previous/$f"
  fi
  cp -f "$RELEASE/$f" "$ROOT/$f"
done

restore_host_files() {
  local f
  for f in "${HOST_FILES[@]}"; do
    if [ -f "$ROOT/.previous/$f" ]; then
      cp -f "$ROOT/.previous/$f" "$ROOT/$f"
    fi
  done
}

# Puts the previous environment file back, if this deploy kept one.
restore_env_file() {
  if [ -f "$ENV_DIR/$ENVIRONMENT.env.previous" ]; then
    cp -p "$ENV_DIR/$ENVIRONMENT.env.previous" "$ENV_DIR/$ENVIRONMENT.env"
  fi
}

# 4 and 5. Switch the tag, pull, start, and reload Caddy. Any failure here counts as an unhealthy deploy.
switch_to() {
  # Chained with && because errexit is off inside functions that run in an if condition.
  set_tag "$TAG_KEY" "$1" &&
    dc pull --quiet --policy missing "$SERVICE" &&
    dc up -d --no-deps "$SERVICE"
}

# up -d is idempotent: it recreates Caddy only when its image or config changed, so a digest bump applies.
reload_caddy() {
  if [ "${LIBERDADE_SKIP_CADDY:-0}" = 1 ]; then
    return 0
  fi
  dc up -d --no-deps caddy &&
    dc exec -T caddy caddy reload --config /etc/caddy/Caddyfile
}

PREVIOUS="$(get_tag "$TAG_KEY")"

if switch_to "$TAG" && reload_caddy && wait_healthy "$HEALTH_TIMEOUT"; then
  # 8. Housekeeping: old unused images, and only the 5 newest releases. Never fails a healthy deploy.
  docker image prune -af --filter "until=168h" >/dev/null || true
  {
    find "$ROOT/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -rn | tail -n +6 | cut -d' ' -f2- |
      while IFS= read -r old; do
        if ! [ "$old" -ef "$RELEASE" ]; then
          rm -rf -- "$old" || true
        fi
      done
  } || true
  echo "deployed $TAG to $ENVIRONMENT"
  exit 0
fi

# 7. Roll back: the previous tag, and the previous host files with a Caddy reload.
echo "deploy: $TAG did not become healthy in $ENVIRONMENT" >&2
if [ -z "$PREVIOUS" ]; then
  set_tag "$TAG_KEY"
  dc rm -sf "$SERVICE" || true
  restore_host_files
  reload_caddy || true
  die "no previous release to roll back to"
fi
restore_host_files
restore_env_file
switch_to "$PREVIOUS" || true
reload_caddy || true
wait_healthy "$ROLLBACK_TIMEOUT" || echo "deploy: the previous release is not healthy either" >&2
echo "rolled back to $PREVIOUS"
exit 1
