package com.vietlancer.dispute;

import com.vietlancer.common.ApiException;
import com.vietlancer.job.Job;
import com.vietlancer.job.JobRepository;
import com.vietlancer.milestone.Milestone;
import com.vietlancer.milestone.MilestoneRepository;
import com.vietlancer.notification.Notification;
import com.vietlancer.notification.NotificationService;
import com.vietlancer.user.User;
import com.vietlancer.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final JobRepository jobRepository;
    private final MilestoneRepository milestoneRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Value("${app.platform.fee-percent}")
    private int feePercent;

    /** Client hoặc freelancer của job đang thực hiện mở khiếu nại → job bị đóng băng. */
    @Transactional
    public Dispute open(User user, Job job, String reason) {
        var isClient = job.getClient().getId().equals(user.getId());
        var isFreelancer = job.getAssignedFreelancer() != null
                && job.getAssignedFreelancer().getId().equals(user.getId());
        if (!isClient && !isFreelancer) {
            throw ApiException.forbidden("Bạn không tham gia job này");
        }
        if (job.getStatus() != Job.Status.IN_PROGRESS) {
            throw ApiException.badRequest("Chỉ khiếu nại được job đang thực hiện");
        }
        if (disputeRepository.existsByJobIdAndStatus(job.getId(), Dispute.Status.OPEN)) {
            throw ApiException.conflict("Job này đã có khiếu nại đang chờ phân xử");
        }

        var dispute = disputeRepository.save(Dispute.builder()
                .job(job)
                .raisedBy(user)
                .reason(reason)
                .heldAmount(heldAmount(job))
                .build());

        var otherParty = isClient ? job.getAssignedFreelancer() : job.getClient();
        notificationService.notify(otherParty, Notification.Type.DISPUTE_OPENED,
                "⚠️ %s đã mở khiếu nại cho job \"%s\". Job tạm khóa chờ phân xử."
                        .formatted(user.getFullName(), job.getTitle()),
                "/jobs/" + job.getId());
        return dispute;
    }

    /** Người mở tự rút khiếu nại → job hoạt động bình thường trở lại. */
    @Transactional
    public Dispute withdraw(User user, Long disputeId) {
        var dispute = find(disputeId);
        if (!dispute.getRaisedBy().getId().equals(user.getId())) {
            throw ApiException.forbidden("Chỉ người mở khiếu nại mới được rút");
        }
        if (dispute.getStatus() != Dispute.Status.OPEN) {
            throw ApiException.badRequest("Khiếu nại đã đóng");
        }
        dispute.setStatus(Dispute.Status.WITHDRAWN);
        dispute.setResolvedAt(Instant.now());
        return disputeRepository.save(dispute);
    }

    /**
     * Admin phân xử: chia số tiền escrow đang giữ — trả freelancer `amountToFreelancer`
     * (trừ phí nền tảng), hoàn phần còn lại cho client, rồi đóng job.
     */
    @Transactional
    public Dispute resolve(User admin, Long disputeId, BigDecimal amountToFreelancer, String note) {
        var dispute = find(disputeId);
        if (dispute.getStatus() != Dispute.Status.OPEN) {
            throw ApiException.badRequest("Khiếu nại đã được xử lý");
        }
        var job = dispute.getJob();
        var held = heldAmount(job);
        if (amountToFreelancer == null || amountToFreelancer.signum() < 0
                || amountToFreelancer.compareTo(held) > 0) {
            throw ApiException.badRequest(
                    "Số tiền trả freelancer phải trong khoảng 0 – %s (tổng escrow đang giữ)".formatted(held));
        }

        if (amountToFreelancer.signum() > 0) {
            walletService.releaseEscrow(job.getClient(), job.getAssignedFreelancer(), amountToFreelancer,
                    feePercent, "Phân xử khiếu nại #%d — job #%d".formatted(dispute.getId(), job.getId()));
        }
        var refund = held.subtract(amountToFreelancer);
        if (refund.signum() > 0) {
            walletService.refundEscrow(job.getClient(), refund,
                    "Hoàn escrow theo phân xử khiếu nại #%d".formatted(dispute.getId()));
        }

        // Đóng các mốc còn giữ tiền (tiền đã được chia ở trên)
        if (job.isMilestoneBased()) {
            milestoneRepository
                    .findByJobIdAndStatusIn(job.getId(), MilestoneRepository.HELD_STATUSES)
                    .forEach(m -> {
                        m.setStatus(Milestone.Status.CANCELLED);
                        milestoneRepository.save(m);
                    });
        } else {
            job.setEscrowAmount(BigDecimal.ZERO);
        }

        job.setStatus(amountToFreelancer.signum() > 0 ? Job.Status.COMPLETED : Job.Status.CANCELLED);
        jobRepository.save(job);

        dispute.setStatus(Dispute.Status.RESOLVED);
        dispute.setAmountToFreelancer(amountToFreelancer);
        dispute.setResolutionNote(note);
        dispute.setResolvedBy(admin);
        dispute.setResolvedAt(Instant.now());

        var message = "Khiếu nại job \"%s\" đã được phân xử: freelancer nhận %s, client được hoàn %s."
                .formatted(job.getTitle(), amountToFreelancer, refund);
        notificationService.notify(job.getClient(), Notification.Type.DISPUTE_RESOLVED, message,
                "/jobs/" + job.getId());
        notificationService.notify(job.getAssignedFreelancer(), Notification.Type.DISPUTE_RESOLVED, message,
                "/jobs/" + job.getId());
        return disputeRepository.save(dispute);
    }

    /** Tổng escrow đang giữ cho job: toàn phần (escrowAmount) hoặc tổng các mốc FUNDED/SUBMITTED. */
    public BigDecimal heldAmount(Job job) {
        if (job.isMilestoneBased()) {
            return milestoneRepository.heldAmountForJob(job.getId());
        }
        return job.getEscrowAmount() == null ? BigDecimal.ZERO : job.getEscrowAmount();
    }

    Dispute find(Long id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khiếu nại"));
    }
}
