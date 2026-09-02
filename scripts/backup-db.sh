#!/usr/bin/env bash
# Sao lưu database + thư mục uploads. Chạy hằng ngày qua cron:
#   0 2 * * * cd /srv/vietlancer && ./scripts/backup-db.sh >> /var/log/vietlancer-backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")/.."

BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
COMPOSE="docker compose -f docker-compose.prod.yml"

mkdir -p "$BACKUP_DIR"

# Đọc DB_USER/DB_NAME từ .env nếu có
DB_USER="$(grep -E '^DB_USER=' .env 2>/dev/null | cut -d= -f2- || true)"
DB_NAME="$(grep -E '^DB_NAME=' .env 2>/dev/null | cut -d= -f2- || true)"
DB_USER="${DB_USER:-vietlancer}"
DB_NAME="${DB_NAME:-vietlancer}"

echo "[$(date -Is)] Bắt đầu backup..."

# 1. Database
$COMPOSE exec -T postgres pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_DIR/db-$STAMP.sql.gz"
echo "  ✓ DB → $BACKUP_DIR/db-$STAMP.sql.gz"

# 2. File người dùng upload (volume uploads)
$COMPOSE run --rm --no-deps -v "$(pwd)/$BACKUP_DIR:/backup" -T backend \
    tar czf "/backup/uploads-$STAMP.tar.gz" -C /app uploads
echo "  ✓ Uploads → $BACKUP_DIR/uploads-$STAMP.tar.gz"

# 3. Dọn bản cũ
find "$BACKUP_DIR" -name '*.gz' -mtime +"$KEEP_DAYS" -delete
echo "[$(date -Is)] Xong. Giữ lại $KEEP_DAYS ngày gần nhất."

# GỢI Ý: đồng bộ $BACKUP_DIR sang nơi khác (S3/Backblaze/máy khác) —
# backup nằm cùng máy với server KHÔNG cứu được khi máy hỏng.
