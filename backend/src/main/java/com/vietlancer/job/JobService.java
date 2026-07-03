package com.vietlancer.job;

import com.vietlancer.ai.TopicClassifier;
import com.vietlancer.bid.BidRepository;
import com.vietlancer.common.ApiException;
import com.vietlancer.dispute.DisputeGuard;
import com.vietlancer.milestone.Milestone;
import com.vietlancer.milestone.MilestoneRepository;
import com.vietlancer.notification.Notification;
import com.vietlancer.notification.NotificationService;
import com.vietlancer.subscription.SubscriptionService;
import com.vietlancer.topic.TopicRepository;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final TopicRepository topicRepository;
    private final BidRepository bidRepository;
    private final TopicClassifier topicClassifier;
    private final SubscriptionService subscriptionService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final DisputeGuard disputeGuard;
    private final MilestoneRepository milestoneRepository;

    @Value("${app.platform.fee-percent}")
    private int feePercent;

    public record CreateJobRequest(
            String title, String description, BigDecimal budgetMin, BigDecimal budgetMax, LocalDate deadline) {}

    public record SearchResult(List<JobDto> jobs, int page, int totalPages, long totalElements) {}

    /** Client đăng job: AI tự phân tích mô tả và gán topic (multi-label). */
    @Transactional
    public JobDto create(User client, CreateJobRequest request) {
        if (client.getRole() != Role.CLIENT) {
            throw ApiException.forbidden("Chỉ client mới được đăng job");
        }
        var result = topicClassifier.classify(request.title(), request.description());
        var slugs = result.topics().stream().map(TopicClassifier.TopicScore::slug).toList();
        var topics = topicRepository.findBySlugIn(slugs);
        if (topics.isEmpty()) {
            topics = topicRepository.findBySlug("other").map(List::of).orElse(List.of());
        }

        var perTopic = result.topics().stream()
                .map(t -> "%s (%.0f%%)".formatted(t.slug(), t.confidence() * 100))
                .collect(Collectors.joining(", "));

        var job = jobRepository.save(Job.builder()
                .client(client)
                .title(request.title())
                .description(request.description())
                .budgetMin(request.budgetMin())
                .budgetMax(request.budgetMax())
                .deadline(request.deadline())
                .aiEngine(result.engine())
                .aiExplanation(result.explanation() + " — " + perTopic)
                .build());
        job.getTopics().addAll(topics);
        return toDto(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public SearchResult search(String topicSlug, String q, int page, int size) {
        // Sort nằm trong ORDER BY của query (Premium trước, mới nhất trước) — pageable không sort
        var pageable = PageRequest.of(page, size);
        var normalizedQ = q == null || q.isBlank() ? null : q.trim();
        var normalizedTopic = topicSlug == null || topicSlug.isBlank() ? null : topicSlug.trim();
        var result = jobRepository.search(Job.Status.OPEN, normalizedTopic, normalizedQ, Instant.now(), pageable);
        return new SearchResult(toDtos(result.getContent()), page, result.getTotalPages(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public JobDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<JobDto> mine(User user) {
        var jobs = switch (user.getRole()) {
            case CLIENT -> jobRepository.findByClientIdOrderByCreatedAtDesc(user.getId());
            case FREELANCER -> jobRepository.findByAssignedFreelancerIdOrderByCreatedAtDesc(user.getId());
            case ADMIN -> jobRepository.findAll();
        };
        return toDtos(jobs);
    }

    /**
     * Gợi ý job cho freelancer: chạy AI classifier trên chuỗi kỹ năng + bio của freelancer
     * để suy ra các topic sở trường, rồi lấy job OPEN thuộc các topic đó (loại trừ job đã bid).
     */
    @Transactional(readOnly = true)
    public List<JobDto> suggestedFor(User freelancer) {
        var profileText = String.join(". ",
                freelancer.getSkills() == null ? "" : freelancer.getSkills().replace(',', ' '),
                freelancer.getBio() == null ? "" : freelancer.getBio());
        if (profileText.isBlank()) {
            return List.of();
        }
        var result = topicClassifier.classify("", profileText);
        var slugs = result.topics().stream()
                .map(TopicClassifier.TopicScore::slug)
                .filter(slug -> !"other".equals(slug))
                .toList();
        if (slugs.isEmpty()) {
            return List.of();
        }
        return toDtos(jobRepository
                .findOpenByTopicsExcludingBidder(Job.Status.OPEN, slugs, freelancer.getId(), PageRequest.of(0, 6)));
    }

    /**
     * Client xác nhận hoàn thành.
     * - Escrow toàn phần: giải ngân toàn bộ cho freelancer (trừ phí nền tảng).
     * - Milestone: các mốc đã giải ngân riêng — chỉ cần không còn mốc đang giữ tiền;
     *   mốc PENDING chưa nạp sẽ được hủy.
     */
    @Transactional
    public JobDto complete(User client, Long jobId) {
        var job = find(jobId);
        requireOwner(client, job);
        if (job.getStatus() != Job.Status.IN_PROGRESS) {
            throw ApiException.badRequest("Chỉ job đang thực hiện mới có thể hoàn thành");
        }
        disputeGuard.requireNoOpenDispute(job.getId());

        if (job.isMilestoneBased()) {
            if (milestoneRepository.existsByJobIdAndStatusIn(job.getId(), MilestoneRepository.HELD_STATUSES)) {
                throw ApiException.badRequest(
                        "Còn milestone đang giữ tiền — hãy giải ngân hoặc xử lý các mốc đó trước");
            }
            milestoneRepository
                    .findByJobIdAndStatusIn(job.getId(), List.of(Milestone.Status.PENDING))
                    .forEach(m -> {
                        m.setStatus(Milestone.Status.CANCELLED);
                        milestoneRepository.save(m);
                    });
        } else {
            walletService.releaseEscrow(job.getClient(), job.getAssignedFreelancer(), job.getEscrowAmount(),
                    feePercent, "Thanh toán job #%d: %s".formatted(job.getId(), job.getTitle()));
        }
        job.setStatus(Job.Status.COMPLETED);
        notificationService.notify(job.getAssignedFreelancer(), Notification.Type.JOB_COMPLETED,
                "Job \"%s\" đã hoàn thành — tiền đã về ví của bạn. Đừng quên đánh giá client!"
                        .formatted(job.getTitle()),
                "/jobs/" + job.getId());
        return toDto(jobRepository.save(job));
    }

    /** Hủy job. Nếu đang thực hiện thì hoàn toàn bộ escrow đang giữ về ví client. */
    @Transactional
    public JobDto cancel(User client, Long jobId) {
        var job = find(jobId);
        requireOwner(client, job);
        switch (job.getStatus()) {
            case OPEN -> job.setStatus(Job.Status.CANCELLED);
            case IN_PROGRESS -> {
                disputeGuard.requireNoOpenDispute(job.getId());
                if (job.isMilestoneBased()) {
                    // Hoàn từng mốc đang giữ tiền, hủy mọi mốc chưa giải ngân
                    milestoneRepository
                            .findByJobIdAndStatusIn(job.getId(), List.of(
                                    Milestone.Status.PENDING, Milestone.Status.FUNDED, Milestone.Status.SUBMITTED))
                            .forEach(m -> {
                                if (m.getStatus() != Milestone.Status.PENDING) {
                                    walletService.refundEscrow(job.getClient(), m.getAmount(),
                                            "Hoàn escrow mốc \"%s\" do hủy job #%d"
                                                    .formatted(m.getTitle(), job.getId()));
                                }
                                m.setStatus(Milestone.Status.CANCELLED);
                                milestoneRepository.save(m);
                            });
                } else {
                    walletService.refundEscrow(job.getClient(), job.getEscrowAmount(),
                            "Hoàn escrow do hủy job #%d".formatted(job.getId()));
                }
                job.setStatus(Job.Status.CANCELLED);
            }
            case COMPLETED, CANCELLED ->
                    throw ApiException.badRequest("Job đã kết thúc, không thể hủy");
        }
        return toDto(jobRepository.save(job));
    }

    public Job find(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy job"));
    }

    private static void requireOwner(User client, Job job) {
        if (!job.getClient().getId().equals(client.getId())) {
            throw ApiException.forbidden("Bạn không phải chủ job này");
        }
    }

    JobDto toDto(Job job) {
        return JobDto.from(
                job,
                bidRepository.countByJobId(job.getId()),
                subscriptionService.isPremium(job.getClient().getId()));
    }

    /** Map danh sách job → DTO với đúng 2 query phụ (bidCount + premium theo lô), tránh N+1. */
    List<JobDto> toDtos(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        var jobIds = jobs.stream().map(Job::getId).toList();
        var bidCounts = bidRepository.countByJobIds(jobIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        var clientIds = jobs.stream().map(j -> j.getClient().getId()).distinct().toList();
        var premiumIds = subscriptionService.premiumUserIds(clientIds);
        return jobs.stream()
                .map(job -> JobDto.from(
                        job,
                        bidCounts.getOrDefault(job.getId(), 0L),
                        premiumIds.contains(job.getClient().getId())))
                .toList();
    }
}
