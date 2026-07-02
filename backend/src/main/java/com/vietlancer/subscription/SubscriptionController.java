package com.vietlancer.subscription;

import com.vietlancer.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;

    public record SubscriptionStatus(boolean premium, String plan, Instant expiresAt, BigDecimal price) {}

    @GetMapping("/me")
    public SubscriptionStatus me(@AuthenticationPrincipal User user) {
        var current = subscriptionRepository.findFirstByUserIdOrderByExpiresAtDesc(user.getId())
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()));
        return new SubscriptionStatus(
                current.isPresent(),
                current.map(s -> s.getPlan().name()).orElse(null),
                current.map(Subscription::getExpiresAt).orElse(null),
                subscriptionService.priceFor(user.getRole()));
    }

    @PostMapping("/subscribe")
    public SubscriptionStatus subscribe(@AuthenticationPrincipal User user) {
        var sub = subscriptionService.subscribe(user);
        return new SubscriptionStatus(true, sub.getPlan().name(), sub.getExpiresAt(),
                subscriptionService.priceFor(user.getRole()));
    }
}
