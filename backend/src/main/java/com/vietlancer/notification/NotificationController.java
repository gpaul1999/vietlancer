package com.vietlancer.notification;

import com.vietlancer.common.ApiException;
import com.vietlancer.user.User;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public record NotificationDto(
            Long id, Notification.Type type, String message, String link, boolean read, Instant createdAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getMessage(), n.getLink(), n.isRead(),
                    n.getCreatedAt());
        }
    }

    public record UnreadCount(long count) {}

    @GetMapping
    public List<NotificationDto> list(@AuthenticationPrincipal User user) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(NotificationDto::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal User user) {
        return new UnreadCount(notificationRepository.countByUserIdAndReadFalse(user.getId()));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public void markRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        var notification = notificationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("Không phải thông báo của bạn");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @PostMapping("/read-all")
    @Transactional
    public void markAllRead(@AuthenticationPrincipal User user) {
        notificationRepository.markAllRead(user.getId());
    }
}
