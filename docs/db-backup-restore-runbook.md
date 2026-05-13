# Database Backup and Restore Runbook

This runbook covers the PostgreSQL backup, restore, upload disk monitoring, and board upload cleanup routine.

## Backup

Copy the scripts to the production host once, then run them from the deployment host.

```sh
sudo mkdir -p /opt/happyzion/scripts
sudo cp deploy/scripts/backup-db.sh /opt/happyzion/scripts/backup-db.sh
sudo cp deploy/scripts/check-disk.sh /opt/happyzion/scripts/check-disk.sh
sudo chmod 755 /opt/happyzion/scripts/*.sh
```

Use `deploy/scripts/backup-db.sh` from the API project directory during local validation, or `/opt/happyzion/scripts/backup-db.sh` on the production VM.

```sh
BACKUP_DIR=/opt/happyzion/backups \
PGHOST=db \
PGPORT=5432 \
PGDATABASE=happyzion \
PGUSER=postgres \
PGPASSWORD="$POSTGRES_PASSWORD" \
deploy/scripts/backup-db.sh
```

The script writes gzipped custom-format dumps named like `db-20260420-030000.dump.gz`. By default it runs `pg_dump --format=custom` through `docker compose -f /opt/happyzion/docker-compose.prod.yml exec -T db` and compresses the dump with `gzip`, which matches the production VM where Postgres runs in Docker Compose.

Set `BACKUP_DB_VIA_DOCKER=false` to use a host-installed `pg_dump` instead. In that mode the same `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD` environment variables are used.

## Production Deployment Checklist

Confirm these items during the final production deployment before enabling cron:

- Confirm `OCI_DEPLOY_PATH` matches the actual deployment directory, normally `/opt/happyzion`.
- Decide whether `deploy/scripts/*.sh` will be copied manually or uploaded by GitHub Actions.
- If using manual placement, copy scripts to `/opt/happyzion/scripts` and run `chmod 755 /opt/happyzion/scripts/*.sh`.
- If using GitHub Actions placement, add an upload step for `deploy/scripts/*.sh` to `${OCI_DEPLOY_PATH}/scripts/`.
- Confirm `/opt/happyzion/docker-compose.prod.yml` exists and the database service name is `db`.
- Confirm `/opt/happyzion/.env` has `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` values matching the running database.
- Run `/opt/happyzion/scripts/backup-db.sh` once manually and verify a `.dump.gz` file is created in `/opt/happyzion/backups`.
- Run `/opt/happyzion/scripts/check-disk.sh` once manually and verify it checks `/opt/happyzion/uploads`.
- Register cron only after the manual backup and disk check commands pass.

## Restore

Restore into an empty database or a database prepared for replacement.

```sh
gunzip -c /opt/happyzion/backups/db-20260420-030000.dump.gz | pg_restore --clean --if-exists --no-owner --no-acl --dbname "$PGDATABASE"
```

For Docker Compose, run the restore from a container or host environment that can reach the Postgres service and has the same `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD` values set.

## Cron

Run a daily backup during a low-traffic window.

```cron
15 3 * * * set -a && . /opt/happyzion/.env && set +a && BACKUP_DIR=/opt/happyzion/backups PGPASSWORD="$POSTGRES_PASSWORD" /opt/happyzion/scripts/backup-db.sh >> /var/log/happyzion-backup.log 2>&1
```

Run disk monitoring before or after backup jobs.

```cron
*/30 * * * * DISK_THRESHOLD=80 UPLOAD_DIR=/opt/happyzion/uploads /opt/happyzion/scripts/check-disk.sh >> /var/log/happyzion-disk.log 2>&1
```

## Disk Check

Use `deploy/scripts/check-disk.sh` to monitor the upload volume. By default it checks `/opt/happyzion/uploads` and exits with status `1` when disk usage is at or above `80%`.

```sh
DISK_THRESHOLD=85 UPLOAD_DIR=/opt/happyzion/uploads deploy/scripts/check-disk.sh
```

## Upload Cleanup Routine

The application runs a daily board upload cleanup routine. Expired upload tokens are removed by `UploadTokenCleanupService`, and stale temporary post assets are removed by `PostAssetCleanupService`.

The cleanup runs daily at `03:30 Asia/Seoul`.

Temporary `post_asset` rows where `post_id` is null and `detached_at` is older than 24 hours are treated as stale. Storage objects are deleted before the database rows are deleted.

Draft caveat: when a draft feature is introduced, draft-linked assets must be excluded from GC so unsaved draft content does not lose referenced uploads after the stale temporary asset window.

## Monthly Upload Total

Use this query to review monthly upload volume from `post_asset.byte_size`.

```sql
select
    date_trunc('month', created_at) as upload_month,
    sum(byte_size) as total_bytes
from post_asset
group by upload_month
order by upload_month desc;
```
