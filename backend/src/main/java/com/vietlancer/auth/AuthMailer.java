package com.vietlancer.auth;

import com.vietlancer.notification.EmailService;
import com.vietlancer.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Soạn và gửi email cho luồng xác thực.
 *
 * <p>Khi CHƯA cấu hình SMTP (môi trường dev), link được ghi ra log để lập trình viên
 * vẫn chạy thử được toàn bộ luồng mà không cần mail server.
 */
@Component
@RequiredArgsConstructor
public class AuthMailer {

    private static final Logger log = LoggerFactory.getLogger(AuthMailer.class);

    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public void sendEmailVerification(User user, String rawToken) {
        var link = "/verify-email?token=" + rawToken;
        send(user, "Xác thực địa chỉ email",
                """
                Chào %s,

                Nhấn vào liên kết bên dưới để xác thực địa chỉ email và kích hoạt đầy đủ tài khoản VietLancer.
                Liên kết có hiệu lực trong 48 giờ."""
                        .formatted(user.getFullName()),
                link);
    }

    public void sendPasswordReset(User user, String rawToken) {
        var link = "/reset-password?token=" + rawToken;
        send(user, "Đặt lại mật khẩu",
                """
                Chào %s,

                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.
                Nhấn vào liên kết bên dưới để đặt mật khẩu mới. Liên kết có hiệu lực trong 1 giờ
                và chỉ dùng được một lần.

                Nếu bạn không yêu cầu, hãy bỏ qua email này — mật khẩu hiện tại vẫn an toàn."""
                        .formatted(user.getFullName()),
                link);
    }

    public void sendPasswordChangedNotice(User user) {
        send(user, "Mật khẩu đã được thay đổi",
                """
                Chào %s,

                Mật khẩu tài khoản VietLancer của bạn vừa được thay đổi và mọi phiên đăng nhập cũ đã bị đăng xuất.

                Nếu KHÔNG phải bạn thực hiện, hãy đặt lại mật khẩu ngay và liên hệ đội ngũ hỗ trợ."""
                        .formatted(user.getFullName()),
                null);
    }

    private void send(User user, String subject, String body, String link) {
        if (emailService.enabled()) {
            emailService.trySend(user.getEmail(), subject, body, link);
        } else if (link != null) {
            // Dev: chưa cấu hình SMTP → in link ra log để tự thao tác
            log.warn("[DEV] Chưa cấu hình SMTP. Link \"{}\" cho {}: {}{}",
                    subject, user.getEmail(), frontendUrl, link);
        }
    }
}
