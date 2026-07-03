package com.vietlancer.milestone;

import com.vietlancer.common.ApiException;
import com.vietlancer.dispute.DisputeGuard;
import com.vietlancer.job.Job;
import com.vietlancer.notification.Notification;
import com.vietlancer.notification.NotificationService;
import com.vietlancer.user.User;
import com.vietlancer.wallet.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final DisputeGuard disputeGuard;

    @Value("${app.platform.fee-percent}")
    private int feePercent;

    public record CreateMilestoneRequest(String title, BigDecimal amount, LocalDate dueDate) {}

    /** Client tạo mốc cho job milestone-based đang thực hiện. */
    @Transactional
    public Milestone create(User client, Job job, CreateMilestoneRequest request) {
        requireClientOwner(client, job);
        requireMilestoneJobInProgress(job);
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw ApiException.badRequest("Số tiền milestone phải lớn hơn 0");
        }
        return milestoneRepository.save(Milestone.builder()
                .job(job)
                .title(request.title())
                .amount(request.amount())
                .dueDate(request.dueDate())
                .build());
    }

    /** Client nạp escrow cho mốc → freelancer yên tâm bắt đầu làm mốc này. */
    @Transactional
    public Milestone fund(User client, Long milestoneId) {
        var milestone = find(milestoneId);
        var job = milestone.getJob();
        requireClientOwner(client, job);
        requireMilestoneJobInProgress(job);
        disputeGuard.requireNoOpenDispute(job.getId());
        requireStatus(milestone, Milestone.Status.PENDING, "Chỉ nạp tiền được cho mốc chưa nạp");

        walletService.holdEscrow(client, milestone.getAmount(),
                "Escrow milestone \"%s\" — job #%d".formatted(milestone.getTitle(), job.getId()));
        milestone.setStatus(Milestone.Status.FUNDED);

        notificationService.notify(job.getAssignedFreelancer(), Notification.Type.MILESTONE_FUNDED,
                "Mốc \"%s\" (%s) đã được nạp escrow — bắt đầu làm thôi!"
                        .formatted(milestone.getTitle(), milestone.getAmount()),
                "/jobs/" + job.getId());
        return milestoneRepository.save(milestone);
    }

    /** Freelancer báo hoàn thành mốc, chờ client duyệt. */
    @Transactional
    public Milestone submit(User freelancer, Long milestoneId) {
        var milestone = find(milestoneId);
        var job = milestone.getJob();
        if (job.getAssignedFreelancer() == null
                || !job.getAssignedFreelancer().getId().equals(freelancer.getId())) {
            throw ApiException.forbidden("Bạn không phải freelancer của job này");
        }
        disputeGuard.requireNoOpenDispute(job.getId());
        requireStatus(milestone, Milestone.Status.FUNDED, "Chỉ nộp được mốc đã nạp escrow");

        milestone.setStatus(Milestone.Status.SUBMITTED);
        notificationService.notify(job.getClient(), Notification.Type.MILESTONE_SUBMITTED,
                "%s đã nộp mốc \"%s\" — kiểm tra và giải ngân nhé"
                        .formatted(freelancer.getFullName(), milestone.getTitle()),
                "/jobs/" + job.getId());
        return milestoneRepository.save(milestone);
    }

    /** Client duyệt mốc → giải ngân escrow của mốc cho freelancer (trừ phí nền tảng). */
    @Transactional
    public Milestone release(User client, Long milestoneId) {
        var milestone = find(milestoneId);
        var job = milestone.getJob();
        requireClientOwner(client, job);
        disputeGuard.requireNoOpenDispute(job.getId());
        if (milestone.getStatus() != Milestone.Status.FUNDED
                && milestone.getStatus() != Milestone.Status.SUBMITTED) {
            throw ApiException.badRequest("Chỉ giải ngân được mốc đã nạp escrow");
        }

        walletService.releaseEscrow(job.getClient(), job.getAssignedFreelancer(), milestone.getAmount(),
                feePercent, "Giải ngân milestone \"%s\" — job #%d".formatted(milestone.getTitle(), job.getId()));
        milestone.setStatus(Milestone.Status.RELEASED);

        notificationService.notify(job.getAssignedFreelancer(), Notification.Type.MILESTONE_RELEASED,
                "💸 Mốc \"%s\" đã được giải ngân — tiền đã về ví!".formatted(milestone.getTitle()),
                "/wallet");
        return milestoneRepository.save(milestone);
    }

    /** Client hủy mốc chưa nạp tiền. */
    @Transactional
    public Milestone cancel(User client, Long milestoneId) {
        var milestone = find(milestoneId);
        requireClientOwner(client, milestone.getJob());
        requireStatus(milestone, Milestone.Status.PENDING, "Chỉ hủy được mốc chưa nạp tiền");
        milestone.setStatus(Milestone.Status.CANCELLED);
        return milestoneRepository.save(milestone);
    }

    Milestone find(Long id) {
        return milestoneRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
    }

    private static void requireClientOwner(User client, Job job) {
        if (!job.getClient().getId().equals(client.getId())) {
            throw ApiException.forbidden("Bạn không phải chủ job này");
        }
    }

    private static void requireMilestoneJobInProgress(Job job) {
        if (!job.isMilestoneBased()) {
            throw ApiException.badRequest("Job này không dùng chế độ thanh toán theo milestone");
        }
        if (job.getStatus() != Job.Status.IN_PROGRESS) {
            throw ApiException.badRequest("Job không ở trạng thái đang thực hiện");
        }
    }

    private static void requireStatus(Milestone milestone, Milestone.Status expected, String message) {
        if (milestone.getStatus() != expected) {
            throw ApiException.badRequest(message);
        }
    }
}
