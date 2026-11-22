#!/usr/bin/env bash
# The nightly database backup on the server, and the narrow door the owner's Mac fetches copies through.
#
#   backup.sh nightly         dump the database into the nightly folder, check the dump reads back, keep the newest 7.
#                             The laa-backup timer runs it at 03:15 UTC (deploy/laa-backup.timer).
#   backup.sh serve           only as the forced command of the Mac's backup key (see `allow`). It answers two
#                             requests, taken from SSH_ORIGINAL_COMMAND, and refuses everything else:
#                               latest       the newest nightly backup's file name
#                               get <name>   that file's bytes
#   backup.sh allow '<key>'   (root) let this public key fetch backups, and nothing else, as the laabackup user
#
# bootstrap.sh installs it as /usr/local/lib/laa/backup.sh (root's, not writable by anyone else, because the backup
# key's forced command runs it), with the nightly folder /var/backups/laa (root:laabackup, 750) and the timer.
# The fetching side is scripts/mac-backup.sh. Restoring a backup: docs/DEPLOYING.md.
#
# The dumps hold everything (accounts, password hashes, books, chats), so the folder is readable only by root and the
# laabackup user, and the key that reads it can't do anything else.
set -euo pipefail

LAA_HOME="${LAA_HOME:-/opt/laa}"
NIGHTLY="${LAA_NIGHTLY_DIR:-/var/backups/laa}"
KEEP=7
NAME_PATTERN='^laa-[0-9]{8}T[0-9]{6}Z\.dump$'
FORCED_COMMAND="/usr/local/lib/laa/backup.sh serve"
KEYS_FILE="${LAA_BACKUP_KEYS:-/var/lib/laabackup/.ssh/authorized_keys}"

fail() { printf '✗ %s\n' "$*" >&2; exit 1; }
compose() { docker compose --project-directory "$LAA_HOME" -f "$LAA_HOME/compose.yml" "$@"; }
# The backups' file names, oldest first. Only names that fit the pattern count, so nothing else in the folder is touched.
backups() { local f; for f in "$NIGHTLY"/laa-*.dump; do [ -f "$f" ] && printf '%s\n' "${f##*/}"; done | grep -E "$NAME_PATTERN" | sort || true; }
newest() { backups | tail -1; }

nightly() {
  [ -n "$(compose ps --status running -q postgres)" ] || fail "The database isn't running, so there's nothing to back up."
  mkdir -p "$NIGHTLY"
  local file; file="$NIGHTLY/laa-$(date -u +%Y%m%dT%H%M%SZ).dump"
  # Written under a temporary name, so a half-written file is never taken for a backup (or fetched by the Mac).
  compose exec -T postgres pg_dump -U laa -d laa -Fc >"$file.part" || { rm -f "$file.part"; fail "pg_dump failed."; }
  # A dump that Postgres can't list, or that has no accounts table, isn't a backup.
  compose exec -T postgres pg_restore --list <"$file.part" | grep -q 'TABLE DATA public users' \
    || { rm -f "$file.part"; fail "The dump didn't read back, so it wasn't kept."; }
  chmod 640 "$file.part"
  # The laabackup user (the Mac's key) may read the dumps; on a laptop trial there's no such group.
  getent group laabackup >/dev/null 2>&1 && chgrp laabackup "$file.part"
  mv "$file.part" "$file"
  backups | sort -r | tail -n +$((KEEP + 1)) | while read -r old; do rm -f "$NIGHTLY/$old"; done
  printf 'Backed up to %s (%s bytes)\n' "$file" "$(wc -c <"$file" | tr -d ' ')"
}

# The forced command: whatever the key's owner types, sshd runs this instead, with their words in
# SSH_ORIGINAL_COMMAND. Only the two exact requests are answered, and a name must be a backup's name, so there's no
# way to reach any other file (no slashes, no "..").
serve() {
  local request="${SSH_ORIGINAL_COMMAND:-}"
  case "$request" in
    latest)
      local name; name="$(newest)"
      [ -n "$name" ] || fail "There are no backups yet."
      echo "$name" ;;
    "get "*)
      local name="${request#get }"
      printf '%s' "$name" | grep -q -E "$NAME_PATTERN" || fail "That isn't a backup's name."
      [ -f "$NIGHTLY/$name" ] || fail "There's no backup called $name."
      cat "$NIGHTLY/$name" ;;
    *) fail "This key can only fetch backups: 'latest' or 'get <name>'." ;;
  esac
}

allow() {
  # Root only on the server; the local trial points LAA_BACKUP_KEYS at a scratch file instead.
  [ "$(id -u)" = 0 ] || [ -n "${LAA_BACKUP_KEYS:-}" ] || fail "Run this as root."
  local key="$1"
  # One line only: grep checks line by line, so a second line could otherwise slip in a key without the restriction.
  case "$key" in *$'\n'* | *$'\r'*) fail "A key is one line." ;; esac
  printf '%s' "$key" | grep -q -E '^ssh-ed25519 [A-Za-z0-9+/=]+( [^ ]+)?$' || fail "That doesn't look like an ed25519 public key (one line, starting ssh-ed25519)."
  local tmp; tmp="$(mktemp)"
  # One line per key: replace this key's line if it's already there.
  { grep -v -F "$(printf '%s' "$key" | awk '{print $2}')" "$KEYS_FILE" 2>/dev/null || true
    printf 'restrict,command="%s" %s\n' "$FORCED_COMMAND" "$key"; } >"$tmp"
  cat "$tmp" >"$KEYS_FILE"
  rm -f "$tmp"
  echo "That key can now fetch backups (and do nothing else)."
}

case "${1:-}" in
  nightly) nightly ;;
  serve) serve ;;
  allow) [ -n "${2:-}" ] || fail "Usage: backup.sh allow 'ssh-ed25519 AAAA… name'"; allow "$2" ;;
  *) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
