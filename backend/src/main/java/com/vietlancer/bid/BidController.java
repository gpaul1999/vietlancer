package com.vietlancer.bid;

import com.vietlancer.job.JobService;
import com.vietlancer.subscription.SubscriptionService;
import com.vietlancer.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
    private final BidRepository bidRepository;
    private final JobService jobService;
    private final SubscriptionService subscriptionService;

    public record PlaceBidBody(
            @NotNull @Positive BigDecimal amount,
            @NotNull @Min(1) @Max(365) Integer deliveryDays,
            @NotBlank @Size(max = 3000) String coverLetter) {}

    public record BidDto(
            Long id, Long jobId, String jobTitle,
            FreelancerRef freelancer,
            BigDecimal amount, Integer deliveryDays, String coverLetter,
            Bid.Status status, Instant createdAt,
            /** Cảnh báo chất lượng (AI check) — chỉ điền khi chủ job xem danh sách bid. */
            List<String> warnings) {

        public record FreelancerRef(Long id, String fullName, String avatarUrl, String skills, boolean premium) {}
    }

    @PostMapping("/jobs/{jobId}/bids")
    public BidDto place(
            @AuthenticationPrincipal User user, @PathVariable Long jobId, @Valid @RequestBody PlaceBidBody body) {
        var bid = bidService.place(user, jobId,
                new BidService.PlaceBidRequest(body.amount(), body.deliveryDays(), body.coverLetter()));
        return toDto(bid);
    }

    /**
     * Danh sách bid của job: chủ job thấy tất cả;
     * freelancer chỉ thấy bid của chính mình.
     */
    @GetMapping("/jobs/{jobId}/bids")
    public List<BidDto> forJob(@AuthenticationPrincipal User user, @PathVariable Long jobId) {
        var job = jobService.find(jobId);
        var bids = bidRepository.findByJobIdOrderByCreatedAtDesc(jobId);
        if (user != null && job.getClient().getId().equals(user.getId())) {
            // Chủ job: kèm cảnh báo chất lượng bid (thư chào ngắn/rập khuôn/chung chung)
            return toDtos(bids, bid -> qualityWarnings(bid, job));
        }
        if (user != null) {
            return toDtos(bids.stream()
                    .filter(b -> b.getFreelancer().getId().equals(user.getId()))
                    .toList());
        }
        return List.of();
    }

    @GetMapping("/bids/mine")
    public List<BidDto> mine(@AuthenticationPrincipal User user) {
        return toDtos(bidRepository.findByFreelancerIdOrderByCreatedAtDesc(user.getId()));
    }

    public record AcceptBody(boolean useMilestones) {}

    @PostMapping("/bids/{id}/accept")
    public BidDto accept(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody(required = false) AcceptBody body) {
        var useMilestones = body != null && body.useMilestones();
        return toDto(bidService.accept(user, id, useMilestones));
    }

    private BidDto toDto(Bid bid) {
        return toDto(bid, subscriptionService.isPremium(bid.getFreelancer().getId()), List.of());
    }

    /** Map danh sách bid với 1 query premium theo lô — tránh N+1. */
    private List<BidDto> toDtos(List<Bid> bids) {
        return toDtos(bids, bid -> List.of());
    }

    private List<BidDto> toDtos(List<Bid> bids, java.util.function.Function<Bid, List<String>> warningsFn) {
        if (bids.isEmpty()) {
            return List.of();
        }
        var freelancerIds = bids.stream().map(b -> b.getFreelancer().getId()).distinct().toList();
        var premiumIds = subscriptionService.premiumUserIds(freelancerIds);
        return bids.stream()
                .map(b -> toDto(b, premiumIds.contains(b.getFreelancer().getId()), warningsFn.apply(b)))
                .toList();
    }

    private BidDto toDto(Bid bid, boolean freelancerPremium, List<String> warnings) {
        var f = bid.getFreelancer();
        return new BidDto(
                bid.getId(),
                bid.getJob().getId(),
                bid.getJob().getTitle(),
                new BidDto.FreelancerRef(f.getId(), f.getFullName(), f.getAvatarUrl(), f.getSkills(),
                        freelancerPremium),
                bid.getAmount(),
                bid.getDeliveryDays(),
                bid.getCoverLetter(),
                bid.getStatus(),
                bid.getCreatedAt(),
                warnings);
    }

    /**
     * AI check chất lượng thư chào — giúp client lọc bid spam:
     * quá ngắn, rập khuôn (dùng lại nhiều nơi), hoặc chung chung không nhắc đến job.
     */
    private List<String> qualityWarnings(Bid bid, com.vietlancer.job.Job job) {
        var warnings = new java.util.ArrayList<String>();
        var letter = bid.getCoverLetter() == null ? "" : bid.getCoverLetter().trim();

        if (letter.length() < 30) {
            warnings.add("Thư chào quá ngắn");
        }
        if (bidRepository.countByFreelancerIdAndCoverLetter(bid.getFreelancer().getId(), bid.getCoverLetter()) > 1) {
            warnings.add("Thư chào rập khuôn — freelancer dùng lại nội dung y hệt ở job khác");
        }

        // Chung chung: không chứa từ khóa nào từ tiêu đề job (từ ≥4 ký tự) và tên topic
        var normalizedLetter = com.vietlancer.ai.LocalHybridClassifier.normalize(letter);
        var mentionsJob = java.util.Arrays.stream(
                        com.vietlancer.ai.LocalHybridClassifier.normalize(job.getTitle()).split("\\s+"))
                .filter(word -> word.length() >= 4)
                .anyMatch(normalizedLetter::contains)
                || job.getTopics().stream()
                        .map(t -> com.vietlancer.ai.LocalHybridClassifier.normalize(t.getName()))
                        .anyMatch(name -> !name.isBlank() && normalizedLetter.contains(name));
        if (!letter.isEmpty() && !mentionsJob) {
            warnings.add("Nội dung chung chung — không nhắc đến yêu cầu cụ thể của job");
        }
        return warnings;
    }
}
