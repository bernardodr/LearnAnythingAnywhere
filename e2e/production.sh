#!/usr/bin/env bash
# Tries the production stack end to end on this machine, before it goes anywhere near the server:
#   ./e2e/production.sh [screenshot dir]
#
# Copies deploy/ into a scratch folder laid out like /opt/laa on the server, writes it a trial .env (local http://
# addresses, fresh secrets, practice mode with no AI keys, emails kept in memory), and then, with the real deploy.sh:
#   1. builds both images from this working copy and deploys them (a first deploy: no database yet, nothing to back up);
#   2. runs e2e/production.mjs against them in headless Chrome (headers, caching, streaming, a learner's lesson);
#   3. deploys a second version: a backup is taken first, and it must be a readable Postgres dump;
#   4. deploys a broken version: it must fail its checks and roll itself back to the working one;
#   5. rolls back by hand, and the learner's textbook must still be there;
#   6. loses the data and restores it from the backup, with the commands in docs/DEPLOYING.md.
# Everything it started (containers, volumes, images, Chrome) is removed at the end. Needs Docker and Google Chrome
# (set CHROME if it isn't in the usual macOS place). Uses ports 8180, 8543 and 9333.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
OUT="${1:-$(mktemp -d)}"
HTTP_PORT=8180 HTTPS_PORT=8543 CDP_PORT=9333
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
WORK="$(mktemp -d)"
mkdir -p "$OUT"

for port in $HTTP_PORT $HTTPS_PORT $CDP_PORT; do
  if lsof -ti "tcp:$port" -sTCP:LISTEN >/dev/null 2>&1; then echo "Port $port is in use; stop whatever is on it first." >&2; exit 1; fi
done

# The server's layout: /opt/laa holds these three files and .env.
cp deploy/compose.yml deploy/compose.build.yml deploy/deploy.sh "$WORK/"
cat >"$WORK/.env" <<EOF
# The local trial's settings (e2e/production.sh). Its own project and images, so nothing else is touched.
COMPOSE_PROJECT_NAME=laa-trial
LAA_TAG=
LAA_PREVIOUS_TAG=
LAA_IMAGES=laa-trial
LAA_WEB_URL=http://localhost:$HTTP_PORT
LAA_API_URL=http://api.localhost:$HTTP_PORT
LAA_HTTP_PORT=$HTTP_PORT
LAA_HTTPS_PORT=$HTTPS_PORT
POSTGRES_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -hex 32)
LAA_DEFAULT_PROVIDER=FAKE
# For the trial only, never on the server: practice mode, dev sign-in, and emails kept in memory. Nothing is spent or sent.
LAA_PRACTICE_PROVIDERS=true
LAA_DEV_LOGIN=true
LAA_MAIL_OUTBOX=true
EOF
chmod 600 "$WORK/.env"

trial() { docker compose --project-directory "$WORK" -f "$WORK/compose.yml" "$@"; }
tag_of() { awk -F= -v k="$1" '$1 == k { print $2 }' "$WORK/.env"; }
CHROME_PID=
cleanup() {
  [ -z "$CHROME_PID" ] || kill "$CHROME_PID" 2>/dev/null || true
  trial down -v --remove-orphans >/dev/null 2>&1 || true
  docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -E '^laa-trial/' | xargs docker image rm >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT
step() { printf '\n━━ %s\n' "$*"; }
check() { printf '✓ %s\n' "$*"; }

step "1. Build both images from this working copy, and deploy them"
LAA_SOURCE="$ROOT" "$WORK/deploy.sh" build
FIRST="$(tag_of LAA_TAG)"
check "deployed $FIRST"

step "2. The stack, from outside (e2e/production.mjs)"
"$CHROME" --headless=new --remote-debugging-port=$CDP_PORT --user-data-dir="$WORK/chrome" --no-first-run about:blank >"$WORK/chrome.log" 2>&1 &
CHROME_PID=$!
for _ in $(seq 1 30); do curl -sf "localhost:$CDP_PORT/json/version" >/dev/null && break; sleep 0.5; done
API="http://api.localhost:$HTTP_PORT" APP="http://localhost:$HTTP_PORT/" node e2e/production.mjs "$OUT"

step "3. A second version: backed up first"
for image in laa-server laa-web; do docker tag "laa-trial/$image:$FIRST" "laa-trial/$image:second"; done
"$WORK/deploy.sh" second
[ "$(tag_of LAA_TAG)" = second ] && [ "$(tag_of LAA_PREVIOUS_TAG)" = "$FIRST" ] || { echo "✗ the tags in .env are wrong" >&2; exit 1; }
BACKUP="$(ls -1t "$WORK"/backups/before-second-*.dump | head -1)"
trial exec -T postgres pg_restore --list <"$BACKUP" | grep -q "TABLE DATA public textbooks" \
  || { echo "✗ the backup isn't a readable dump with the textbooks in it" >&2; exit 1; }
check "second is live, $FIRST is recorded as the one before, and the backup is a readable dump"

step "4. A broken version: it must roll itself back"
printf 'FROM laa-trial/laa-server:%s\nENTRYPOINT ["sh", "-c", "echo a broken server; exit 1"]\n' "$FIRST" \
  | docker build -q -t laa-trial/laa-server:broken - >/dev/null
docker tag "laa-trial/laa-web:$FIRST" laa-trial/laa-web:broken
if "$WORK/deploy.sh" broken; then echo "✗ the broken version was reported as deployed" >&2; exit 1; fi
[ "$(tag_of LAA_TAG)" = second ] || { echo "✗ LAA_TAG should be back on second" >&2; exit 1; }
curl -fsS "http://api.localhost:$HTTP_PORT/health" >/dev/null
check "the broken version failed its checks and second is live again"

step "5. Roll back by hand, and the data is still there"
"$WORK/deploy.sh" rollback
[ "$(tag_of LAA_TAG)" = "$FIRST" ] || { echo "✗ rollback should have gone back to $FIRST" >&2; exit 1; }
BOOKS="$(trial exec -T postgres psql -U laa -d laa -tAc 'SELECT count(*) FROM textbooks')"
[ "$BOOKS" -ge 1 ] || { echo "✗ the learner's textbook is gone" >&2; exit 1; }
check "back on $FIRST, with the learner's textbook still there ($BOOKS)"

step "6. Restore a backup, as docs/DEPLOYING.md says"
# Lose the data first, so the restore has something to bring back.
trial exec -T postgres psql -q -U laa -d laa -c 'TRUNCATE textbooks CASCADE'
trial stop server
trial exec -T postgres pg_restore -U laa -d laa --clean --if-exists --single-transaction <"$BACKUP"
trial start server
for _ in $(seq 1 60); do curl -fsS "http://api.localhost:$HTTP_PORT/health" >/dev/null 2>&1 && break; sleep 2; done
curl -fsS "http://api.localhost:$HTTP_PORT/health" >/dev/null || { echo "✗ the server didn't come back after the restore" >&2; exit 1; }
BOOKS="$(trial exec -T postgres psql -U laa -d laa -tAc 'SELECT count(*) FROM textbooks')"
[ "$BOOKS" -ge 1 ] || { echo "✗ the restore didn't bring the textbook back" >&2; exit 1; }
check "the backup restored the lost textbook, and the server is healthy on it"

printf '\n✓ The production stack works end to end. Screenshots: %s\n' "$OUT"
