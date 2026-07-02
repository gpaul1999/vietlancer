package com.vietlancer.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);

    boolean existsByUserIdAndTypeAndLinkAndReadFalse(Long userId, Notification.Type type, String link);

    @Modifying
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    void markAllRead(@Param("userId") Long userId);
}
