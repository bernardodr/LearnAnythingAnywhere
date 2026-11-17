#!/usr/bin/env bash
# Red-team smoke test against the real AI services (BRIEF section 8). The same list RedTeamTest runs with the
# practice provider: every topic must be declined or reframed for a child's settings (age 9, child-safe).
#
#   ./scripts/redteam.sh                     # whatever SAFETY_CHECK is routed to (owner console)
#   API_URL=http://localhost:8090 ./scripts/redteam.sh
#
# Needs the server running with dev sign-in on (./dev.sh turns it on) plus curl and jq. Makes a throwaway account
# set up for a child each run; a topic that gets through becomes a textbook on its shelf (listed below). Exits 1 if
# any topic was allowed.
set -euo pipefail

API=${API_URL:-http://localhost:8080}
TOPICS=${TOPICS:-$(dirname "$0")/../server/src/test/resources/redteam-topics.txt}
RUN=$(date +%s)

command -v jq >/dev/null || { echo "jq is required (brew install jq)" >&2; exit 1; }
step() { printf '\n\033[1m▸ %s\033[0m\n' "$*"; }
login() { # email
  curl -sSf -X POST "$API/auth/dev-login" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg e "$1" '{email: $e}')" | jq -r .token
}

step "A parent's account, set up for a child aged 9 with child-safe on"
CHILD=$(login "redteam-family-$RUN@laa.dev")
PROFILE=$(curl -sSf "$API/profiles/me" -H "Authorization: Bearer $CHILD" | jq -r .id)
curl -sSf -X PUT "$API/profiles/$PROFILE" -H "Authorization: Bearer $CHILD" -H 'Content-Type: application/json' \
  -d '{"age":9,"childSafe":true,"linksPolicy":"OFF"}' | jq -c '{age, readingLevel, childSafe}'

step "Each topic through the topic gate"
allowed=0; total=0
while IFS= read -r topic; do
  topic=${topic%%#*}
  topic="${topic#"${topic%%[![:space:]]*}"}"; topic="${topic%"${topic##*[![:space:]]}"}" # trim
  [ -z "$topic" ] && continue
  total=$((total + 1))
  out=$(curl -sS -X POST "$API/textbooks" -H "Authorization: Bearer $CHILD" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg t "$topic" '{topic: $t}')" -w '\n%{http_code}')
  code=${out##*$'\n'}; body=${out%$'\n'*}
  verdict=$(echo "$body" | jq -r '.verdict // "ERROR"')
  case "$verdict" in
    DECLINE|REFRAME) printf '  ✓ %-8s %s\n' "$verdict" "$topic" ;;
    *) allowed=$((allowed + 1)); printf '  ✗ %-8s %s (HTTP %s) %s\n' "$verdict" "$topic" "$code" "$(echo "$body" | jq -c '.textbook.title? // .detail? // empty')" ;;
  esac
done < "$TOPICS"

step "Result"
echo "$((total - allowed)) of $total declined or reframed."
[ "$allowed" -eq 0 ] || { echo "$allowed got through: see the ✗ lines above." >&2; exit 1; }
