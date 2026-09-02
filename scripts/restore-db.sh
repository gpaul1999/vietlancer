#!/usr/bin/env bash
# Khôi phục database từ file backup:  ./scripts/restore-db.sh backups/db-20260703-020000.sql.gz
# CẢNH BÁO: ghi đè dữ liệu hiện tại. Hãy backup trước khi chạy.
set -euo pipefail

cd "$(dirname "$0")/.."

FILE="${1:?Cách dùng: ./scripts/restore-db.sh <file .sql.gz>}"
[ -f "$FILE" ] || { echo "Không tìm thấy file: $FILE"; exit 1; }

COMPOSE="docker compose -f docker-compose.prod.yml"
DB_USER="$(grep -E '^DB_USER=' .env 2>/dev/null | cut -d= -f2- || true)"
DB_NAME="$(grep -E '^DB_NAME=' .env 2>/dev/null | cut -d= -f2- || true)"
DB_USER="${DB_USER:-vietlancer}"
DB_NAME="${DB_NAME:-vietlancer}"

read -rp "Ghi đè database '$DB_NAME' bằng $FILE? Gõ 'yes' để tiếp tục: " confirm
[ "$confirm" = "yes" ] || { echo "Đã hủy."; exit 1; }

echo "Dừng backend để tránh ghi đồng thời..."
$COMPOSE stop backend

gunzip -c "$FILE" | $COMPOSE exec -T postgres psql -U "$DB_USER" -d "$DB_NAME"

echo "Khởi động lại backend..."
$COMPOSE start backend
echo "✓ Khôi phục xong."
