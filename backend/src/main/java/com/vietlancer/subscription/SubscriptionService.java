package com.vietlancer.subscription;

import com.vietlancer.common.ApiException;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.wallet.WalletService;
import com.vietlancer.wallet.WalletTransaction;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Duration PERIOD = Duration.ofDays(30);

    private final SubscriptionRepository subscriptionRepository;
    private final WalletService walletService;

    @Value("${app.platform.client-premium-price}")
    private BigDecimal clientPremiumPrice;

    @Value("${app.platform.freelancer-premium-price}")
    private BigDecimal freelancerPremiumPrice;

    public boolean isPremium(User user) {
        return isPremium(user.getId());
    }

    public boolean isPremium(Long userId) {
        return subscriptionRepository.existsByUserIdAndExpiresAtAfter(userId, Instant.now());
    }

    /** Kiểm tra Premium theo lô (1 query cho cả danh sách) — dùng khi map DTO danh sách. */
    public java.util.Set<Long> premiumUserIds(java.util.Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return java.util.Set.of();
        }
        return java.util.Set.copyOf(subscriptionRepository.premiumUserIdsIn(userIds, Instant.now()));
    }

    /** Mua/gia hạn gói premium tương ứng với role, trừ tiền từ ví. */
    @Transactional
    public Subscription subscribe(User user) {
        // Java 25: local record + switch expression trên enum role → gói + giá tương ứng
        record PlanPrice(Subscription.Plan plan, BigDecimal price) {}
        var pp = switch (user.getRole()) {
            case CLIENT -> new PlanPrice(Subscription.Plan.CLIENT_PREMIUM, clientPremiumPrice);
            case FREELANCER -> new PlanPrice(Subscription.Plan.FREELANCER_PREMIUM, freelancerPremiumPrice);
            case ADMIN -> throw ApiException.badRequest("Tài khoản quản trị không cần gói Premium");
        };

        walletService.charge(user, pp.price(), WalletTransaction.Type.SUBSCRIPTION,
                "Mua gói %s (30 ngày)".formatted(pp.plan()));

        // Gia hạn nối tiếp nếu đang còn hạn, ngược lại bắt đầu từ bây giờ
        var current = subscriptionRepository.findFirstByUserIdOrderByExpiresAtDesc(user.getId());
        var startsAt = current
                .map(Subscription::getExpiresAt)
                .filter(exp -> exp.isAfter(Instant.now()))
                .orElse(Instant.now());

        return subscriptionRepository.save(Subscription.builder()
                .user(user)
                .plan(pp.plan())
                .startsAt(startsAt)
                .expiresAt(startsAt.plus(PERIOD))
                .build());
    }

    public BigDecimal priceFor(Role role) {
        return switch (role) {
            case CLIENT -> clientPremiumPrice;
            case FREELANCER -> freelancerPremiumPrice;
            case ADMIN -> BigDecimal.ZERO;
        };
    }

    public static ApiException notPremiumError() {
        return ApiException.forbidden("Tính năng này yêu cầu gói Premium");
    }
}
