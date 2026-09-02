# Triển khai VietLancer lên production

Hướng dẫn đưa dự án lên một VPS (Ubuntu 22.04+) bằng Docker. Thời gian ước tính: **30–45 phút**.

Kiến trúc khi chạy thật:

```
Internet → nginx (80/443, SSL)
             ├── /            → frontend (Next.js :3000)
             └── /api,/ws,/files,/actuator/health → backend (Spring Boot :8080)
                                                      └── postgres (nội bộ, không mở cổng)
```

Backend và frontend **dùng chung một domain** → không cần cấu hình CORS, không cần subdomain riêng.

---

## 1. Chuẩn bị

| Thứ cần có | Ghi chú |
|---|---|
| VPS | Tối thiểu **2 vCPU / 4GB RAM / 40GB SSD**. Gợi ý: Vietnix, VNG Cloud, DigitalOcean, Hetzner |
| Domain | Trỏ bản ghi `A` của `tenmien.vn` và `www` về IP VPS |
| Docker | Cài theo hướng dẫn dưới |

```bash
# Trên VPS
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER && newgrp docker
docker --version && docker compose version
```

## 2. Lấy mã nguồn

```bash
sudo mkdir -p /srv && sudo chown $USER /srv && cd /srv
git clone <URL-repo-cua-ban> vietlancer
cd vietlancer
git checkout claude/vlancer-ai-topic-analysis-jjzr6u   # hoặc main sau khi merge
```

## 3. Tạo file cấu hình `.env`

```bash
cp .env.example .env
# Sinh 2 chuỗi bí mật:
echo "APP_JWT_SECRET=$(openssl rand -base64 48)"
echo "DB_PASSWORD=$(openssl rand -base64 24)"
nano .env    # dán vào, đặt SITE_URL và APP_ADMIN_PASSWORD
```

**Bắt buộc điền**: `SITE_URL`, `DB_PASSWORD`, `APP_JWT_SECRET`, `APP_ADMIN_PASSWORD`.
Ban đầu cứ để `SITE_URL=http://tenmien.vn` (chưa có SSL), bước 6 sẽ đổi sang `https://`.

## 4. Khởi chạy lần đầu (HTTP)

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps          # tất cả phải "healthy"/"running"
docker compose -f docker-compose.prod.yml logs -f backend
```

Kiểm tra:

```bash
curl http://localhost/actuator/health     # {"status":"UP"}
curl http://localhost/api/topics          # danh sách 18 lĩnh vực
```

Mở `http://tenmien.vn` trên trình duyệt — trang chủ phải hiện.

> Lần chạy đầu backend tự tạo bảng và seed 18 topic. Vì `APP_SEED_DEMO=false`,
> **không** có dữ liệu demo; chỉ tài khoản `admin@vietlancer.vn` được tạo với mật khẩu bạn đặt.

## 5. Cài SSL (Let's Encrypt)

```bash
# Xin chứng chỉ (đổi tenmien.vn và email)
docker run --rm \
  -v /srv/vietlancer/nginx/certs:/etc/letsencrypt \
  -v /srv/vietlancer/certbot-www:/var/www/certbot \
  -p 8081:80 certbot/certbot certonly --standalone \
  -d tenmien.vn -d www.tenmien.vn \
  --email ban@email.vn --agree-tos --no-eff-email
```

> Nếu cổng 80 đang bận, tạm dừng nginx: `docker compose -f docker-compose.prod.yml stop nginx`,
> chạy lệnh trên với `-p 80:80`, rồi bật lại.

Bật cấu hình HTTPS:

```bash
cp nginx/nginx-ssl.conf.example nginx/nginx.conf
sed -i 's/tenmien.vn/DOMAIN-THAT-CUA-BAN/g' nginx/nginx.conf
```

## 6. Đổi `SITE_URL` sang https và build lại frontend

Biến `NEXT_PUBLIC_*` được **nhúng vào bundle lúc build**, nên đổi domain phải build lại frontend:

```bash
sed -i 's|^SITE_URL=.*|SITE_URL=https://tenmien.vn|' .env
docker compose -f docker-compose.prod.yml up -d --build frontend
docker compose -f docker-compose.prod.yml restart nginx backend
```

Kiểm tra lại bằng `https://tenmien.vn` — nếu trang chạy nhưng gọi API lỗi, gần như chắc chắn
là frontend chưa build lại sau khi đổi `SITE_URL`.

**Gia hạn chứng chỉ tự động** (thêm vào crontab):

```
0 3 1 * * cd /srv/vietlancer && docker run --rm -v /srv/vietlancer/nginx/certs:/etc/letsencrypt -v /srv/vietlancer/certbot-www:/var/www/certbot certbot/certbot renew --webroot -w /var/www/certbot && docker compose -f docker-compose.prod.yml restart nginx
```

## 7. Sao lưu tự động

