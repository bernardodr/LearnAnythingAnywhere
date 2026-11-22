#!/usr/bin/env bash
# Puts a version of LAA live on this machine, safely: back up the database, switch to the new images, wait until the
# server is healthy and both sites answer, and go back to the previous version by itself if anything fails.
#
#   ./deploy.sh build [ref]   build both images here from the repository (in ./src; ref: a branch, tag or commit,
#                             default main), then roll them out. For deploys before the deploy workflow exists.
#   ./deploy.sh <tag>         roll out images already built, pulling them from the registry if they aren't here
#   ./deploy.sh rollback      go back to the version before the current one
#   ./deploy.sh status        which version is live, and the containers' state
#
# It lives in /opt/laa beside compose.yml, compose.build.yml and .env (root only; from deploy/.env.example). The
# live version is LAA_TAG in .env and the one before it LAA_PREVIOUS_TAG; this script is what changes them.
# Backups go to ./backups (the last 10 deploys); the nightly backups are separate.
#
# Rolling back puts the previous *code* back. If the failed version had already changed the database (a new
# migration), the older code usually still runs, but the backup taken just before is there if it doesn't; restoring
# it is a deliberate step (docs/DEPLOYING.md).
#
# Works with the bash 3 that macOS ships, so the local trial (e2e/production.sh) runs this exact script.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$HERE/.env"
BACKUPS="$HERE/backups"
KEEP_BACKUPS=10
REPO="https://github.com/bernardodr/LearnAnythingAnywhere.git"
WAIT_SECONDS=180

say() { printf '▸ %s\n' "$*"; }
fail() { printf '✗ %s\n' "$*" >&2; exit 1; }

usage() { sed -n '5,10p' "$0" | sed 's/^# \{0,1\}//'; }

compose() { docker compose --project-directory "$HERE" -f "$HERE/compose.yml" "$@"; }

# ---- .env, read and changed in place. Rewriting through `cat >` keeps the file's owner and its root-only mode.
env_get() {
  awk -v key="$1" 'index($0, key "=") == 1 { v = substr($0, length(key) + 2); gsub(/^"|"$/, "", v); print v; exit }' "$ENV_FILE"
}
env_set() {
  local tmp; tmp="$(mktemp)"
  awk -v key="$1" -v value="$2" '
    index($0, key "=") == 1 { print key "=" value; done = 1; next }
    { print }
    END { if (!done) print key "=" value }' "$ENV_FILE" >"$tmp"
  cat "$tmp" >"$ENV_FILE"
  rm -f "$tmp"
}

# ---- Only one deploy at a time: a second would back up, switch and roll back underneath the first.
lock() {
  mkdir "$HERE/.deploy.lock" 2>/dev/null \
    || fail "Another deploy is running. If one was interrupted, remove $HERE/.deploy.lock and try again."
  trap 'rmdir "$HERE/.deploy.lock" 2>/dev/null || true' EXIT
}

# ---- A copy of the database before anything changes, in Postgres's own format (restore with pg_restore).
backup() {
  if [ -z "$(compose ps --status running -q postgres 2>/dev/null)" ]; then
    say "No database running yet, so there's nothing to back up."
    return
  fi
  mkdir -p "$BACKUPS" && chmod 700 "$BACKUPS"
  local file; file="$BACKUPS/before-$1-$(date -u +%Y%m%dT%H%M%SZ).dump"
  # Written under a temporary name first, so a half-written file never looks like a backup.
  compose exec -T postgres pg_dump -U laa -d laa -Fc >"$file.part" || fail "The backup failed, so nothing was changed."
  mv "$file.part" "$file"
  say "Database backed up to $file"
  # Keep the newest few.
  ls -1t "$BACKUPS"/before-*.dump 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) | while read -r old; do rm -f "$old"; done || true
}

# ---- Both sites answer through this machine's own Caddy (whatever DNS says), over HTTPS where they're https://.
# A first start may still be fetching its certificates, so this keeps trying for a while.
sites_answer() {
  local web api; web="$(env_get LAA_WEB_URL)"; api="$(env_get LAA_API_URL)"
  local deadline=$(($(date +%s) + WAIT_SECONDS))
  while :; do
    if curl -fsS -o /dev/null --max-time 10 --connect-to "::127.0.0.1:" "$api/health" 2>/dev/null \
      && curl -fsS -o /dev/null --max-time 10 --connect-to "::127.0.0.1:" "$web/" 2>/dev/null; then
      return 0
    fi
    [ "$(date +%s)" -lt "$deadline" ] || { printf '✗ %s or %s/health did not answer.\n' "$web" "$api" >&2; return 1; }
    sleep 3
  done
}

