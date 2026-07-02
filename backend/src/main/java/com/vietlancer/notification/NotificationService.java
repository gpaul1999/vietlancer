package com.vietlancer.notification;

import com.vietlancer.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    /**
     * Thông báo là non-critical: không bao giờ để lỗi tạo thông báo
     * làm hỏng nghiệp vụ chính (graceful degradation).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(User user, Notification.Type type, String message, String link) {
        try {
            // Chống spam: nếu đã có thông báo CHƯA ĐỌC cùng loại + cùng link thì không tạo thêm
            if (link != null && notificationRepository.existsByUserIdAndTypeAndLinkAndReadFalse(
                    user.getId(), type, link)) {
                return;
            }
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .type(type)
                    .message(message)
                    .link(link)
                    .build());
        } catch (Exception e) {
            log.warn("Không tạo được thông báo cho user {}: {}", user.getId(), e.getMessage());
        }
    }
}
