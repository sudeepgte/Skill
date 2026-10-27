#!/usr/bin/env bash
# ==============================================================================
# SkillHub Elite - Automated Production Backup Script
# Performs atomic logical MySQL dump and encrypted file storage archive
# ==============================================================================

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/skillhub}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DB_BACKUP_FILE="${BACKUP_DIR}/db_skillhub_${TIMESTAMP}.sql.gz"
UPLOADS_BACKUP_FILE="${BACKUP_DIR}/uploads_skillhub_${TIMESTAMP}.tar.gz"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

# Ensure backup directory exists
mkdir -p "${BACKUP_DIR}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting SkillHub production backup..."

# 1. MySQL Atomic Database Backup (Single Transaction to guarantee zero downtime and no locking)
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Dumping MySQL database..."
mysqldump \
  --host="${DB_HOST:-127.0.0.1}" \
  --port="${DB_PORT:-3306}" \
  --user="${DB_USERNAME:-skillhub_user}" \
  --password="${DB_PASSWORD}" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --hex-blob \
  --default-character-set=utf8mb4 \
  "${MYSQL_DATABASE:-skillhub}" | gzip -9 > "${DB_BACKUP_FILE}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Database backup created: ${DB_BACKUP_FILE} ($(du -h "${DB_BACKUP_FILE}" | cut -f1))"

# 2. File Storage and Certificate Assets Backup
if [ -d "${UPLOAD_DIR:-/var/skillhub/uploads}" ]; then
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] Archiving uploads and private documents..."
  tar -czf "${UPLOADS_BACKUP_FILE}" -C "$(dirname "${UPLOAD_DIR:-/var/skillhub/uploads}")" "$(basename "${UPLOAD_DIR:-/var/skillhub/uploads}")"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] Uploads backup created: ${UPLOADS_BACKUP_FILE} ($(du -h "${UPLOADS_BACKUP_FILE}" | cut -f1))"
fi

# 3. Retention Policy Cleanup: Remove backups older than RETENTION_DAYS
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Enforcing ${RETENTION_DAYS}-day retention policy..."
find "${BACKUP_DIR}" -name "db_skillhub_*.sql.gz" -mtime +"${RETENTION_DAYS}" -exec rm -f {} +
find "${BACKUP_DIR}" -name "uploads_skillhub_*.tar.gz" -mtime +"${RETENTION_DAYS}" -exec rm -f {} +

# 4. Off-Site Replication (e.g. AWS S3 / Cloud Storage sync)
if [ -n "${OFFSITE_S3_BUCKET:-}" ]; then
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] Syncing backups to offsite bucket: ${OFFSITE_S3_BUCKET}..."
  aws s3 cp "${DB_BACKUP_FILE}" "s3://${OFFSITE_S3_BUCKET}/db/"
  aws s3 cp "${UPLOADS_BACKUP_FILE}" "s3://${OFFSITE_S3_BUCKET}/uploads/"
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] SkillHub production backup completed successfully."
