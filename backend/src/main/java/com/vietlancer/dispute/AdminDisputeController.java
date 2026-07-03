package com.vietlancer.dispute;

import com.vietlancer.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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

/** Chỉ ADMIN truy cập được (chặn ở SecurityConfig: /api/admin/** → hasRole ADMIN). */
@RestController
@RequestMapping("/api/admin/disputes")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeRepository disputeRepository;
    private final DisputeService disputeService;

    public record ResolveBody(
            @NotNull @PositiveOrZero BigDecimal amountToFreelancer,
            @Size(max = 2000) String note) {}

    @GetMapping
    public List<DisputeController.DisputeDto> list(
            @RequestParam(defaultValue = "OPEN") Dispute.Status status) {
        return disputeRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(DisputeController.DisputeDto::from)
                .toList();
    }

    @PostMapping("/{id}/resolve")
    public DisputeController.DisputeDto resolve(
            @AuthenticationPrincipal User admin, @PathVariable Long id, @Valid @RequestBody ResolveBody body) {
        return DisputeController.DisputeDto.from(
                disputeService.resolve(admin, id, body.amountToFreelancer(), body.note()));
    }
}
