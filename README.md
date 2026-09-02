# VietLancer 🇻🇳

Nền tảng freelance marketplace với **AI tự động phân loại công việc**: client chỉ cần mô tả việc cần thuê bằng tiếng Việt tự nhiên — hệ thống phân tích và gán một hoặc **nhiều topic** liên quan, không cần chọn danh mục thủ công.

> 📋 Kế hoạch & chiến lược: [PLAN.md](./PLAN.md) · 🚀 Triển khai production: [DEPLOY.md](./DEPLOY.md) · 📓 Nhật ký phát triển: [DEVLOG.md](./DEVLOG.md)

## Kiến trúc

```
vietlancer/
├── backend/    Spring Boot 3.5 · Java 25 · Gradle 9 · JPA · JWT · H2/PostgreSQL
└── frontend/   Next.js 15 · TypeScript · Tailwind CSS
```

### Điểm nổi bật

- 🤖 **AI phân loại topic (multi-label)** — pluggable engine:
  - `local` (mặc định, **miễn phí**): chuẩn hóa tiếng Việt + từ điển keyword có trọng số.
  - `claude`: Claude API cho độ chính xác cao hơn — bật bằng `APP_AI_CLASSIFIER=claude` + `ANTHROPIC_API_KEY`, tự fallback về local khi lỗi.
- 💰 **Escrow**: tiền được giữ khi chọn freelancer, giải ngân khi hoàn thành (phí nền tảng 10%).
- 💬 **Chat có kiểm soát**: hai bên chỉ nhắn tin khi bid được chấp nhận, HOẶC cả hai đều có gói Premium.
- ⭐ **Premium** (một gói mỗi bên): client được ưu tiên hiển thị job + chat sớm; freelancer bid không giới hạn (free: 15/tháng) + chat sớm.
- 🚀 **Java 25**: virtual threads cho mọi request, records, sealed interfaces, pattern matching.

## Chạy dự án

### Backend (không cần cài DB — H2 tự khởi tạo)

```bash
cd backend
./gradlew bootRun          # http://localhost:8080
```

Tài khoản demo được seed sẵn:

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Client | `client@demo.vn` | `password123` |
| Freelancer | `freelancer@demo.vn` | `password123` |

### Frontend

```bash
cd frontend
npm install
npm run dev                # http://localhost:3000
```

### PostgreSQL (production)

```bash
docker compose up -d postgres
cd backend && SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```

### Deploy lên VPS (toàn stack + SSL)

```bash
cp .env.example .env      # điền SITE_URL, DB_PASSWORD, APP_JWT_SECRET, APP_ADMIN_PASSWORD
docker compose -f docker-compose.prod.yml up -d --build
```

Hướng dẫn đầy đủ 11 bước (SSL, backup tự động, checklist bảo mật): **[DEPLOY.md](./DEPLOY.md)**

## Cấu hình chính (biến môi trường)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `APP_AI_CLASSIFIER` | `local` | Engine phân loại: `local` (miễn phí) / `claude` |
| `ANTHROPIC_API_KEY` | — | API key khi dùng engine `claude` |
| `APP_JWT_SECRET` | dev key | Bắt buộc đổi ở production |
| `APP_CORS_ORIGINS` | `http://localhost:3000` | Origin của frontend |

## API chính

| Endpoint | Mô tả |
|---|---|
| `POST /api/auth/register` · `/login` | Đăng ký / đăng nhập (JWT) |
| `POST /api/auth/forgot-password` · `/reset-password` | Quên & đặt lại mật khẩu qua email (token dùng 1 lần, hạn 1h) |
| `POST /api/auth/verify-email` · `/resend-verification` | Xác thực địa chỉ email |
| `POST /api/ai/classify-preview` | Xem trước topic AI sẽ gán |
| `POST /api/jobs` · `GET /api/jobs?topic=&q=` | Đăng job (AI gán topic) / tìm kiếm |
| `POST /api/jobs/{id}/bids` · `POST /api/bids/{id}/accept` | Chào giá / chọn freelancer (escrow) |
| `POST /api/jobs/{id}/complete` | Hoàn thành & giải ngân |
| `POST /api/chats/start` · `/api/chats/{id}/messages` | Chat (theo luật gating) |
| `POST /api/reviews` | Đánh giá 2 chiều |
| `POST /api/subscriptions/subscribe` | Mua gói Premium |
| `POST /api/wallet/deposit` | Nạp ví (mô phỏng) |
