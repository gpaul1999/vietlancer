# CLAUDE.md — VietLancer

Nền tảng freelance marketplace với AI tự phân loại topic cho job post (multi-label).
Monorepo: `backend/` (Spring Boot 3.5 · Java 25 · Gradle 9) + `frontend/` (Next.js 15 · TypeScript · Tailwind).

## Lệnh thường dùng

```bash
# Backend (H2 tự tạo, không cần DB) — http://localhost:8080
cd backend && ./gradlew bootRun

# Backend: compile / test
cd backend && ./gradlew compileJava && ./gradlew test

# Frontend — http://localhost:3000
cd frontend && npm run dev        # dev
cd frontend && npm run build      # kiểm tra build trước khi commit

# Demo accounts (seed sẵn): client@demo.vn / freelancer@demo.vn — password123
```

Lưu ý môi trường sandbox: nếu `./gradlew` không tải được distribution (proxy chặn GitHub),
dùng `gradle` hệ thống (8.14, chạy trên Java 21, compile bằng toolchain Java 25 tại
`/usr/lib/jvm/java-25-openjdk-amd64` — đã khai báo trong `gradle.properties`).

## Kiến trúc backend

- Package theo domain: `user`, `topic`, `job`, `bid`, `chat`, `review`, `subscription`, `wallet`, `ai`, `notification`, `config`, `common`.
- **Kiến trúc: modular monolith có chủ đích** — escrow cần ACID một transaction; ranh giới tách
  microservice = ranh giới package (lộ trình tách + tín hiệu kích hoạt: PLAN.md mục 5).
- AI classifier là **pluggable** (`ai/TopicClassifier` — sealed interface): `local-hybrid` mặc định (miễn phí),
  `claude` bật qua `APP_AI_CLASSIFIER=claude` + `ANTHROPIC_API_KEY`, luôn bọc trong `ResilientClassifier` để fallback về local.
- Luật nghiệp vụ cốt lõi (không được phá vỡ khi sửa code):
  1. Client KHÔNG tự chọn topic — chỉ AI gán (multi-label, 1–4 topic).
  2. Chat chỉ mở khi bid được chấp nhận HOẶC cả hai bên có Premium còn hạn (`ChatService.canChat`).
  3. Tiền đi qua escrow: accept bid → hold; complete → release trừ phí nền tảng; cancel → refund.
  4. Freelancer free tier: giới hạn bid/tháng (`app.platform.free-bids-per-month`); Premium không giới hạn.
  5. Thông báo là non-critical: mọi hook notify phải đi qua `NotificationService.notify`
     (REQUIRES_NEW + try/catch + chống spam theo type+link) — không được ném lỗi vào nghiệp vụ chính.
  6. Rate limit: `RateLimitFilter` (auth 20 req/phút/IP, api 300 req/phút/IP) — in-memory,
     chuyển Redis khi chạy nhiều instance.

---

## Working style rules

- **Đọc DEVLOG.md trước** khi bắt đầu — đó là nguồn sự thật duy nhất về những gì đã làm.
  Không tốn thời gian re-examine files để suy luận lại lịch sử.
- **Không recap những gì đã biết** — bắt đầu làm ngay, không hỏi lại những gì đã được ghi.
- **Sau mỗi feature group**: tự cập nhật DEVLOG.md + CLAUDE.md mà không cần nhắc.

## Backend Engineering Rules

These rules apply to ALL backend code written in this project. Follow them automatically without being asked.

### 1. Correctness first
- **Đúng trước, nhanh sau** — no premature optimization. Prove correctness first.
- **ACID**: wrap multi-step mutations in `@Transactional`. Never leave partial state on failure.
- **Idempotency**: creating the same resource twice must not corrupt state. Use unique constraints
  + graceful duplicate handling (như `bids(job_id, freelancer_id)`, `reviews(job_id, reviewer_id)`).
- **Validate at boundaries**: validate every external input (HTTP body, path params, query params)
  via Bean Validation (`@Valid`, `@NotNull`, `@Min`, …). Never trust data from outside.

### 2. Reliability
- **Fail fast**: throw immediately on invalid state (dùng `ApiException.badRequest/forbidden/...`).
  Never swallow exceptions silently (`catch (Exception e) {}`).
- **Circuit breaker**: external calls (Claude API, email SMTP, storage) must be wrapped so a failure
  there cannot crash the main request path. Pattern chuẩn trong dự án: `ai/ResilientClassifier`
  (try primary → log → fallback). Mọi external call mới phải theo pattern này.
- **Retry with backoff**: transient failures on external calls should be retried with exponential
  backoff, not immediately.
- **Graceful degradation**: notifications and email alerts are non-critical — always `try/catch`
  and continue if they fail. (Chưa có notification system; áp dụng khi xây ở Phase 2.)

### 3. Scalability
- **Stateless service**: never store request-scoped state in service fields. Request context lấy qua
  `@AuthenticationPrincipal` / SecurityContext — không tự tạo ThreadLocal mới nếu chưa cần.
- **Async for non-blocking work**: notifications should be fire-and-forget. Use `@Async` or move to
  a queue when load increases. (Server đã bật virtual threads — I/O blocking trong request là chấp nhận được.)
- **Cache deliberately**: avoid caching unless profiling shows a hotspot. Cache invalidation is a
  common source of bugs.

### 4. Maintainability
- **Single responsibility**: each service class handles one domain entity. Cross-entity orchestration
  belongs in a dedicated service (vd: `BidService.accept` điều phối wallet + job + chat).
- **Dependency injection**: always constructor-inject (`@RequiredArgsConstructor`). No field injection.
  No `new` inside services (ngoại lệ: builders/DTOs/value objects).
- **Centralized error handling**: all exceptions surface through `GlobalExceptionHandler`.
  Never return raw HTTP 500 with stack trace.
- **Readable over clever**: method names describe intent. No magic numbers — dùng named constants
  hoặc config trong `application.yml` (`app.platform.*`).
- **DTO convention**: DTO là Java `record`, đặt cạnh domain, convert qua static factory
  `Dto.from(entity)`. Controller KHÔNG BAO GIỜ trả entity thô ra ngoài — luôn qua DTO.
  (Khi codebase lớn lên hoặc cần mapping phức tạp, tách sang lớp `mapper/` riêng.)

### 5. Security
- **Least privilege — default deny**: mọi endpoint mặc định yêu cầu đăng nhập (`anyRequest().authenticated()`
  trong `SecurityConfig`); chỉ mở public một cách tường minh. Role/quyền check ở service layer
  (`role != CLIENT → forbidden`); khi thêm endpoint nhạy cảm mới, cân nhắc bật `@EnableMethodSecurity`
  + `@PreAuthorize`.
- **Ownership checks**: mọi thao tác lên resource phải xác minh quyền sở hữu/tham gia
  (vd: `requireOwner(client, job)`, `requireParticipant(user, conversation)`).
  Không bao giờ tin `id` từ client mà thao tác thẳng.
- **Never trust input**: use Bean Validation on all request bodies. SQL injection is prevented by JPA,
  but still use parameterized queries if ever writing native SQL.
- **No hardcoded secrets**: all credentials via environment variables / config property placeholders
  (`${APP_JWT_SECRET:...}`). Never commit actual values.
- **Rate limiting**: bắt buộc thêm rate limiting trước khi expose bất kỳ endpoint nào ra production
  (chưa có — nằm trong checklist pre-production, xem DEVLOG).
