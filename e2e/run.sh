#!/usr/bin/env bash
# Runs a browser walk-through against the real app: ./e2e/run.sh [diagrams] [screenshot dir]
#
# Starts, on its own ports and a scratch database (laa_e2e, recreated each run): the server (practice mode, no AI keys,
# so nothing is spent; email to Mailpit), the exported web app, and headless Chrome. Stops them all afterwards.
# Needs Docker (Postgres and Mailpit from docker-compose.yml), Java 21, Node 22+ and Google Chrome
# (set CHROME to its path if it isn't in the usual macOS place).
set -euo pipefail
cd "$(dirname "$0")/.."

TEST="${1:-diagrams}"
OUT="${2:-$(mktemp -d)}"
API_PORT=8090 WEB_PORT=8099 CDP_PORT=9333
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
WORK="$(mktemp -d)"
mkdir -p "$OUT"

for port in $API_PORT $WEB_PORT $CDP_PORT; do
  if lsof -ti "tcp:$port" -sTCP:LISTEN >/dev/null 2>&1; then echo "Port $port is in use; stop whatever is on it first." >&2; exit 1; fi
done
[ -f .env ] || { echo "Missing .env" >&2; exit 1; }

PIDS=()
cleanup() { for pid in "${PIDS[@]:-}"; do kill "$pid" 2>/dev/null || true; done; wait 2>/dev/null || true; }
trap cleanup EXIT

echo "▸ Database (laa_e2e) and Mailpit…"
docker compose up -d --wait postgres mailpit >/dev/null
docker compose exec -T postgres psql -q -U laa -d laa -c "DROP DATABASE IF EXISTS laa_e2e" -c "CREATE DATABASE laa_e2e" >/dev/null

echo "▸ Building the server and exporting the web app…"
(cd server && ./mvnw -q -B -DskipTests package)
JAR=$(ls server/target/server-*.jar | grep -v plain | head -1)
(cd app && EXPO_PUBLIC_API_URL="http://localhost:$API_PORT" npx expo export --platform web --output-dir "$WORK/web" >"$WORK/export.log" 2>&1)

echo "▸ Starting the server, the web app and Chrome…"
( set -a; source .env; set +a
  # Practice mode only, and no keys at all: a walk-through can never spend money or send real email.
  unset ANTHROPIC_API_KEY OPENAI_API_KEY GEMINI_API_KEY SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD
  export DATABASE_URL="jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/laa_e2e" SERVER_PORT=$API_PORT \
    SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT="${MAILPIT_SMTP_PORT:-1025}" SPRING_MAIL_SSL_ENABLED=false \
    LAA_DEV_LOGIN=true LAA_PRACTICE_PROVIDERS=true LAA_DEFAULT_PROVIDER=FAKE \
    CORS_ALLOWED_ORIGINS="http://localhost:$WEB_PORT" LAA_APP_URL="http://localhost:$WEB_PORT"
  exec java -jar "$JAR" >"$WORK/server.log" 2>&1 ) &
PIDS+=($!)
node e2e/lib/serve.mjs "$WORK/web" $WEB_PORT >"$WORK/web.log" 2>&1 &
PIDS+=($!)
"$CHROME" --headless=new --remote-debugging-port=$CDP_PORT --user-data-dir="$WORK/chrome" --no-first-run about:blank >"$WORK/chrome.log" 2>&1 &
PIDS+=($!)
for _ in $(seq 1 90); do
  curl -sf "localhost:$API_PORT/health" >/dev/null && curl -sf "localhost:$CDP_PORT/json/version" >/dev/null && break
  sleep 1
done
curl -sf "localhost:$API_PORT/health" >/dev/null || { echo "The server didn't start; see $WORK/server.log" >&2; exit 1; }

echo "▸ Running e2e/$TEST.mjs (screenshots in $OUT)…"
API="http://localhost:$API_PORT" APP="http://localhost:$WEB_PORT/" node "e2e/$TEST.mjs" "$OUT"
