package com.vietlancer.subscription;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByUserIdOrderByExpiresAtDesc(Long userId);

    boolean existsByUserIdAndExpiresAtAfter(Long userId, Instant now);
}
