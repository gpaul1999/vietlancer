package com.vietlancer.job;

import com.vietlancer.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final com.vietlancer.ai.MatchingService matchingService;

    public record MatchDto(
            Long freelancerId, String fullName, String avatarUrl, String skills,
            java.math.BigDecimal hourlyRate, double score, boolean premium,
            Double ratingAvg, long ratingCount, long completedJobs, List<String> reasons) {}

    public record CreateJobBody(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(min = 30, max = 10000) String description,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            LocalDate deadline) {}

    @PostMapping
    public JobDto create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateJobBody body) {
        return jobService.create(user, new JobService.CreateJobRequest(
                body.title(), body.description(), body.budgetMin(), body.budgetMax(), body.deadline()));
    }

    @GetMapping
    public JobService.SearchResult search(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return jobService.search(topic, q, Math.max(0, page), Math.clamp(size, 1, 50));
    }

    @GetMapping("/mine")
    public List<JobDto> mine(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw com.vietlancer.common.ApiException.forbidden("Chưa đăng nhập");
        }
        return jobService.mine(user);
    }

    /** Gợi ý job phù hợp cho freelancer dựa trên AI phân tích kỹ năng + bio. */
    @GetMapping("/suggested")
    public List<JobDto> suggested(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw com.vietlancer.common.ApiException.forbidden("Chưa đăng nhập");
        }
        return jobService.suggestedFor(user);
    }

    @GetMapping("/{id}")
    public JobDto get(@PathVariable Long id) {
        return jobService.get(id);
    }

    /** AI gợi ý freelancer phù hợp cho job — chỉ chủ job xem được. */
    @GetMapping("/{id}/matches")
    public List<MatchDto> matches(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) {
            throw com.vietlancer.common.ApiException.forbidden("Chưa đăng nhập");
        }
        var job = jobService.find(id);
        if (!job.getClient().getId().equals(user.getId())) {
            throw com.vietlancer.common.ApiException.forbidden("Bạn không phải chủ job này");
        }
        return matchingService.matchFreelancers(job.getId(), 5).stream()
                .map(m -> new MatchDto(
                        m.freelancer().getId(),
                        m.freelancer().getFullName(),
                        m.freelancer().getAvatarUrl(),
                        m.freelancer().getSkills(),
                        m.freelancer().getHourlyRate(),
                        m.score(),
                        m.premium(),
                        m.ratingAvg(),
                        m.ratingCount(),
                        m.completedJobs(),
                        m.reasons()))
                .toList();
    }

    @PostMapping("/{id}/complete")
    public JobDto complete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobService.complete(user, id);
    }

    @PostMapping("/{id}/cancel")
    public JobDto cancel(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobService.cancel(user, id);
    }
}
