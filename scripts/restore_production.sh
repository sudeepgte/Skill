#!/usr/bin/env bash
# ==============================================================================
# SkillHub Elite - Disaster Recovery and Restoration Script
# Restores MySQL database and uploads from compressed backup archives
# ==============================================================================

set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <path_to_db_backup.sql.gz> [path_to_uploads_backup.tar.gz]"
  exit 1
fi

DB_ARCHIVE="$1"
UPLOADS_ARCHIVE="${2:-}"

if [ ! -f "${DB_ARCHIVE}" ]; then
  echo "Error: Database archive file not found: ${DB_ARCHIVE}"
  exit 1
fi

echo "====================================================================="
echo "WARNING: Restoring will overwrite existing data in '${MYSQL_DATABASE:-skillhub}'!"
echo "Target Host: ${DB_HOST:-127.0.0.1}"
echo "Database: ${MYSQL_DATABASE:-skillhub}"
echo "DB Archive: ${DB_ARCHIVE}"
echo "====================================================================="

read -p "Type 'RESTORE-CONFIRM' to proceed: " CONFIRMATION
if [ "${CONFIRMATION}" != "RESTORE-CONFIRM" ]; then
  echo "Restoration aborted."
  exit 1
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting MySQL database restoration..."
gunzip -c "${DB_ARCHIVE}" | mysql \
  --host="${DB_HOST:-127.0.0.1}" \
  --port="${DB_PORT:-3306}" \
  --user="${DB_USERNAME:-skillhub_user}" \
  --password="${DB_PASSWORD}" \
  --default-character-set=utf8mb4 \
  "${MYSQL_DATABASE:-skillhub}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Database restored successfully."

if [ -n "${UPLOADS_ARCHIVE}" ] && [ -f "${UPLOADS_ARCHIVE}" ]; then
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] Restoring file uploads..."
  TARGET_DIR="$(dirname "${UPLOAD_DIR:-/var/skillhub/uploads}")"
  mkdir -p "${TARGET_DIR}"
  tar -xzf "${UPLOADS_ARCHIVE}" -C "${TARGET_DIR}"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] File uploads restored to: ${UPLOAD_DIR:-/var/skillhub/uploads}"
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Full restoration process complete."
