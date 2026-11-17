#!/usr/bin/env bash
# Usage: smoke.sh <api-url> <web-url>
# Exit 0 when the API is ready, /v1/me answers 401 as problem+json, and the web app sends a CSP that
# allows the API origin.
set -euo pipefail

[ "$#" -eq 2 ] || {
  echo "usage: smoke.sh <api-url> <web-url>" >&2
  exit 2
}
API="${1%/}"
WEB="${2%/}"
BODY="$(mktemp)"
HEADERS="$(mktemp)"
trap 'rm -f "$BODY" "$HEADERS"' EXIT

fail() {
  echo "smoke: $*" >&2
  exit 1
}

# Prints the status code; body and headers go to the temp files.
fetch() {
  curl -sS --max-time 15 -o "$BODY" -D "$HEADERS" -w '%{http_code}' "$1" || true
}

ready=0
for attempt in $(seq 1 10); do
  status="$(fetch "$API/actuator/health/readiness")"
  if [ "$status" = 200 ] && grep -q '"UP"' "$BODY"; then
    ready=1
    break
  fi
  echo "smoke: API not ready yet (attempt $attempt of 10, status $status)"
  [ "$attempt" -eq 10 ] || sleep 6
done
[ "$ready" -eq 1 ] || fail "readiness never answered 200 with UP"
echo "smoke: readiness ok"

status="$(fetch "$API/v1/me")"
[ "$status" = 401 ] || fail "/v1/me answered $status, expected 401"
grep -qi '^content-type:.*application/problem+json' "$HEADERS" || fail "/v1/me did not answer application/problem+json"
echo "smoke: /v1/me ok"

status="$(fetch "$WEB/")"
[ "$status" = 200 ] || fail "web root answered $status, expected 200"
csp="$(grep -i '^content-security-policy:' "$HEADERS" || true)"
[ -n "$csp" ] || fail "web root sent no content-security-policy"
case "$csp" in
  *"$API"*) ;;
  *) fail "the content-security-policy does not mention the API origin" ;;
esac
echo "smoke: web ok"
