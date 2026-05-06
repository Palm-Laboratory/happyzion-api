#!/usr/bin/env bash
set -Eeuo pipefail

BACKUP_DIR=${BACKUP_DIR:-/opt/happyzion/backups}
COMPOSE_FILE=${COMPOSE_FILE:-/opt/happyzion/docker-compose.prod.yml}
DB_SERVICE=${DB_SERVICE:-db}
BACKUP_DB_VIA_DOCKER=${BACKUP_DB_VIA_DOCKER:-true}
PGHOST=${PGHOST:-db}
PGPORT=${PGPORT:-5432}
PGDATABASE=${PGDATABASE:-happyzion}
PGUSER=${PGUSER:-postgres}
PGPASSWORD=${PGPASSWORD:-${POSTGRES_PASSWORD:-}}

export PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD

mkdir -p "$BACKUP_DIR"

backup_file="${BACKUP_DIR}/db-$(date +%Y%m%d-%H%M%S).dump.gz"

if [ "$BACKUP_DB_VIA_DOCKER" = "true" ]; then
    docker compose --file "$COMPOSE_FILE" exec -T \
        -e PGHOST="$PGHOST" \
        -e PGPORT="$PGPORT" \
        -e PGDATABASE="$PGDATABASE" \
        -e PGUSER="$PGUSER" \
        -e PGPASSWORD="$PGPASSWORD" \
        "$DB_SERVICE" \
        pg_dump --format=custom --no-owner --no-acl | gzip > "$backup_file"
else
    pg_dump --format=custom --no-owner --no-acl | gzip > "$backup_file"
fi
chmod 600 "$backup_file"

printf 'Created backup: %s\n' "$backup_file"