# Starts the version in LAA_TAG and checks it. `--wait` returns once every container with a health check is healthy,
# and fails if one turns unhealthy or stops.
start_and_check() {
  compose up -d --pull missing --remove-orphans --wait --wait-timeout "$WAIT_SECONDS" && sites_answer
}

roll_out() {
  local tag="$1" previous; previous="$(env_get LAA_TAG)"
  lock
  backup "$tag"
  env_set LAA_TAG "$tag"
  say "Starting ${tag}…"
  if start_and_check; then
    [ -z "$previous" ] || [ "$previous" = "$tag" ] || env_set LAA_PREVIOUS_TAG "$previous"
    forget_old_images "$tag" "$(env_get LAA_PREVIOUS_TAG)"
    say "$tag is live: $(env_get LAA_WEB_URL)"
    return 0
  fi

  printf '✗ %s failed. The last lines of its logs:\n' "$tag" >&2
  compose logs --no-color --tail 40 server web >&2 || true
  if [ -z "$previous" ] || [ "$previous" = "$tag" ]; then
    fail "There's no earlier version to go back to."
  fi
  say "Going back to ${previous}…"
  env_set LAA_TAG "$previous"
  if start_and_check; then
    fail "$tag didn't pass its checks, so $previous is live again. If $tag changed the database, the backup from just before it is in $BACKUPS."
  fi
  fail "Going back to $previous failed too. Look at \`docker compose logs\` in $HERE."
}

# Images take room on a small disk: of LAA's own images (LAA_IMAGES), keep only the live version and the one before
# it. awk rather than grep, because a grep that matches nothing fails, and that would stop the script here.
forget_old_images() {
  local images; images="$(env_get LAA_IMAGES)"
  docker image ls --format '{{.Repository}}:{{.Tag}}' \
    | awk -v prefix="${images:-ghcr.io/bernardodr}/laa-" -v keep1="$1" -v keep2="${2:-$1}" '
        index($0, prefix "server:") == 1 || index($0, prefix "web:") == 1 {
          n = split($0, part, ":"); tag = part[n]
          if (tag != keep1 && tag != keep2) print
        }' \
    | while read -r image; do docker image rm "$image" >/dev/null 2>&1 || true; done
}

# ---- Build both images here, from ./src (a clone of the repository) or from LAA_SOURCE, a checkout used as it is.
build() {
  local ref="${1:-main}" source
  if [ -n "${LAA_SOURCE:-}" ]; then
    # Someone else's checkout (the local trial uses the working copy): never move it to another commit.
    [ -z "${1:-}" ] || fail "With LAA_SOURCE set, the checkout is built as it is; leave out the ref."
    source="$LAA_SOURCE"
  else
    source="$HERE/src"
    [ -d "$source/.git" ] || git clone --quiet "$REPO" "$source"
    git -C "$source" fetch --quiet --tags origin
    git -C "$source" checkout --quiet --detach "origin/$ref" 2>/dev/null || git -C "$source" checkout --quiet --detach "$ref"
  fi
  # The tag is the commit, so `status` and the images say exactly what's live. Uncommitted changes get a marker.
  local tag; tag="$(git -C "$source" rev-parse --short=12 HEAD)"
  [ -z "$(git -C "$source" status --porcelain)" ] || tag="$tag-changed-$(date -u +%Y%m%d%H%M%S)"
  say "Building $tag from ${source}…"
  LAA_SOURCE="$source" LAA_TAG="$tag" \
    docker compose --project-directory "$HERE" -f "$HERE/compose.yml" -f "$HERE/compose.build.yml" build server web
  roll_out "$tag"
}

[ -f "$ENV_FILE" ] || fail "No .env in $HERE. Copy deploy/.env.example there, fill it in, and chmod 600 it."

case "${1:-}" in
  build) build "${2:-}" ;;
  rollback)
    previous="$(env_get LAA_PREVIOUS_TAG)"
    [ -n "$previous" ] || fail "There's no earlier version recorded."
    roll_out "$previous" ;;
  status)
    printf 'Live:   %s\nBefore: %s\n\n' "$(env_get LAA_TAG)" "$(env_get LAA_PREVIOUS_TAG)"
    compose ps ;;
  "" | -h | --help) usage ;;
  *)
    printf '%s' "$1" | grep -q -E '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$' || fail "\"$1\" isn't an image tag. Try --help."
    roll_out "$1" ;;
esac
