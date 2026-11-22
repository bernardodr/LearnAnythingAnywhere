#!/bin/bash
# Proves a backup can be restored: loads it into a scratch database on this Mac, counts what's inside, then removes
# the scratch database. Nothing on the server is touched.
#
#   ./scripts/restore-drill.sh [backup file]    (default: the newest in ~/LAA-backups)
#
# Uses the Postgres container from docker-compose.yml (the one ./dev.sh starts), which is the same major version as
# production. The scratch database is laa_restore_drill, made fresh each time.
set -euo pipefail
cd "$(dirname "$0")/.."

DIR="${LAA_BACKUP_DIR:-$HOME/LAA-backups}"
FILE="${1:-$(ls -1 "$DIR"/laa-*.dump 2>/dev/null | sort | tail -1 || true)}"
[ -n "$FILE" ] && [ -f "$FILE" ] || { echo "✗ No backup to restore. Fetch one with ./scripts/mac-backup.sh fetch." >&2; exit 1; }
DB=laa_restore_drill

pg() { docker compose exec -T postgres "$@"; }

echo "▸ Restoring $(basename "$FILE") into a scratch database ($DB)…"
docker compose up -d --wait postgres >/dev/null
pg psql -q -U laa -d laa -c "DROP DATABASE IF EXISTS $DB" -c "CREATE DATABASE $DB" >/dev/null
trap 'pg psql -q -U laa -d laa -c "DROP DATABASE IF EXISTS $DB" >/dev/null 2>&1 || true' EXIT
# --no-owner: the scratch database belongs to the local user, whoever owned the tables in production.
pg pg_restore -U laa -d "$DB" --no-owner --exit-on-error <"$FILE"

echo "▸ What's in it:"
pg psql -U laa -d "$DB" -P footer=off -c "
  SELECT 'accounts' AS what, count(*) FROM users
  UNION ALL SELECT 'textbooks', count(*) FROM textbooks
  UNION ALL SELECT 'lessons', count(*) FROM lessons
  UNION ALL SELECT 'AI calls logged', count(*) FROM model_runs
  UNION ALL SELECT 'newest migration', max(version::int) FROM flyway_schema_history"
echo "✓ The backup restores. (The scratch database is removed again.)"
