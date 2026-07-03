package com.vietlancer.subscription;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByUserIdOrderByExpiresAtDesc(Long userId);

    boolean existsByUserIdAndExpiresAtAfter(Long userId, Instant now);

    /** Số người dùng đang có Premium còn hạn — cho thống kê admin. */
    @Query("select count(distinct s.user.id) from Subscription s where s.expiresAt > :now")
    long countActivePremiumUsers(@Param("now") Instant now);

    /** Lọc theo lô: những user nào trong danh sách đang có Premium còn hạn — tránh N+1. */
    @Query("""
            select distinct s.user.id from Subscription s
            where s.user.id in :userIds and s.expiresAt > :now
            """)
    List<Long> premiumUserIdsIn(@Param("userIds") Collection<Long> userIds, @Param("now") Instant now);
}
