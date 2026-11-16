#!/usr/bin/env bash
# M1 demo: switch the default LLM provider and show the next model_runs row follows it.
#
#   ./scripts/m1-switch-default.sh                 # FAKE -> FAKE_B (works with no API keys)
#   ./scripts/m1-switch-default.sh ANTHROPIC GOOGLE # between real providers (keys must be in .env)
#
# Needs the server running with dev sign-in on (./dev.sh turns it on) plus curl and jq. Leaves the default set to the second provider.
set -euo pipefail

FROM=${1:-FAKE}
TO=${2:-FAKE_B}
TASK=${TASK:-LESSON_PLAN}
API=${API_URL:-http://localhost:8080}
OWNER=${OWNER_EMAIL:-owner@laa.dev}

command -v jq >/dev/null || { echo "jq is required (brew install jq)" >&2; exit 1; }
step() { printf '\n\033[1m▸ %s\033[0m\n' "$*"; }
api() { # method path [json]
  local args=(-sS -X "$1" "$API$2" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -w '\n%{http_code}')
  [ $# -ge 3 ] && args+=(-d "$3")
  local out code; out=$(curl "${args[@]}"); code=${out##*$'\n'}; out=${out%$'\n'*}
  if [ "$code" -ge 400 ]; then echo "HTTP $code from $1 $2:" >&2; echo "$out" | jq . >&2 2>/dev/null || echo "$out" >&2; exit 1; fi
  echo "$out"
}

step "Sign in as owner ($OWNER)"
TOKEN=$(curl -sSf -X POST "$API/auth/dev-login" -H 'Content-Type: application/json' -d "{\"email\":\"$OWNER\"}" | jq -r .token)
echo "got a JWT"

step "Providers"
api GET /owner/providers | jq -r '.[] | [.id, (if .isDefault then "DEFAULT" else "-" end), (if .usable then "usable" else .unavailableReason end), "serves \(.tasksServed) tasks"] | @tsv' | column -t -s $'\t'

step "Make $FROM the default"
api PATCH "/owner/providers/$FROM" '{"isDefault": true}' | jq -c '{id, isDefault, tasksServed}'

step "Call $TASK (route follows DEFAULT)"
api POST /dev/llm/ping "{\"task\":\"$TASK\"}" | jq -c '{provider, model, status}'

step "Switch the default to $TO"
api PATCH "/owner/providers/$TO" '{"isDefault": true}' | jq -c '{id, isDefault, tasksServed}'

step "Call $TASK again"
api POST /dev/llm/ping "{\"task\":\"$TASK\"}" | jq -c '{provider, model, status}'

step "Latest model_runs rows for $TASK (newest first)"
RUNS=$(api GET "/owner/runs?task=$TASK&limit=2")
echo "$RUNS" | jq -r '.[] | [.createdAt, .task, .provider, .model, .status, "\(.latencyMs) ms"] | @tsv' | column -t -s $'\t'

NEWEST=$(echo "$RUNS" | jq -r '.[0].provider')
if [ "$NEWEST" = "$TO" ]; then
  printf '\n\033[32m✔ The next run used %s after the default switched.\033[0m\n' "$TO"
else
  printf '\n\033[31m✘ Expected the newest run on %s, got %s.\033[0m\n' "$TO" "$NEWEST"; exit 1
fi
