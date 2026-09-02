package com.vietlancer.auth;

import com.vietlancer.common.ApiException;
import com.vietlancer.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chặn hành động quan trọng (đăng job, chào giá) khi email chưa xác thực —
 * đây là nơi tài khoản ảo gây hại nhất.
 *
 * <p>Mặc định TẮT (`app.auth.require-verified-email=false`) để môi trường dev chưa có SMTP
 * vẫn dùng được mọi luồng. Production nên bật.
 */
@Component
public class EmailVerificationGuard {

    private final boolean required;

    public EmailVerificationGuard(@Value("${app.auth.require-verified-email:false}") boolean required) {
        this.required = required;
    }

    public void requireVerified(User user) {
        if (required && !user.isEmailVerified()) {
            throw ApiException.forbidden(
                    "Vui lòng xác thực email trước khi thực hiện thao tác này. "
                            + "Kiểm tra hộp thư hoặc bấm \"Gửi lại email xác thực\" trong hồ sơ.");
        }
    }
}
