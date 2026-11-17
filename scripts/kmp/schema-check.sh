#!/usr/bin/env bash
# Verifies Room schemas 12/13/14 still match the pinned baseline (CR stripped before hashing).
# Looks in $SCHEMA_DIR if set, else app/schemas or shared/schemas. Exit 1 on missing or changed file.
# Deliberately no git diff / HEAD: catches staged and committed edits alike.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
BASELINE="$HERE/schema-baseline.sha256"
DB="com.example.data.db.AppDatabase"
if [ -n "${SCHEMA_DIR:-}" ]; then
  DIRS=("$SCHEMA_DIR")
else
  DIRS=("$ROOT/app/schemas/$DB" "$ROOT/shared/schemas/$DB" "$ROOT/app/schemas" "$ROOT/shared/schemas")
fi
rc=0
while read -r want name; do
  [ -z "$want" ] && continue
  f=""
  for d in "${DIRS[@]}"; do [ -f "$d/$name" ] && { f="$d/$name"; break; }; done
  if [ -z "$f" ]; then echo "SCHEMA MISSING: $name"; rc=1; continue; fi
  got="$(tr -d '\r' < "$f" | sha256sum | cut -d' ' -f1)"
  if [ "$got" != "$want" ]; then echo "SCHEMA CHANGED: $name ($f)"; rc=1; fi
done < "$BASELINE"
[ $rc -eq 0 ] && echo "schema-check OK"
exit $rc