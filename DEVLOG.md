# DEVLOG — VietLancer

Nguồn sự thật về những gì đã làm. Đọc file này trước khi bắt đầu session mới.
Quy ước: mỗi feature group một mục, mới nhất ở trên cùng. Ghi cả quyết định + lý do, không chỉ kết quả.

---

## 2026-07-03 — Session 5: Code review toàn bộ + sửa 10 phát hiện

Review high-effort (8 góc + verify, 2 phát hiện xác nhận bằng thực nghiệm). 10 finding đã sửa HẾT:

1. **Lộ email** (security): `GET /api/users/{id}` public trả UserDto có email → tạo `PublicUserDto`
   (không email); UserDto đầy đủ chỉ còn ở `/auth/me`. FE: `User.email` thành optional.
2. **Race condition tiền** (correctness): thêm `@Version` (optimistic locking) vào `Job`, `Wallet`,
   `Milestone` → accept 2 bid đồng thời / double-click fund-release giờ ném
   OptimisticLockingFailureException → 409. LƯU Ý: schema đổi → dev phải xóa `backend/data/`.
3. **Sổ cái ví lệch** (correctness): releaseEscrow ghi PAYOUT=+gross rồi FEE=-fee
   (tổng = net = thay đổi balance). Test `soCaiViKhopVoiSoDu` chốt invariant này.
4. **Error handling**: GlobalExceptionHandler thêm IllegalArgumentException/TypeMismatch→400,
   OptimisticLocking→409, fallback Exception→500 (log, không lộ stack); permit `/error`
   (trước đó lỗi với user ẩn danh bị che thành 403); clamp page/size ở controller.
5. **Rate-limit bypass**: X-Forwarded-For chỉ được tin khi `app.rate-limit.trust-forwarded-header=true`
   (mặc định false; chỉ bật sau reverse proxy tin cậy).
6. **Premium ordering đúng cam kết**: đưa exists-subscription vào ORDER BY của query
   (JobRepository.search + UserRepository.searchByRole), bỏ sort in-memory sau phân trang.
   Query search đổi từ join distinct → exists (cần cho order by). Test chốt: job Premium cũ hơn vẫn đứng đầu.
7. **N+1**: batch queries — `BidRepository.countByJobIds`, `SubscriptionRepository.premiumUserIdsIn`
   (+ `SubscriptionService.premiumUserIds`), `ReviewRepository.ratingSummaries`,
   `MessageRepository.lastMessagesFor`; JobService.toDtos / BidController.toDtos /
   UserController.freelancers / ChatController.myConversations dùng lô. Trang 10 job: 21 query → 3.
8. **Thông báo ma**: NotificationService ghi thông báo trong afterCommit của tx ngoài
   (TransactionSynchronization + TransactionTemplate REQUIRES_NEW); ngoài tx thì ghi ngay.
9. **Claude retry**: postWithRetry 3 lần, backoff 500ms→1s trước khi fallback local.
10. **Admin password**: `app.admin.password` từ env (`APP_ADMIN_PASSWORD`); dev default password123,
    profile postgres KHÔNG default (trống → không tạo admin); demo data gate qua `app.seed-demo`
    (postgres mặc định false). Fix kèm: seedDemoData check theo email demo (trước check count>0
    nên admin tạo trước làm demo bị bỏ qua).

Kiểm chứng: 17 tests xanh (3 test mới trong ReviewFixesIntegrationTest); e2e live: email không còn
trong profile public, /api/jobs/abc → 400, page=-1 → 200 (clamp), admin login OK, seed đủ 3 job.

Chưa sửa (cleanup mức thấp, đã ghi nhận): gom helper ownership-check dùng chung, gom markup
avatar/pagination ở FE, cache kết quả classifier cho suggestedFor.

---

## 2026-07-02 — Session 4: WebSocket chat real-time, upload file, SEO

### WebSocket chat (STOMP)
- `spring-boot-starter-websocket`; endpoint `/ws` (handshake permitAll — auth thật ở STOMP CONNECT).
- `StompAuthInterceptor`: CONNECT bắt buộc JWT (native header Authorization) → gắn `StompPrincipal(email, userId)`;
  SUBSCRIBE `/topic/conversations/{id}` chỉ cho participant của hội thoại.
- Gửi tin vẫn qua REST `POST /api/chats/{id}/messages` (giữ nguyên gating + notification);
  controller broadcast DTO qua `SimpMessagingTemplate` tới `/topic/conversations/{id}`.
- Simple broker in-memory — khi scale nhiều instance: chuyển broker relay (RabbitMQ/Redis).
- FE: `@stomp/stompjs`, badge "Real-time" xanh khi kết nối, polling fallback giãn còn 20s.

### Upload file (`file/`)
- `FileStorageService`: lưu `./uploads` (config `app.storage.dir`), tên UUID, whitelist extension
  (ảnh/tài liệu/nén), max 5MB (multipart config). Khi scale → thay S3, giữ interface.
