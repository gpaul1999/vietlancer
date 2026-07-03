package com.vietlancer.dispute;

import com.vietlancer.job.JobService;
import com.vietlancer.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class DisputeController {

    private final DisputeService disputeService;
    private final DisputeRepository disputeRepository;
    private final JobService jobService;

    public record OpenDisputeBody(@NotBlank @Size(max = 4000) String reason) {}

    public record DisputeDto(
            Long id, Long jobId, String jobTitle,
            Long raisedById, String raisedByName,
            String reason, Dispute.Status status,
            BigDecimal heldAmount, BigDecimal amountToFreelancer, String resolutionNote,
            Instant createdAt, Instant resolvedAt) {
        static DisputeDto from(Dispute d) {
            return new DisputeDto(
                    d.getId(), d.getJob().getId(), d.getJob().getTitle(),
                    d.getRaisedBy().getId(), d.getRaisedBy().getFullName(),
                    d.getReason(), d.getStatus(),
                    d.getHeldAmount(), d.getAmountToFreelancer(), d.getResolutionNote(),
                    d.getCreatedAt(), d.getResolvedAt());
        }
    }

    @PostMapping("/jobs/{jobId}/disputes")
    public DisputeDto open(
            @AuthenticationPrincipal User user, @PathVariable Long jobId,
            @Valid @RequestBody OpenDisputeBody body) {
        var job = jobService.find(jobId);
        return DisputeDto.from(disputeService.open(user, job, body.reason()));
    }

    /** Khiếu nại của job — người tham gia job xem được. */
    @GetMapping("/jobs/{jobId}/disputes")
    public List<DisputeDto> forJob(@AuthenticationPrincipal User user, @PathVariable Long jobId) {
        if (user == null) {
            return List.of();
        }
        var job = jobService.find(jobId);
        var isParticipant = job.getClient().getId().equals(user.getId())
                || (job.getAssignedFreelancer() != null
                        && job.getAssignedFreelancer().getId().equals(user.getId()));
        if (!isParticipant && user.getRole() != com.vietlancer.user.Role.ADMIN) {
            return List.of();
        }
        return disputeRepository.findByJobIdOrderByCreatedAtDesc(jobId).stream()
                .map(DisputeDto::from)
                .toList();
    }

    @GetMapping("/disputes/mine")
    public List<DisputeDto> mine(@AuthenticationPrincipal User user) {
        return disputeRepository.findAllForUser(user.getId()).stream().map(DisputeDto::from).toList();
    }

    @PostMapping("/disputes/{id}/withdraw")
    public DisputeDto withdraw(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return DisputeDto.from(disputeService.withdraw(user, id));
    }
}
