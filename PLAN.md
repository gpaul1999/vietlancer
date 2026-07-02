# VietLancer — Kế hoạch phát triển

Nền tảng freelance marketplace (tương tự vLancer) với UI hiện đại và **AI tự động phân tích topic** cho job post: client chỉ cần nhập mô tả, hệ thống tự gán một hoặc nhiều topic liên quan.

## 1. Tech stack

| Thành phần | Công nghệ | Lý do |
|---|---|---|
| Backend | Java 25 + Spring Boot 3.5 (Gradle 9) | Java LTS mới nhất: virtual threads, records, sealed interfaces, pattern matching |
| Database | PostgreSQL (prod) / H2 (dev, zero-setup) | Chuẩn công nghiệp; H2 giúp chạy local không cần cài gì |
| Auth | JWT (stateless) + BCrypt | Đơn giản, scale tốt |
| Frontend | Next.js 15 + TypeScript + Tailwind CSS | UI hiện đại, cấu trúc rõ, phù hợp khi không chuyên FE |
| AI phân loại topic | Pluggable: `local` (miễn phí, mặc định) → `claude` (Claude API, bật khi có kinh phí) | Giai đoạn đầu 0 đồng, nâng cấp chỉ bằng đổi config |

## 2. Kiến trúc AI phân loại topic

```
Client nhập tiêu đề + mô tả job
        │
        ▼
TopicClassifier (interface)
        ├── LocalHybridClassifier  (mặc định, miễn phí)
        │     • Chuẩn hóa tiếng Việt (bỏ dấu, lowercase)
        │     • Từ điển keyword có trọng số cho từng topic (vi + en)
        │     • Chấm điểm multi-label, ngưỡng tin cậy, luôn có ≥1 topic
        └── ClaudeClassifier       (bật qua app.ai.classifier=claude)
              • Gọi Claude API, trả về danh sách topic + lý do
        ▼
Job được gán N topic (many-to-many) + độ tin cậy + giải thích
```

- Một post thuộc **nhiều topic** miễn là liên quan (multi-label).
- Client có thể xem topic AI đề xuất trước khi đăng (endpoint preview) nhưng **không tự chọn** topic.
- Khi chuyển sang Claude API: chỉ cần đặt `APP_AI_CLASSIFIER=claude` + `ANTHROPIC_API_KEY`.

## 3. Tính năng MVP

### Core marketplace
- Đăng ký / đăng nhập (role: CLIENT, FREELANCER), JWT.
- Client đăng job: nhập tiêu đề, mô tả, ngân sách, hạn chót → AI gán topic.
- Duyệt / tìm kiếm job theo topic, từ khóa, ngân sách.
- Freelancer chào giá (bid): số tiền, số ngày, thư chào.
- Client chọn bid → job chuyển IN_PROGRESS → hoàn thành → COMPLETED.

### Hồ sơ & đánh giá
- Profile freelancer: bio, kỹ năng, giá theo giờ, điểm trung bình.
- Review 2 chiều (client ↔ freelancer) sau khi job hoàn thành.

### Nhắn tin (có kiểm soát)
- **Luật chat**: client và freelancer chỉ được nhắn tin khi:
  1. Bid của freelancer được client **chấp nhận**, HOẶC
  2. **Cả hai** đang có gói trả phí (Premium).
- Chat theo hội thoại gắn với job.

### Gói trả phí (1 gói mỗi bên)
- **Client Premium**: chat sớm với freelancer Premium trước khi chọn bid, huy hiệu, job được ưu tiên hiển thị.
- **Freelancer Premium**: chat sớm với client Premium, huy hiệu, không giới hạn bid/tháng (free: 15 bid/tháng).
- Thanh toán bằng số dư ví, gia hạn theo tháng.

### Ví & Escrow
- Nạp tiền vào ví (MVP: mô phỏng nạp; phase 2: tích hợp VNPay/MoMo/Stripe).
- Khi client chấp nhận bid: tiền bị **giữ trong escrow**.
- Khi client xác nhận hoàn thành: giải ngân cho freelancer, trừ phí nền tảng (10%).
- Lịch sử giao dịch đầy đủ.

## 4. Mô hình dữ liệu chính

```
User(id, email, password, fullName, role, bio, skills, hourlyRate, avatarUrl)
Topic(id, slug, name, nameEn, icon)
Job(id, client, title, description, budgetMin/Max, deadline, status,
    topics[N-N], aiConfidence, aiExplanation)
Bid(id, job, freelancer, amount, deliveryDays, coverLetter, status)
Conversation(id, job, client, freelancer) / Message(id, conversation, sender, content)
Review(id, job, reviewer, reviewee, rating, comment)
Subscription(id, user, plan, startsAt, expiresAt)
Wallet(id, user, balance, escrowBalance) / WalletTransaction(id, wallet, type, amount, note)
```

## 5. Lộ trình

- **Phase 1 (MVP — đang làm)**: toàn bộ mục 3, thanh toán mô phỏng, classifier local.
- **Phase 2**: cổng thanh toán thật (VNPay/MoMo), WebSocket chat real-time, upload file/portfolio, thông báo đẩy, admin dashboard.
- **Phase 3**: chuyển classifier sang Claude API, gợi ý freelancer phù hợp cho job (matching), gợi ý job cho freelancer, phân tích giá thị trường.

## 6. Chạy dự án

```bash
# Backend (H2 in-memory, không cần cài DB)
cd backend && ./gradlew bootRun

# Frontend
cd frontend && npm install && npm run dev
# → http://localhost:3000 (API: http://localhost:8080)

# Prod với PostgreSQL
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```
