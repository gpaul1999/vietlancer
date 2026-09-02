package com.vietlancer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vietlancer.auth.AuthTokenService;
import com.vietlancer.auth.VerificationToken;
import com.vietlancer.auth.VerificationTokenRepository;
import com.vietlancer.common.ApiException;
import com.vietlancer.config.JwtService;
import com.vietlancer.user.AuthController;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Luồng quên mật khẩu + xác thực email. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb5;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
class AuthFlowIntegrationTest {

    @Autowired AuthController authController;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthTokenService authTokenService;
    @Autowired VerificationTokenRepository tokenRepository;
    @Autowired JwtService jwtService;

    private User register(String email) {
        authController.register(new AuthController.RegisterRequest(
                email, "password123", "Người Dùng Test", Role.CLIENT));
        return userRepository.findByEmail(email).orElseThrow();
    }

    /** Lấy token gốc bằng cách phát hành mới (giá trị trong email không lưu lại được). */
    private String issueResetToken(User user) {
        return authTokenService.issue(
                user, VerificationToken.Type.PASSWORD_RESET, AuthTokenService.PASSWORD_RESET_TTL);
    }

    @Test
    void dangKy_taoTokenXacThucEmail_vaEmailChuaDuocXacThuc() {
        var user = register("verify-me@test.vn");

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(tokenRepository.findAll())
                .anyMatch(t -> t.getUser().getId().equals(user.getId())
                        && t.getType() == VerificationToken.Type.EMAIL_VERIFICATION);
    }

    @Test
    void xacThucEmail_bangTokenHopLe() {
        var user = register("verify-ok@test.vn");
        var token = authTokenService.issue(
                user, VerificationToken.Type.EMAIL_VERIFICATION, AuthTokenService.EMAIL_VERIFICATION_TTL);

        authController.verifyEmail(new AuthController.TokenRequest(token));

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void datLaiMatKhau_dangNhapDuocBangMatKhauMoi() {
        var user = register("reset-ok@test.vn");
        var token = issueResetToken(user);

        var response = authController.resetPassword(
                new AuthController.ResetPasswordRequest(token, "matkhaumoi456"));

        assertThat(response.token()).isNotBlank();
        var updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("matkhaumoi456", updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("password123", updated.getPassword())).isFalse();
        // Đặt lại qua email ⇒ email được coi là đã xác thực
        assertThat(updated.isEmailVerified()).isTrue();

        // Đăng nhập bằng mật khẩu mới phải thành công
        assertThat(authController.login(
                new AuthController.LoginRequest("reset-ok@test.vn", "matkhaumoi456")).token())
                .isNotBlank();
    }

    @Test
    void tokenDatLaiMatKhau_chiDungDuocMotLan() {
        var user = register("reset-once@test.vn");
        var token = issueResetToken(user);
        authController.resetPassword(new AuthController.ResetPasswordRequest(token, "matkhaumoi456"));

        assertThatThrownBy(() -> authController.resetPassword(
                new AuthController.ResetPasswordRequest(token, "thulaimotlan789")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không hợp lệ hoặc đã hết hạn");
    }

    @Test
    void tokenHetHan_biTuChoi() {
        var user = register("reset-expired@test.vn");
        // Phát hành token rồi đẩy hạn về quá khứ
        var token = authTokenService.issue(
                user, VerificationToken.Type.PASSWORD_RESET, Duration.ofHours(1));
        var stored = tokenRepository.findByTokenHashAndType(
                        AuthTokenServiceTestHelper.hashOf(token), VerificationToken.Type.PASSWORD_RESET)
                .orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        tokenRepository.save(stored);

        assertThatThrownBy(() -> authController.resetPassword(
                new AuthController.ResetPasswordRequest(token, "matkhaumoi456")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void tokenSaiLoai_khongDungCheoDuoc() {
        var user = register("wrong-type@test.vn");
        var emailToken = authTokenService.issue(
                user, VerificationToken.Type.EMAIL_VERIFICATION, AuthTokenService.EMAIL_VERIFICATION_TTL);

        // Token xác thực email KHÔNG được dùng để đặt lại mật khẩu
        assertThatThrownBy(() -> authController.resetPassword(
                new AuthController.ResetPasswordRequest(emailToken, "matkhaumoi456")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void quenMatKhau_khongTietLoEmailCoTonTaiHayKhong() {
        register("exists@test.vn");

        var forExisting = authController.forgotPassword(
                new AuthController.ForgotPasswordRequest("exists@test.vn"));
        var forMissing = authController.forgotPassword(
                new AuthController.ForgotPasswordRequest("khong-ton-tai@test.vn"));

        assertThat(forExisting.message()).isEqualTo(forMissing.message());
    }

    @Test
    void doiMatKhau_vaHieuHoaTokenJwtCu() {
        var user = register("kick-old-session@test.vn");
        var oldJwt = authController.login(
                new AuthController.LoginRequest("kick-old-session@test.vn", "password123")).token();
        var oldIssuedAt = jwtService.extractIssuedAt(oldJwt);

        authController.resetPassword(new AuthController.ResetPasswordRequest(
                issueResetToken(user), "matkhaumoi456"));

        // Mốc đổi mật khẩu phải SAU thời điểm phát hành token cũ → filter sẽ từ chối token đó
        var updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getPasswordChangedAt()).isNotNull();
        assertThat(oldIssuedAt).isBefore(updated.getPasswordChangedAt());
    }

    /** Truy cập hàm băm nội bộ để dựng lại bản ghi token trong test. */
    static class AuthTokenServiceTestHelper {
        static String hashOf(String rawToken) {
            try {
                var method = AuthTokenService.class.getDeclaredMethod("hash", String.class);
                method.setAccessible(true);
                return (String) method.invoke(null, rawToken);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
