# DEVLOG — VietLancer

Nguồn sự thật về những gì đã làm. Đọc file này trước khi bắt đầu session mới.
Quy ước: mỗi feature group một mục, mới nhất ở trên cùng. Ghi cả quyết định + lý do, không chỉ kết quả.

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
