#!/usr/bin/env bash
set -Eeuo pipefail

THRESHOLD=${DISK_THRESHOLD:-80}
UPLOAD_DIR=${UPLOAD_DIR:-/opt/happyzion/uploads}

usage="$(df -P "$UPLOAD_DIR" | awk 'NR == 2 { gsub("%", "", $5); print $5 }')"

if [ "$usage" -ge "$THRESHOLD" ]; then
    printf 'Disk usage for %s is %s%%, threshold is %s%%\n' "$UPLOAD_DIR" "$usage" "$THRESHOLD" >&2
    exit 1
fi

printf 'Disk usage for %s is %s%%, threshold is %s%%\n' "$UPLOAD_DIR" "$usage" "$THRESHOLD"
