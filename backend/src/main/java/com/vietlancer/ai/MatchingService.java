package com.vietlancer.ai;

import com.vietlancer.job.Job;
import com.vietlancer.job.JobRepository;
import com.vietlancer.review.ReviewRepository;
import com.vietlancer.subscription.SubscriptionService;
import com.vietlancer.topic.Topic;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.user.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI matching: xếp hạng freelancer phù hợp cho một job.
 * Điểm = 55% khớp topic (classifier phân tích skills+bio) + 25% rating
 *       + 15% kinh nghiệm (job đã hoàn thành) + 5% Premium.
 * Chạy hoàn toàn local (miễn phí); khi bật engine claude, chất lượng khớp topic tự tăng theo.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private static final double MIN_SCORE = 0.2;

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ReviewRepository reviewRepository;
    private final SubscriptionService subscriptionService;
    private final TopicClassifier topicClassifier;

    public record FreelancerMatch(
            User freelancer, double score, boolean premium,
            Double ratingAvg, long ratingCount, long completedJobs, List<String> reasons) {}

    @Transactional(readOnly = true)
    public List<FreelancerMatch> matchFreelancers(Long jobId, int limit) {
        // Load job trong transaction của chính mình — không phụ thuộc OSIV/entity detached
        var job = jobRepository.findById(jobId)
                .orElseThrow(() -> com.vietlancer.common.ApiException.notFound("Không tìm thấy job"));
        var jobTopicSlugs = job.getTopics().stream().map(Topic::getSlug).collect(Collectors.toSet());
        var jobTopicNames = job.getTopics().stream().collect(Collectors.toMap(Topic::getSlug, Topic::getName));
        if (jobTopicSlugs.isEmpty()) {
            return List.of();
        }

        var candidates = userRepository.findByRoleAndSkillsIsNotNull(Role.FREELANCER).stream()
                .filter(u -> u.getSkills() != null && !u.getSkills().isBlank())
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Batch: rating, premium, số job hoàn thành — 3 query cho toàn bộ ứng viên
        var ids = candidates.stream().map(User::getId).toList();
        var ratings = reviewRepository.ratingSummaries(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], Function.identity()));
        var premiumIds = subscriptionService.premiumUserIds(ids);
        var completedCounts = jobRepository.completedCountsByFreelancerIds(Job.Status.COMPLETED, ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        var matches = new ArrayList<FreelancerMatch>();
        for (var freelancer : candidates) {
            var profileText = String.join(". ",
                    freelancer.getSkills().replace(',', ' '),
                    freelancer.getBio() == null ? "" : freelancer.getBio());
            var classified = topicClassifier.classify("", profileText);
            var confidenceBySlug = classified.topics().stream()
                    .collect(Collectors.toMap(TopicClassifier.TopicScore::slug,
                            TopicClassifier.TopicScore::confidence, Math::max));

            // Khớp topic: trung bình độ tin cậy của freelancer trên các topic của job
            var topicScore = jobTopicSlugs.stream()
                    .mapToDouble(slug -> confidenceBySlug.getOrDefault(slug, 0.0))
                    .average()
                    .orElse(0.0);

            var rating = ratings.get(freelancer.getId());
            var ratingAvg = rating == null ? null : (Double) rating[1];
            var ratingCount = rating == null ? 0L : (Long) rating[2];
            var ratingScore = ratingAvg == null ? 0.5 : ratingAvg / 5.0; // chưa có đánh giá → trung tính

            var completed = completedCounts.getOrDefault(freelancer.getId(), 0L);
            var experienceScore = Math.min(completed / 10.0, 1.0);

            var premium = premiumIds.contains(freelancer.getId());

            var score = 0.55 * topicScore + 0.25 * ratingScore + 0.15 * experienceScore
                    + (premium ? 0.05 : 0.0);
            if (score < MIN_SCORE || topicScore == 0.0) {
                continue;
            }

            var reasons = buildReasons(jobTopicSlugs, jobTopicNames, confidenceBySlug,
                    ratingAvg, ratingCount, completed, premium);
            matches.add(new FreelancerMatch(freelancer, Math.round(score * 100.0) / 100.0,
                    premium, ratingAvg, ratingCount, completed, reasons));
        }

        matches.sort(Comparator.comparingDouble(FreelancerMatch::score).reversed());
        return matches.stream().limit(limit).toList();
    }

    private static List<String> buildReasons(
            java.util.Set<String> jobTopicSlugs, Map<String, String> topicNames,
            Map<String, Double> confidenceBySlug,
            Double ratingAvg, long ratingCount, long completed, boolean premium) {
        var reasons = new ArrayList<String>();
        jobTopicSlugs.stream()
                .filter(slug -> confidenceBySlug.getOrDefault(slug, 0.0) > 0)
                .sorted(Comparator.comparingDouble(
                        (String slug) -> confidenceBySlug.getOrDefault(slug, 0.0)).reversed())
                .limit(2)
                .forEach(slug -> reasons.add("Kỹ năng khớp \"%s\" (%.0f%%)"
                        .formatted(topicNames.get(slug), confidenceBySlug.get(slug) * 100)));
        if (ratingAvg != null) {
            reasons.add("Đánh giá %.1f/5 (%d lượt)".formatted(ratingAvg, ratingCount));
        }
        if (completed > 0) {
            reasons.add("Đã hoàn thành %d job trên nền tảng".formatted(completed));
        }
        if (premium) {
            reasons.add("Freelancer Premium ⭐");
        }
        return reasons;
    }
}
