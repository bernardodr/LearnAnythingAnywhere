#!/usr/bin/env bash
# Starts PostgreSQL, the Spring Boot server and the Expo web app. Ctrl-C stops the server and app
# (Postgres keeps running; stop it with `docker compose down`).
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { echo "Missing .env — copy .env.example to .env first." >&2; exit 1; }
set -a; source .env; set +a   # exported so the server and Expo (EXPO_PUBLIC_*) both see them
export LAA_DEV_LOGIN="${LAA_DEV_LOGIN:-true}"   # email-only sign-in for local development (the server's default is off)

port_in_use() { lsof -ti "tcp:$1" -sTCP:LISTEN >/dev/null 2>&1; }

# An old server would answer /health below and hide that the new one failed to start.
if port_in_use 8080; then
  echo "Port 8080 is already in use, probably by a server from an earlier run. Stop it with:" >&2
  echo "  lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill" >&2
  exit 1
fi

echo "▸ Starting PostgreSQL…"
docker compose up -d --wait postgres

[ -d app/node_modules ] || (cd app && npm install)

# Background jobs in a script ignore Ctrl-C, and spring-boot:run runs the app in a second JVM,
# so stopping means killing the whole tree (subshell → Maven → app), then waiting for the port.
descendants() { local c; for c in $(pgrep -P "$1"); do echo "$c"; descendants "$c"; done; }
stop_server() {
  [ -n "${SERVER_PID:-}" ] || return 0
  local pids; pids="$SERVER_PID $(descendants "$SERVER_PID")"
  SERVER_PID=
  kill $pids 2>/dev/null || true
  for _ in $(seq 1 20); do port_in_use 8080 || break; sleep 0.5; done
}

echo "▸ Starting server on :8080…"
(cd server && ./mvnw -q spring-boot:run) &
SERVER_PID=$!
trap stop_server EXIT
trap 'exit 130' INT TERM

for _ in $(seq 1 90); do
  curl -sf http://localhost:8080/health >/dev/null && break
  kill -0 $SERVER_PID 2>/dev/null || { echo "Server exited during startup." >&2; exit 1; }
  sleep 1
done
curl -sf http://localhost:8080/health >/dev/null || { echo "Server did not become healthy." >&2; exit 1; }
echo "▸ Server healthy."

echo "▸ Starting Expo (web opens in your browser; phone or iPad: scan the QR code in Expo Go)…"
cd app && npx expo start "${@:---web}"
