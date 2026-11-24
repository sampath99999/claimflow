#!/usr/bin/env bash
# Runs mongod for the audit service on port 27340. MONGOD defaults to the binary that the
# audit-service tests already downloaded through flapdoodle (~/.embedmongo).
set -euo pipefail
cd "$(dirname "$0")/../.."

MONGOD=${MONGOD:-$(find "$HOME/.embedmongo" -name 'mongod*' -type f | head -n 1)}
if [ -z "$MONGOD" ]; then
  echo "No mongod found. Run ./mvnw -pl audit-service verify once, or set MONGOD." >&2
  exit 1
fi
mkdir -p .local/mongo
exec "$MONGOD" --dbpath .local/mongo --port 27340 --bind_ip 127.0.0.1
