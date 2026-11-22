#!/bin/bash
# Keeps copies of the production database on this Mac: once a day it fetches the server's newest nightly backup
# into ~/LAA-backups and keeps the last 30.
#
#   ./scripts/mac-backup.sh setup    once: make a key for backups only, schedule the daily fetch (09:00, or when the
#                                    Mac next wakes), and print the one command to run on the server
#   ./scripts/mac-backup.sh fetch    fetch the newest backup now (the schedule runs this)
#   ./scripts/mac-backup.sh status   the copies here, the last runs, and the schedule
#
# The key (~/.ssh/laa_backup) has no passphrase, because nobody is there to type one at 09:00. That's safe because the
# server lets it do one thing only: fetch backup files as the laabackup user (deploy/backup.sh `serve`).
# The copies hold everything in LAA (accounts, books, chats), so the folder is private to you; keep FileVault on.
# Restore one with scripts/restore-drill.sh, and on the server as docs/DEPLOYING.md says.
#
# Written for the bash 3 that macOS ships. LAA_BACKUP_HOST, LAA_BACKUP_DIR and LAA_BACKUP_SSH (the ssh command) can
# be overridden; the local trial (e2e/production.sh) uses them to run this against a stand-in server.
set -euo pipefail

HOST="${LAA_BACKUP_HOST:-laabackup@2.28.71.82}"
DIR="${LAA_BACKUP_DIR:-$HOME/LAA-backups}"
KEY="$HOME/.ssh/laa_backup"
SSH="${LAA_BACKUP_SSH:-ssh}"
KEEP=30
LABEL="app.learnanythinganywhere.backup"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
INSTALLED="$HOME/Library/Application Support/LAA/mac-backup.sh"
NAME_PATTERN='^laa-[0-9]{8}T[0-9]{6}Z\.dump$'

log() { mkdir -p "$DIR"; printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$DIR/backup.log"; }
notify() { osascript -e "display notification \"$1\" with title \"LAA backup\"" >/dev/null 2>&1 || true; }
fail() { log "FAILED: $*"; notify "Failed: $*"; exit 1; }
# The copies' file names, oldest first. Only names that fit the pattern count, so nothing else in the folder is touched.
copies() { local f; for f in "$DIR"/laa-*.dump; do [ -f "$f" ] && printf '%s\n' "${f##*/}"; done | grep -E "$NAME_PATTERN" | sort || true; }
server() { "$SSH" -i "$KEY" -o BatchMode=yes -o ConnectTimeout=20 -o IdentitiesOnly=yes "$HOST" "$1"; }

fetch() {
  mkdir -p "$DIR" && chmod 700 "$DIR"
  local name; name="$(server latest)" || fail "couldn't reach the server, or it has no backups yet."
  printf '%s' "$name" | grep -q -E "$NAME_PATTERN" || fail "the server answered with something that isn't a backup's name."
  if [ -s "$DIR/$name" ]; then
    log "Already have $name"
    return 0
  fi
  server "get $name" >"$DIR/$name.part" || { rm -f "$DIR/$name.part"; fail "the download of $name broke off."; }
  # Postgres's own format starts with "PGDMP": anything else is an error message, not a backup.
  [ "$(head -c 5 "$DIR/$name.part")" = "PGDMP" ] || { rm -f "$DIR/$name.part"; fail "$name didn't look like a database backup."; }
  mv "$DIR/$name.part" "$DIR/$name"
  chmod 600 "$DIR/$name"
  copies | sort -r | tail -n +$((KEEP + 1)) | while read -r old; do rm -f "$DIR/$old"; done
  log "Fetched $name ($(wc -c <"$DIR/$name" | tr -d ' ') bytes)"
}

setup() {
  if [ ! -f "$KEY" ]; then
    ssh-keygen -q -t ed25519 -N "" -C "laa-backup@$(scutil --get LocalHostName 2>/dev/null || hostname -s)" -f "$KEY"
  fi
  # The schedule runs a copy kept outside the repository, so moving or switching branches doesn't break it.
  mkdir -p "$(dirname "$INSTALLED")" "$(dirname "$PLIST")" "$DIR"
  chmod 700 "$DIR"
  cp "$0" "$INSTALLED" && chmod 755 "$INSTALLED"
  cat >"$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array><string>/bin/bash</string><string>$INSTALLED</string><string>fetch</string></array>
  <!-- Every day at 09:00. A Mac that was asleep then runs it as soon as it wakes. -->
  <key>StartCalendarInterval</key><dict><key>Hour</key><integer>9</integer><key>Minute</key><integer>0</integer></dict>
  <key>StandardOutPath</key><string>$DIR/launchd.log</string>
  <key>StandardErrorPath</key><string>$DIR/launchd.log</string>
</dict>
</plist>
EOF
  launchctl bootout "gui/$(id -u)" "$PLIST" >/dev/null 2>&1 || true
  launchctl bootstrap "gui/$(id -u)" "$PLIST"
  echo "✓ This Mac will fetch the newest backup every day at 09:00 (or when it next wakes), into $DIR."
  echo
  echo "One step on the server, as root, lets this key fetch backups and nothing else:"
  echo
  echo "  /usr/local/lib/laa/backup.sh allow '$(cat "$KEY.pub")'"
  echo
  echo "Then try it: ./scripts/mac-backup.sh fetch"
}

status() {
  echo "Copies in $DIR:"
  copies | sort -r | head -5 | sed 's/^/  /'
  echo "  ($(copies | grep -c . || true) in all; the newest 5 shown)"
  echo
  echo "Last runs:"
  tail -n 5 "$DIR/backup.log" 2>/dev/null | sed 's/^/  /' || echo "  none yet"
  echo
  if launchctl print "gui/$(id -u)/$LABEL" >/dev/null 2>&1; then
    echo "Schedule: on (daily at 09:00)"
  else
    echo "Schedule: off (run ./scripts/mac-backup.sh setup)"
  fi
}

case "${1:-}" in
  setup) setup ;;
  fetch) fetch ;;
  status) status ;;
  *) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
