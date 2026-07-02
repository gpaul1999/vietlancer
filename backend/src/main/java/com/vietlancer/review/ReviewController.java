package com.vietlancer.review;

import com.vietlancer.common.ApiException;
import com.vietlancer.job.Job;
import com.vietlancer.job.JobService;
import com.vietlancer.notification.Notification;
import com.vietlancer.notification.NotificationService;
import com.vietlancer.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final JobService jobService;
    private final NotificationService notificationService;

    public record CreateReviewBody(
            @NotNull Long jobId,
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 2000) String comment) {}

    public record ReviewDto(
            Long id, Long jobId, String jobTitle,
            Long reviewerId, String reviewerName,
            Integer rating, String comment, Instant createdAt) {}

    public record RatingSummary(Double average, long count, List<ReviewDto> reviews) {}

    /** Đánh giá 2 chiều sau khi job hoàn thành: client ↔ freelancer. */
    @PostMapping("/reviews")
    public ReviewDto create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateReviewBody body) {
        var job = jobService.find(body.jobId());
        if (job.getStatus() != Job.Status.COMPLETED) {
            throw ApiException.badRequest("Chỉ đánh giá được job đã hoàn thành");
        }
        var isClient = job.getClient().getId().equals(user.getId());
        var isFreelancer = job.getAssignedFreelancer() != null
                && job.getAssignedFreelancer().getId().equals(user.getId());
        if (!isClient && !isFreelancer) {
            throw ApiException.forbidden("Bạn không tham gia job này");
        }
        if (reviewRepository.existsByJobIdAndReviewerId(job.getId(), user.getId())) {
            throw ApiException.conflict("Bạn đã đánh giá job này rồi");
        }
        var reviewee = isClient ? job.getAssignedFreelancer() : job.getClient();
        var review = reviewRepository.save(Review.builder()
                .job(job)
                .reviewer(user)
                .reviewee(reviewee)
                .rating(body.rating())
                .comment(body.comment())
                .build());
        notificationService.notify(reviewee, Notification.Type.NEW_REVIEW,
                "%s vừa đánh giá bạn %d⭐ cho \"%s\"".formatted(user.getFullName(), body.rating(), job.getTitle()),
                "/profile/" + reviewee.getId());
        return toDto(review);
    }

    @GetMapping("/users/{id}/reviews")
    public RatingSummary forUser(@PathVariable Long id) {
        var reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(id).stream()
                .map(this::toDto)
                .toList();
        return new RatingSummary(
                reviewRepository.averageRating(id),
                reviewRepository.countByRevieweeId(id),
                reviews);
    }

    private ReviewDto toDto(Review r) {
        return new ReviewDto(
                r.getId(),
                r.getJob().getId(),
                r.getJob().getTitle(),
                r.getReviewer().getId(),
                r.getReviewer().getFullName(),
                r.getRating(),
                r.getComment(),
                r.getCreatedAt());
    }
}
