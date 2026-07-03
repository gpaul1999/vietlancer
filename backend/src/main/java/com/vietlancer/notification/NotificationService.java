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
    private final EmailService emailService;
    private final TransactionTemplate newTransaction;

    public NotificationService(
            NotificationRepository notificationRepository,
            EmailService emailService,
            PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
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
            var created = newTransaction.execute(status -> {
                // Chống spam: nếu đã có thông báo CHƯA ĐỌC cùng loại + cùng link thì không tạo thêm
                if (link != null && notificationRepository.existsByUserIdAndTypeAndLinkAndReadFalse(
                        user.getId(), type, link)) {
                    return false;
                }
                notificationRepository.save(Notification.builder()
                        .user(user)
                        .type(type)
                        .message(message)
                        .link(link)
                        .build());
                return true;
            });
            // Email mirror thông báo in-app (chỉ khi SMTP được cấu hình; cùng luật chống spam)
            if (Boolean.TRUE.equals(created)) {
                emailService.trySend(user.getEmail(), subjectFor(type), message, link);
            }
        } catch (Exception e) {
            log.warn("Không tạo được thông báo cho user {}: {}", user.getId(), e.getMessage());
        }
    }

    private static String subjectFor(Notification.Type type) {
        return switch (type) {
            case NEW_BID -> "Có chào giá mới";
            case BID_ACCEPTED -> "Bạn được chọn cho job!";
            case BID_REJECTED -> "Kết quả chào giá";
            case JOB_COMPLETED -> "Job hoàn thành — tiền đã về ví";
            case NEW_MESSAGE -> "Tin nhắn mới";
            case NEW_REVIEW -> "Bạn nhận được đánh giá mới";
            case MILESTONE_FUNDED -> "Milestone đã được nạp escrow";
            case MILESTONE_SUBMITTED -> "Milestone chờ bạn duyệt";
            case MILESTONE_RELEASED -> "Milestone đã giải ngân";
            case DISPUTE_OPENED -> "Job có khiếu nại";
            case DISPUTE_RESOLVED -> "Khiếu nại đã được phân xử";
            case NEW_JOB_ALERT -> "Job mới thuộc lĩnh vực bạn theo dõi";
            case KYC_APPROVED -> "Hồ sơ xác minh được duyệt";
            case KYC_REJECTED -> "Hồ sơ xác minh bị từ chối";
            case JOB_REMOVED -> "Job của bạn đã bị gỡ";
        };
    }
}
