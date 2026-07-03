package com.vietlancer.admin;

import com.vietlancer.common.ApiException;
import com.vietlancer.dispute.Dispute;
import com.vietlancer.dispute.DisputeRepository;
import com.vietlancer.job.Job;
import com.vietlancer.job.JobRepository;
import com.vietlancer.notification.Notification;
import com.vietlancer.notification.NotificationService;
import com.vietlancer.subscription.SubscriptionRepository;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.user.UserRepository;
import com.vietlancer.wallet.WalletTransaction;
import com.vietlancer.wallet.WalletTransactionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bảng điều khiển quản trị — chỉ ADMIN (chặn ở SecurityConfig /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final DisputeRepository disputeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WalletTransactionRepository transactionRepository;
    private final NotificationService notificationService;

    public record Stats(
            long totalClients, long totalFreelancers,
            long jobsOpen, long jobsInProgress, long jobsCompleted, long jobsCancelled,
            long openDisputes, long activePremiumUsers, long pendingKyc,
            BigDecimal gmv, BigDecimal platformRevenue) {}

    @GetMapping("/stats")
    public Stats stats() {
        // GMV = tổng tiền gross đã trả cho freelancer; doanh thu = tổng phí nền tảng
        var gmv = transactionRepository.sumByType(WalletTransaction.Type.PAYOUT);
        var revenue = transactionRepository.sumByType(WalletTransaction.Type.PLATFORM_FEE).negate();
        return new Stats(
                userRepository.countByRole(Role.CLIENT),
                userRepository.countByRole(Role.FREELANCER),
                jobRepository.countByStatus(Job.Status.OPEN),
                jobRepository.countByStatus(Job.Status.IN_PROGRESS),
                jobRepository.countByStatus(Job.Status.COMPLETED),
                jobRepository.countByStatus(Job.Status.CANCELLED),
                disputeRepository.findByStatusOrderByCreatedAtAsc(Dispute.Status.OPEN).size(),
                subscriptionRepository.countActivePremiumUsers(Instant.now()),
                userRepository.findByKycStatusOrderByCreatedAtAsc(User.KycStatus.PENDING).size(),
                gmv,
                revenue);
    }

    // ==== KYC ====

    public record KycEntry(Long userId, String fullName, String email, String role,
            String idNumber, Instant createdAt) {}

    @GetMapping("/kyc")
    public List<KycEntry> pendingKyc() {
        return userRepository.findByKycStatusOrderByCreatedAtAsc(User.KycStatus.PENDING).stream()
                .map(u -> new KycEntry(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(),
                        u.getKycIdNumber(), u.getCreatedAt()))
                .toList();
    }

    public record KycDecisionBody(@Size(max = 500) String note) {}

    @PostMapping("/kyc/{userId}/approve")
    @Transactional
    public void approveKyc(@PathVariable Long userId, @Valid @RequestBody(required = false) KycDecisionBody body) {
        var user = requirePendingKyc(userId);
        user.setKycStatus(User.KycStatus.VERIFIED);
        user.setKycNote(body == null ? null : body.note());
        userRepository.save(user);
        notificationService.notify(user, Notification.Type.KYC_APPROVED,
                "✅ Hồ sơ xác minh của bạn đã được duyệt — huy hiệu \"Đã xác minh\" đã bật.",
                "/profile/" + user.getId());
    }

    @PostMapping("/kyc/{userId}/reject")
    @Transactional
    public void rejectKyc(@PathVariable Long userId, @Valid @RequestBody(required = false) KycDecisionBody body) {
        var user = requirePendingKyc(userId);
        user.setKycStatus(User.KycStatus.REJECTED);
        user.setKycNote(body == null ? null : body.note());
        userRepository.save(user);
        notificationService.notify(user, Notification.Type.KYC_REJECTED,
                "Hồ sơ xác minh bị từ chối%s. Bạn có thể nộp lại."
                        .formatted(body == null || body.note() == null ? "" : ": " + body.note()),
                "/settings");
    }

    private User requirePendingKyc(Long userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        if (user.getKycStatus() != User.KycStatus.PENDING) {
            throw ApiException.badRequest("Hồ sơ không ở trạng thái chờ duyệt");
        }
        return user;
    }

    // ==== Kiểm duyệt job ====

    public record RecentJob(Long id, String title, String status, String clientName, Instant createdAt) {}

    @GetMapping("/jobs")
    public List<RecentJob> recentJobs() {
        return jobRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(j -> new RecentJob(j.getId(), j.getTitle(), j.getStatus().name(),
                        j.getClient().getFullName(), j.getCreatedAt()))
                .toList();
    }

    public record TakedownBody(@Size(max = 500) String reason) {}

    /** Gỡ job vi phạm — chỉ job OPEN (job đang thực hiện phải qua luồng khiếu nại). */
    @PostMapping("/jobs/{id}/takedown")
    @Transactional
    public void takedown(
            @AuthenticationPrincipal User admin, @PathVariable Long id,
            @Valid @RequestBody(required = false) TakedownBody body) {
        var job = jobRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy job"));
        if (job.getStatus() != Job.Status.OPEN) {
            throw ApiException.badRequest(
                    "Chỉ gỡ được job đang mở. Job đang thực hiện phải xử lý qua trung tâm khiếu nại.");
        }
        job.setStatus(Job.Status.CANCELLED);
        jobRepository.save(job);
        notificationService.notify(job.getClient(), Notification.Type.JOB_REMOVED,
                "Job \"%s\" đã bị gỡ do vi phạm quy định%s."
                        .formatted(job.getTitle(),
                                body == null || body.reason() == null ? "" : " (" + body.reason() + ")"),
                null);
    }
}
