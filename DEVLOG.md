# DEVLOG — VietLancer

Nguồn sự thật về những gì đã làm. Đọc file này trước khi bắt đầu session mới.
Quy ước: mỗi feature group một mục, mới nhất ở trên cùng. Ghi cả quyết định + lý do, không chỉ kết quả.

---

## 2026-07-02 — Session 2: Hoàn thiện MVP (notifications, directory, AI suggestions, rate limit, tests)

### Đã implement
- **Notifications in-app** (`notification/`): entity + service + controller.
  - Hooks: NEW_BID (client), BID_ACCEPTED/BID_REJECTED (freelancer), JOB_COMPLETED, NEW_MESSAGE, NEW_REVIEW.
  - `NotificationService.notify` = non-critical: `REQUIRES_NEW` + try/catch (không bao giờ hỏng nghiệp vụ chính).
  - Chống spam: 1 thông báo CHƯA ĐỌC / (type + link) — tin nhắn dồn vào 1 notif/hội thoại.
  - API: `GET /api/notifications`, `/unread-count`, `POST /{id}/read`, `/read-all`.
- **Danh bạ freelancer**: `GET /api/users/freelancers?q=` (tìm theo tên/skills/bio, Premium xếp trước,
  kèm rating avg/count). Lưu ý: literal path `/freelancers` được Spring ưu tiên hơn `/{id}`.
- **AI gợi ý job cho freelancer**: `GET /api/jobs/suggested` — chạy classifier trên skills+bio
  → suy topic sở trường → job OPEN thuộc topic đó, loại job đã bid, top 6. (Tái dùng classifier, zero cost.)
- **Rate limiting** (`config/RateLimitFilter`, HIGHEST_PRECEDENCE): fixed window 1 phút theo IP.
  `/api/auth/**` 20 req/phút (chống brute-force), `/api/**` 300 req/phút. In-memory —
  khi scale ngang phải chuyển Redis (đã ghi PLAN.md).
- **Tests** (10, đều xanh): `LocalHybridClassifierTest` (multi-label, có dấu/không dấu, fallback other,
  title weight > description) + `MarketplaceFlowIntegrationTest` (@SpringBootTest, H2 mem:
  full flow đăng job→bid→chặn chat→accept escrow→chat→complete 90%→notification; thiếu tiền không accept được;
  không bid trùng; suggested jobs khớp skill).
- **Frontend**: `NotificationBell` (badge unread, poll 20s, dropdown, mark read), `/freelancers`,
  `/settings` (sửa hồ sơ — skills nuôi AI gợi ý), dashboard thêm section "🤖 Gợi ý cho bạn" + CTA thêm skills.

### Quyết định kiến trúc (user hỏi về microservice)
**Modular monolith có chủ đích, chưa tách microservice.** Lý do: escrow cần ACID một transaction;
package-per-domain đã là ranh giới tách sẵn; chi phí vận hành chưa được trả lại. Lộ trình tách
(ai-classifier → notification → chat → wallet) + tín hiệu kích hoạt ghi trong PLAN.md mục 5.

### Đã kiểm chứng (e2e script scratchpad)
Search 'react' ra freelancer; suggested trả đúng job web cho freelancer skills React/Spring;
bid → client unread=1 → mark read =0; spam login sai → 429 sau ~19 request.

### Checklist pre-production (cập nhật)
- [x] Unit/integration tests cơ bản
- [x] Rate limiting (in-memory; Redis khi scale ngang)
- [ ] Đổi `APP_JWT_SECRET` production; review CORS
- [ ] WebSocket chat, upload file, VNPay/MoMo, admin dashboard (Phase 2 — xem PLAN.md mục 6)

---

## 2026-07-02 — Session 1: Khởi tạo dự án, MVP hoàn chỉnh

**Commit**: `56a839d` trên branch `claude/vlancer-ai-topic-analysis-jjzr6u` (89 files). PR: chưa tạo.

### Quyết định kiến trúc (đã chốt với chủ dự án)
- Backend **Spring Boot 3.5.16 + Java 25 + Gradle 9** (user yêu cầu Java mới nhất, Gradle hơn Maven,
  BE Java). Chọn Boot 3.5 thay vì 4.x để hệ sinh thái ổn định; Java 25: virtual threads bật qua
  `spring.threads.virtual.enabled=true`, dùng records/sealed interfaces/pattern matching.