- `POST /api/files` (đăng nhập) → URL tuyệt đối `/files/{uuid.ext}` (public-read, URL không đoán được).
- FE: upload avatar ở /settings (preview + gỡ ảnh), đính kèm file trong chat (nút 📎,
  content = URL; render ảnh inline hoặc link 📎).

### SEO
- `app/jobs/[id]/page.tsx` → server component: `generateMetadata` (title/description/OG theo job)
  + JSON-LD **schema.org/JobPosting** (kèm baseSalary VND); UI cũ chuyển vào `components/JobDetailClient.tsx`.
- `app/sitemap.ts` (trang tĩnh + 50 job mới nhất, force-dynamic), `app/robots.ts`
  (chặn dashboard/settings/wallet/messages/admin). Cần đặt `NEXT_PUBLIC_SITE_URL` ở production.

### Kiểm chứng
- 14 tests xanh. E2E: WS nhận tin real-time <1s, JWT sai bị từ chối kết nối, upload png OK +
  đọc public OK, chặn `.sh` (400), chặn ẩn danh (403). Sitemap/robots/title động + JobPosting render đúng.

---

## 2026-07-02 — Session 3: Milestone payments + Dispute center (2 lợi thế cạnh tranh vs vLancer)

Chủ dự án giao Claude tự chọn hướng cạnh tranh tốt nhất → chọn 2 tính năng vLancer yếu
(theo phân tích PLAN.md mục 6) thay vì WebSocket chat (chỉ là tính năng ngang bằng).

### Milestone payments (`milestone/`)
- Accept bid có 2 chế độ (`POST /api/bids/{id}/accept` body `{useMilestones}`):
  - `false` (mặc định): giữ toàn bộ giá bid vào escrow như cũ — flow cũ KHÔNG đổi.
  - `true`: không giữ tiền ngay; `job.milestoneBased=true`, `escrowAmount=null`.
- Vòng đời mốc: PENDING → (client fund, hold escrow đúng số tiền mốc) FUNDED →
  (freelancer submit) SUBMITTED → (client release, trừ phí 10%) RELEASED. Hủy được khi PENDING.
- `JobService.complete` (milestone): chặn nếu còn mốc FUNDED/SUBMITTED; mốc PENDING tự hủy.
- `JobService.cancel` (milestone): hoàn escrow từng mốc đang giữ.
- API: `POST /api/jobs/{id}/milestones`, `GET .../milestones` (participants + admin),
  `POST /api/milestones/{id}/fund|submit|release|cancel`.

### Dispute center (`dispute/`)
- Role **ADMIN** mới: không thể tự đăng ký (guard trong AuthController), seed `admin@vietlancer.vn`
  / password123. `/api/admin/**` → `hasRole("ADMIN")` trong SecurityConfig.
- Client/freelancer của job IN_PROGRESS mở khiếu nại (1 OPEN/job). `heldAmount` chốt tại thời điểm mở
  (escrow toàn phần hoặc tổng mốc FUNDED+SUBMITTED).
- **`DisputeGuard.requireNoOpenDispute(jobId)`** — chốt chặn dùng chung, PHẢI gọi trong mọi mutation
  đụng escrow: hiện có complete, cancel, milestone fund/submit/release.
- Admin resolve: nhập `amountToFreelancer` (0..held) → release cho freelancer (trừ phí),
  hoàn phần còn lại cho client; job → COMPLETED (nếu >0) / CANCELLED (nếu =0); mốc đang giữ → CANCELLED.
- Người mở được withdraw → job hoạt động lại.
- API: `POST /api/jobs/{id}/disputes`, `GET .../disputes`, `GET /api/disputes/mine`,
  `POST /api/disputes/{id}/withdraw`; admin: `GET /api/admin/disputes?status=`, `POST .../{id}/resolve`.

### Frontend
- Job detail: chọn chế độ thanh toán khi accept (2 nút Escrow toàn bộ / Theo milestone),
  `MilestonePanel` (fund/submit/release/cancel + progress giải ngân), `DisputePanel`
  (banner đóng băng + form khiếu nại + kết quả phân xử). `/admin/disputes` cho ADMIN
  (link ⚖️ trên navbar). Notification types mới: MILESTONE_*, DISPUTE_*.

### Kiểm chứng
- 14 tests xanh (thêm `MilestoneAndDisputeIntegrationTest`: fund→submit→release đúng số dư,
  cancel hoàn mốc, dispute đóng băng + admin chia 4tr/6tr, withdraw mở khóa lại).
- E2E script: accept milestone không khóa vốn, complete/release trả 409 khi có khiếu nại,
  admin resolve đúng số dư 2 ví, client thường gọi API admin → 403.

### Lưu ý cho session sau
- `Role.ADMIN` làm switch role phải có nhánh ADMIN (JobService.mine, SubscriptionService).
- Đổi mật khẩu admin seed trước khi lên production.

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