```bash
crontab -e
# Thêm dòng: backup 2h sáng mỗi ngày
0 2 * * * cd /srv/vietlancer && ./scripts/backup-db.sh >> /var/log/vietlancer-backup.log 2>&1
```

Khôi phục khi cần: `./scripts/restore-db.sh backups/db-YYYYMMDD-HHMMSS.sql.gz`

⚠️ **Backup nằm cùng máy không cứu được khi VPS hỏng** — hãy đồng bộ thư mục `backups/`
sang S3/Backblaze/máy khác (ví dụ bằng `rclone sync`).

## 8. Checklist BẮT BUỘC trước khi mở cho người dùng thật

- [ ] `.env` không bị commit (`git status` phải sạch)
- [ ] `APP_JWT_SECRET` là chuỗi ngẫu nhiên ≥32 ký tự, **không** dùng giá trị mặc định trong repo
- [ ] Đăng nhập `admin@vietlancer.vn` và **đổi mật khẩu**; cân nhắc đổi email admin trong `DataSeeder`
- [ ] `APP_SEED_DEMO=false` (không có job/tài khoản demo trên production)
- [ ] HTTPS hoạt động, HTTP tự chuyển sang HTTPS
- [ ] Backup đã chạy thành công ít nhất 1 lần và đã thử khôi phục
- [ ] Firewall: chỉ mở 22, 80, 443 (`ufw allow 22,80,443/tcp && ufw enable`)
- [ ] Uptime monitor trỏ vào `https://tenmien.vn/actuator/health` (UptimeRobot, BetterStack…)
- [ ] SMTP đã cấu hình và **thử thành công** luồng quên mật khẩu (email thật nhận được link)
- [ ] Bật `APP_REQUIRE_VERIFIED_EMAIL=true` sau khi SMTP chạy ổn
- [ ] **Pháp lý**: mô hình ví nội bộ + escrow có thể thuộc phạm vi điều chỉnh về trung gian
      thanh toán — tham vấn luật sư TRƯỚC khi nhận tiền thật
- [ ] Có Điều khoản sử dụng + Chính sách bảo mật (bắt buộc khi thu thập CCCD cho KYC)

## 9. Vận hành hằng ngày

```bash
# Xem log
docker compose -f docker-compose.prod.yml logs -f --tail=100 backend

# Cập nhật phiên bản mới
git pull && docker compose -f docker-compose.prod.yml up -d --build

# Khởi động lại một service
docker compose -f docker-compose.prod.yml restart backend

# Dọn image cũ khi đầy ổ
docker system prune -af --volumes=false
```

## 10. Bật Claude API (khi có kinh phí)

```bash
# Trong .env
APP_AI_CLASSIFIER=claude
ANTHROPIC_API_KEY=sk-ant-...
```

Rồi `docker compose -f docker-compose.prod.yml up -d backend`. Hệ thống tự fallback về engine
local nếu API lỗi/hết hạn mức, nên không có rủi ro gián đoạn.

## 11. Bật email thông báo

Điền `SMTP_HOST/PORT/USER/PASSWORD` trong `.env` rồi khởi động lại backend.
Gợi ý dịch vụ: Amazon SES, Resend, Mailgun (Gmail SMTP chỉ hợp cho thử nghiệm).

Chưa cấu hình SMTP thì hệ thống vẫn chạy: thông báo in-app hoạt động bình thường, còn link
**xác thực email / đặt lại mật khẩu** được ghi ra log server (`logs backend`, dòng `[DEV]`)
để bạn vẫn thử được luồng.

**Sau khi SMTP chạy ổn**, bật bắt buộc xác thực email để chặn tài khoản ảo:

```bash
# .env
APP_REQUIRE_VERIFIED_EMAIL=true
```

Khi bật, người dùng chưa xác thực vẫn đăng nhập/duyệt job được nhưng **không đăng job và
không chào giá được** cho tới khi bấm link trong email.

---

## Sự cố thường gặp

| Triệu chứng | Nguyên nhân & cách xử lý |
|---|---|
| Frontend chạy nhưng mọi API lỗi | Chưa build lại frontend sau khi đổi `SITE_URL` (xem bước 6) |
| `nginx` khởi động thất bại | Đang dùng `nginx-ssl.conf` nhưng chưa có chứng chỉ → quay lại conf HTTP |
| Backend `unhealthy` | Xem `logs backend`; hay gặp: thiếu biến bắt buộc trong `.env` |
| Chat không real-time | nginx thiếu block `/ws` với header `Upgrade` (đã có sẵn trong conf mẫu) |
| Rate limit chặn nhầm/không chặn | `APP_TRUST_FORWARDED_HEADER=true` chỉ đúng khi đứng sau nginx (compose đã set sẵn) |
| Upload ảnh lỗi 413 | Tăng `client_max_body_size` trong nginx **và** `spring.servlet.multipart` trong backend |
