package com.vietlancer.auth;

import com.vietlancer.common.ApiException;
import com.vietlancer.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phát hành và tiêu thụ token dùng một lần (xác thực email / đặt lại mật khẩu). */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    /** Link đặt lại mật khẩu sống ngắn để giảm cửa sổ tấn công. */
    public static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    public static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(48);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationTokenRepository tokenRepository;

    /**
     * Sinh token mới cho user, xóa các token cũ cùng loại (chỉ link mới nhất còn hiệu lực).
     *
     * @return token GỐC để gửi qua email — hệ thống không lưu lại giá trị này.
     */
    @Transactional
    public String issue(User user, VerificationToken.Type type, Duration ttl) {
        tokenRepository.deleteByUserIdAndType(user.getId(), type);

        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokenRepository.save(VerificationToken.builder()
                .user(user)
                .type(type)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plus(ttl))
                .build());
        return rawToken;
    }

    /**
     * Xác minh token và đánh dấu đã dùng.
     *
     * @throws ApiException 400 nếu token sai, đã dùng hoặc hết hạn (thông điệp giống nhau
     *                      để không tiết lộ token nào từng tồn tại).
     */
    @Transactional
    public User consume(String rawToken, VerificationToken.Type type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        var token = tokenRepository.findByTokenHashAndType(hash(rawToken), type)
                .orElseThrow(AuthTokenService::invalidToken);
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw invalidToken();
        }
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
        return token.getUser();
    }

    @Transactional
    public void invalidateAll(User user, VerificationToken.Type type) {
        tokenRepository.deleteByUserIdAndType(user.getId(), type);
    }

    private static ApiException invalidToken() {
        return ApiException.badRequest("Liên kết không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu liên kết mới.");
    }

    static String hash(String rawToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Không băm được token", e);
        }
    }
}
