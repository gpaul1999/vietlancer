package com.vietlancer.milestone;

import com.vietlancer.common.ApiException;
import com.vietlancer.job.JobService;
import com.vietlancer.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
public class MilestoneController {

    private final MilestoneService milestoneService;
    private final MilestoneRepository milestoneRepository;
    private final JobService jobService;

    public record CreateMilestoneBody(
            @NotBlank @Size(max = 200) String title,
            @NotNull @Positive BigDecimal amount,
            LocalDate dueDate) {}

    public record MilestoneDto(
            Long id, Long jobId, String title, BigDecimal amount, LocalDate dueDate,
            Milestone.Status status, Instant createdAt) {
        static MilestoneDto from(Milestone m) {
            return new MilestoneDto(m.getId(), m.getJob().getId(), m.getTitle(), m.getAmount(),
                    m.getDueDate(), m.getStatus(), m.getCreatedAt());
        }
    }

    @PostMapping("/jobs/{jobId}/milestones")
    public MilestoneDto create(
            @AuthenticationPrincipal User user, @PathVariable Long jobId,
            @Valid @RequestBody CreateMilestoneBody body) {
        var job = jobService.find(jobId);
        return MilestoneDto.from(milestoneService.create(user, job,
                new MilestoneService.CreateMilestoneRequest(body.title(), body.amount(), body.dueDate())));
    }

    /** Danh sách milestone của job — chỉ người tham gia job xem được. */
    @GetMapping("/jobs/{jobId}/milestones")
    public List<MilestoneDto> forJob(@AuthenticationPrincipal User user, @PathVariable Long jobId) {
        if (user == null) {
            throw ApiException.forbidden("Chưa đăng nhập");
        }
        var job = jobService.find(jobId);
        var isParticipant = job.getClient().getId().equals(user.getId())
                || (job.getAssignedFreelancer() != null
                        && job.getAssignedFreelancer().getId().equals(user.getId()));
        if (!isParticipant && user.getRole() != com.vietlancer.user.Role.ADMIN) {
            return List.of();
        }
        return milestoneRepository.findByJobIdOrderByCreatedAtAsc(jobId).stream()
                .map(MilestoneDto::from)
                .toList();
    }

    @PostMapping("/milestones/{id}/fund")
    public MilestoneDto fund(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return MilestoneDto.from(milestoneService.fund(user, id));
    }

    @PostMapping("/milestones/{id}/submit")
    public MilestoneDto submit(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return MilestoneDto.from(milestoneService.submit(user, id));
    }

    @PostMapping("/milestones/{id}/release")
    public MilestoneDto release(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return MilestoneDto.from(milestoneService.release(user, id));
    }

    @PostMapping("/milestones/{id}/cancel")
    public MilestoneDto cancel(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return MilestoneDto.from(milestoneService.cancel(user, id));
    }
}
