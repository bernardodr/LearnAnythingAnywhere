#!/usr/bin/env bash
# Starts PostgreSQL, the Spring Boot server and the Expo web app. Ctrl-C stops the server and app
# (Postgres keeps running; stop it with `docker compose down`).
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { echo "Missing .env — copy .env.example to .env first." >&2; exit 1; }
set -a; source .env; set +a   # exported so the server and Expo (EXPO_PUBLIC_*) both see them

echo "▸ Starting PostgreSQL…"
docker compose up -d --wait postgres

[ -d app/node_modules ] || (cd app && npm install)

echo "▸ Starting server on :8080…"
(cd server && ./mvnw -q spring-boot:run) &
SERVER_PID=$!
trap 'kill $SERVER_PID 2>/dev/null || true' EXIT INT TERM

for _ in $(seq 1 90); do
  curl -sf http://localhost:8080/health >/dev/null && break
  kill -0 $SERVER_PID 2>/dev/null || { echo "Server exited during startup." >&2; exit 1; }
  sleep 1
done
curl -sf http://localhost:8080/health >/dev/null || { echo "Server did not become healthy." >&2; exit 1; }
echo "▸ Server healthy."

echo "▸ Starting Expo (web: press w, or open the URL below; phone: scan the QR code in Expo Go)…"
cd app && npx expo start "${@:---web}"