- Frontend **Next.js 15 + TypeScript + Tailwind 3** (user không chuyên FE, giao Claude chọn).
- AI classifier **miễn phí trước, Claude API sau** (yêu cầu user): interface `TopicClassifier` (sealed),
  mặc định `LocalHybridClassifier` (bỏ dấu tiếng Việt + từ điển keyword có trọng số, threshold 0.35,
  tối đa 4 topic, luôn ≥1). `ClaudeClassifier` sẵn sàng, bật bằng env, bọc `ResilientClassifier` fallback.
- DB: H2 file mặc định (zero-setup), profile `postgres` cho production (docker-compose có sẵn).

### Đã implement (backend)
- Auth JWT (jjwt 0.13), roles CLIENT/FREELANCER, BCrypt. Principal = entity `User`.
- Topics: 18 topic seed trong `DataSeeder` (slug khớp từ điển classifier — nếu thêm topic phải thêm keyword).
- Jobs: đăng (AI gán topic, chỉ CLIENT), search theo topic/keyword/paging, complete/cancel.
- Bids: 1 bid/job/freelancer (unique constraint), limit 15/tháng cho free tier, accept → escrow + reject bid khác + mở conversation.
- Wallet/escrow: deposit mô phỏng, hold/release (phí 10% — `app.platform.fee-percent`)/refund, transaction log đầy đủ.
- Chat: `canChat` = bid accepted HOẶC cả hai Premium; check lại tại thời điểm gửi tin.
- Reviews 2 chiều sau COMPLETED, unique (job, reviewer).
- Subscriptions: CLIENT_PREMIUM 99k / FREELANCER_PREMIUM 79k, 30 ngày, trừ ví, gia hạn nối tiếp.

### Đã implement (frontend)
12 trang: landing, login/register, jobs (filter sidebar + search), jobs/[id] (bid form, accept, complete,
review, chat start), post-job (nút "Phân tích" preview topic AI), dashboard, messages (polling 5s),
wallet, pricing, profile/[id]. API client `lib/api.ts` (JWT localStorage), auth context.

### Đã kiểm chứng
- E2E script (scratchpad, không commit): login → bid → chat bị chặn 403 → accept (ví 20tr→12tr,
  escrow 8tr) → chat OK → complete (freelancer +7.2tr sau phí 10%) → review 2 chiều → mua Premium. PASS.
- Classifier: "logo quán cà phê" → graphic-design 88% + digital-marketing 43%; job dịch thuật → translation 76%.
- `npm run build` sạch; UI đã screenshot xác nhận.

### Gotchas môi trường sandbox
- Proxy chặn tải Gradle distribution từ GitHub → wrapper 9.6.1 không tải được TRONG sandbox;
  dùng `gradle` hệ thống (8.14) chạy trên Java 21 + toolchain Java 25. Máy thật dùng `./gradlew` bình thường.
- Java 25 cài qua apt: `/usr/lib/jvm/java-25-openjdk-amd64` (khai báo trong `backend/gradle.properties`).
- OSIV (`open-in-view: true`) đang bật CÓ CHỦ ĐÍCH — controllers đọc lazy relations khi map DTO.
  Nếu tắt, phải chuyển toDto vào trong transaction boundary.

### Chưa làm / checklist pre-production
- [ ] Unit/integration tests (mới có E2E thủ công)
- [ ] Rate limiting (bắt buộc trước khi public)
- [ ] Đổi `APP_JWT_SECRET` ở production; review CORS origins
- [ ] WebSocket chat (hiện polling 5s), notification system
- [ ] Cổng thanh toán thật (VNPay/MoMo) thay deposit mô phỏng
- [ ] Upload avatar/portfolio, admin dashboard
- [ ] Chuyển classifier sang Claude khi có kinh phí (chỉ cần env, code sẵn sàng)

---

## 2026-07-02 — Session 1b: Thêm CLAUDE.md + DEVLOG.md

- Tạo `CLAUDE.md` (working style + backend engineering rules) và `DEVLOG.md` (file này).
- Điều chỉnh 2 quy tắc so với bản gốc của chủ dự án:
  1. **Tenant isolation (bỏ)**: VietLancer là single-tenant marketplace, không có `tenantId`.
     Thay bằng quy tắc tương đương: **ownership checks** trên mọi resource.
  2. **Mapper layer (điều chỉnh)**: codebase đang dùng convention DTO record + static `from(entity)`
     (gọn cho quy mô hiện tại). Giữ nguyên tắc cốt lõi "không trả entity thô ra controller";
     sẽ tách `mapper/` riêng khi mapping phức tạp lên. Nếu muốn strict mapper layer ngay → refactor ~8 DTO.
