package com.vietlancer.notification;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Gửi email non-critical, fire-and-forget trên virtual thread.
 * CHỈ hoạt động khi cấu hình SMTP qua env (SPRING_MAIL_HOST, SPRING_MAIL_USERNAME,
 * SPRING_MAIL_PASSWORD, SPRING_MAIL_PORT) — không có thì bỏ qua êm (dev không cần SMTP).
 * Theo pattern circuit-breaker của dự án: lỗi email không bao giờ chạm nghiệp vụ chính.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${app.mail.from:no-reply@vietlancer.vn}")
    private String from;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean enabled() {
        return mailSenderProvider.getIfAvailable() != null;
    }

    /** Gửi email thông báo kèm link về frontend. Không bao giờ ném lỗi ra ngoài. */
    public void trySend(String to, String subject, String body, String link) {
        var sender = mailSenderProvider.getIfAvailable();
        if (sender == null || to == null || to.isBlank()) {
            return;
        }
        executor.submit(() -> {
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject("[VietLancer] " + subject);
                message.setText(body + (link == null ? "" : "\n\nXem chi tiết: " + frontendUrl + link)
                        + "\n\n— VietLancer");
                sender.send(message);
            } catch (Exception e) {
                log.warn("Không gửi được email tới {}: {}", to, e.getMessage());
            }
        });
    }
}
