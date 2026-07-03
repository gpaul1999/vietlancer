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

## 5. Quyết định kiến trúc: Modular Monolith → tách service theo nhu cầu

**Hiện tại: modular monolith có chủ đích** (không phải "monolith vì lười"):

- Escrow cần **ACID trong một transaction** (trừ ví + giữ escrow + đổi trạng thái job + reject bid khác).
  Tách service ở giai đoạn này = distributed transaction/saga → phức tạp và dễ sai hơn rất nhiều.
- Codebase đã chia **package theo domain** (user/job/bid/chat/wallet/ai/notification…), giao tiếp qua
  service interface — đây chính là "microservice-ready": tách ra là cắt theo đường có sẵn.
- Chi phí vận hành microservice (deploy, observability, versioning API nội bộ) chưa được trả lại
  bằng lợi ích gì khi chưa có traffic.

**Lộ trình tách khi có tín hiệu** (theo thứ tự ưu tiên):

| Service tách ra | Vì sao tách trước | Tín hiệu kích hoạt |
|---|---|---|
| `ai-classifier` | Stateless, không cần DB chung, scale độc lập, có thể viết Python nếu cần model riêng | Phân loại chậm ảnh hưởng đăng job, hoặc muốn GPU/model riêng |
| `notification` | Fire-and-forget, hợp queue (Kafka/RabbitMQ), thêm email/push không đụng core | Gửi email/push volume lớn |
| `chat` | WebSocket connection-heavy, scale theo connection chứ không theo CPU | >10k concurrent connections |
| `wallet/payment` | Yêu cầu audit/compliance riêng khi tích hợp cổng thanh toán thật | Tích hợp VNPay/MoMo + khối lượng giao dịch lớn |

Core marketplace (user/job/bid/review) giữ chung một service lâu nhất — chúng chia sẻ transaction.

## 6. Lộ trình tính năng — cạnh tranh trực tiếp với vLancer

### ✅ Phase 1 — MVP (XONG)
Toàn bộ mục 3 + thông báo in-app, danh bạ freelancer, **AI gợi ý job theo kỹ năng**,
rate limiting, tests (unit + integration).

### Phase 2 — Trải nghiệm & tăng trưởng (ưu tiên kế tiếp)
| Tính năng | Ghi chú | vLancer có? |
|---|---|---|
| WebSocket chat real-time + typing indicator | Thay polling 5s hiện tại | Có (cơ bản) |
| Upload file: avatar, portfolio, đính kèm job/chat | S3-compatible storage | Có |
| Cổng thanh toán VNPay/MoMo/ZaloPay | Thay deposit mô phỏng | Có |
| ✅ **Thanh toán theo milestone** (XONG) | Chia job thành mốc, nạp/giải ngân escrow từng mốc — giảm rủi ro 2 bên | Không rõ → **lợi thế** |
| ✅ **Trung tâm giải quyết tranh chấp** (XONG) | Khiếu nại đóng băng job, admin chia escrow linh hoạt | Yếu → **lợi thế** |
| Email notification + job alert theo topic đã lưu | Digest hằng ngày | Có |
| Lưu job yêu thích, lịch sử xem | | Có |
| Admin dashboard: duyệt user, gỡ job xấu, thống kê | | — |
| SEO: SSR trang public, sitemap, schema.org JobPosting | Kéo organic traffic — kênh lớn nhất của vLancer | Có |
| Xác thực: OTP điện thoại, KYC freelancer (huy hiệu "Đã xác minh") | | Có (KYC yếu) |

### Phase 3 — AI làm khác biệt hóa (moat thật sự so với vLancer)
| Tính năng | Mô tả |
|---|---|
| Claude classifier | Bật engine `claude` (code sẵn) khi có doanh thu |
| **AI matching 2 chiều** | Xếp hạng freelancer phù hợp nhất cho từng job (skill × topic × rating × lịch sử) và ngược lại |
| **AI gợi ý giá** | Phân tích bid lịch sử theo topic → gợi ý ngân sách cho client, giá chào cho freelancer |
| **AI hỗ trợ viết mô tả job** | Client nhập 2-3 dòng → AI sinh mô tả đầy đủ, rõ phạm vi (giảm tranh chấp) |
| AI chấm chất lượng bid | Cảnh báo bid spam/copy-paste cho client |
| AI phát hiện gian lận | Pattern giao dịch bất thường, tài khoản ảo |

### Phase 4 — Scale
Tách service theo mục 5, Redis (cache + rate limit phân tán), Elasticsearch cho search,
mobile app (React Native — tái dùng API), i18n tiếng Anh mở rộng thị trường.

## 7. Chạy dự án

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
