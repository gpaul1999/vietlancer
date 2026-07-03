package com.vietlancer.notification;

import com.vietlancer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final TransactionTemplate newTransaction;

    public NotificationService(
            NotificationRepository notificationRepository, PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Thông báo là non-critical: không bao giờ làm hỏng nghiệp vụ chính.
     * Nếu đang trong transaction, chỉ ghi thông báo SAU KHI transaction commit thành công
     * (afterCommit) — tránh "thông báo ma" khi nghiệp vụ chính rollback.
     */
    public void notify(User user, Notification.Type type, String message, String link) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    persist(user, type, message, link);
                }
            });
        } else {
            persist(user, type, message, link);
        }
    }

    private void persist(User user, Notification.Type type, String message, String link) {
        try {
            newTransaction.executeWithoutResult(status -> {
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
            });
        } catch (Exception e) {
            log.warn("Không tạo được thông báo cho user {}: {}", user.getId(), e.getMessage());
        }
    }
}
