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
        return jobService.search(topic, q, page, Math.min(size, 50));
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

    @PostMapping("/{id}/complete")
    public JobDto complete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobService.complete(user, id);
    }

    @PostMapping("/{id}/cancel")
    public JobDto cancel(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return jobService.cancel(user, id);
    }
}
