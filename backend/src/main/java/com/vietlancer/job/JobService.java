package com.vietlancer.job;

import com.vietlancer.ai.TopicClassifier;
import com.vietlancer.bid.BidRepository;
import com.vietlancer.common.ApiException;
import com.vietlancer.notification.Notification;
import com.vietlancer.notification.NotificationService;
import com.vietlancer.subscription.SubscriptionService;
import com.vietlancer.topic.TopicRepository;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.wallet.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var normalizedQ = q == null || q.isBlank() ? null : q.trim();
        var normalizedTopic = topicSlug == null || topicSlug.isBlank() ? null : topicSlug.trim();
        var result = jobRepository.search(Job.Status.OPEN, normalizedTopic, normalizedQ, pageable);

        // Job của client Premium được ưu tiên hiển thị trước trong trang
        var jobs = result.getContent().stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(JobDto::client, Comparator.comparing(c -> !c.premium())))
                .toList();
        return new SearchResult(jobs, page, result.getTotalPages(), result.getTotalElements());
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
        };
        return jobs.stream().map(this::toDto).toList();
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
        return jobRepository
                .findOpenByTopicsExcludingBidder(Job.Status.OPEN, slugs, freelancer.getId(), PageRequest.of(0, 6))
                .stream()
                .map(this::toDto)
                .toList();
    }

    /** Client xác nhận hoàn thành → giải ngân escrow cho freelancer (trừ phí nền tảng). */
    @Transactional
    public JobDto complete(User client, Long jobId) {
        var job = find(jobId);
        requireOwner(client, job);
        if (job.getStatus() != Job.Status.IN_PROGRESS) {
            throw ApiException.badRequest("Chỉ job đang thực hiện mới có thể hoàn thành");
        }
        walletService.releaseEscrow(job.getClient(), job.getAssignedFreelancer(), job.getEscrowAmount(),
                feePercent, "Thanh toán job #%d: %s".formatted(job.getId(), job.getTitle()));
        job.setStatus(Job.Status.COMPLETED);
        notificationService.notify(job.getAssignedFreelancer(), Notification.Type.JOB_COMPLETED,
                "Job \"%s\" đã hoàn thành — tiền đã về ví của bạn. Đừng quên đánh giá client!"
                        .formatted(job.getTitle()),
                "/jobs/" + job.getId());
        return toDto(jobRepository.save(job));
    }

    /** Hủy job. Nếu đang thực hiện thì hoàn escrow về ví client. */
    @Transactional
    public JobDto cancel(User client, Long jobId) {
        var job = find(jobId);
        requireOwner(client, job);
        switch (job.getStatus()) {
            case OPEN -> job.setStatus(Job.Status.CANCELLED);
            case IN_PROGRESS -> {
                walletService.refundEscrow(job.getClient(), job.getEscrowAmount(),
                        "Hoàn escrow do hủy job #%d".formatted(job.getId()));
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
}
