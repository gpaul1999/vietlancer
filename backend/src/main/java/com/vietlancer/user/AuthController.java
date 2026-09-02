package com.vietlancer.user;

import com.vietlancer.auth.AuthMailer;
import com.vietlancer.auth.AuthTokenService;
import com.vietlancer.auth.VerificationToken;
import com.vietlancer.common.ApiException;
import com.vietlancer.config.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenService authTokenService;
    private final AuthMailer authMailer;

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank String fullName,
            @NotNull Role role) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, UserDto user) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record TokenRequest(@NotBlank String token) {}

    public record MessageResponse(String message) {}

    @PostMapping("/register")
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw ApiException.badRequest("Không thể tự đăng ký tài khoản quản trị");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email đã được sử dụng");
        }
        var user = userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(request.role())
                .build());

        sendVerificationEmail(user);
        return new AuthResponse(jwtService.generateToken(user.getEmail()), UserDto.from(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        var user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> ApiException.badRequest("Email hoặc mật khẩu không đúng"));
        return new AuthResponse(jwtService.generateToken(user.getEmail()), UserDto.from(user));
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw ApiException.forbidden("Chưa đăng nhập");
        }
        return UserDto.from(user);
    }

    // ==== Quên / đặt lại mật khẩu ====

    /**
     * Gửi link đặt lại mật khẩu.
     *
     * <p>BẢO MẬT: luôn trả về cùng một thông điệp dù email có tồn tại hay không,
     * để kẻ tấn công không dò được email nào đã đăng ký (user enumeration).
     */
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            var token = authTokenService.issue(
                    user, VerificationToken.Type.PASSWORD_RESET, AuthTokenService.PASSWORD_RESET_TTL);
            authMailer.sendPasswordReset(user, token);
        });
        return new MessageResponse(
                "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi liên kết đặt lại mật khẩu. "
                        + "Vui lòng kiểm tra hộp thư (kể cả mục spam).");
    }

    /** Đặt mật khẩu mới bằng token trong email. Mọi phiên đăng nhập cũ sẽ bị đăng xuất. */
    @PostMapping("/reset-password")
    @Transactional
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        var user = authTokenService.consume(request.token(), VerificationToken.Type.PASSWORD_RESET);

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        // Đặt lại được mật khẩu qua email ⇒ email chắc chắn thuộc về người dùng
        user.setEmailVerified(true);
        var saved = userRepository.save(user);

        authTokenService.invalidateAll(saved, VerificationToken.Type.PASSWORD_RESET);
        authMailer.sendPasswordChangedNotice(saved);

        // Cấp token mới để đăng nhập luôn, khỏi bắt người dùng nhập lại mật khẩu vừa đặt
        return new AuthResponse(jwtService.generateToken(saved.getEmail()), UserDto.from(saved));
    }

    // ==== Xác thực email ====

    @PostMapping("/verify-email")
    @Transactional
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        var user = authTokenService.consume(request.token(), VerificationToken.Type.EMAIL_VERIFICATION);
        user.setEmailVerified(true);
        userRepository.save(user);
        return new MessageResponse("Email đã được xác thực. Cảm ơn bạn!");
    }

    /** Gửi lại email xác thực cho người dùng đang đăng nhập. */
    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw ApiException.forbidden("Chưa đăng nhập");
        }
        if (user.isEmailVerified()) {
            throw ApiException.badRequest("Email của bạn đã được xác thực");
        }
        sendVerificationEmail(user);
        return new MessageResponse("Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư.");
    }

    private void sendVerificationEmail(User user) {
        var token = authTokenService.issue(
                user, VerificationToken.Type.EMAIL_VERIFICATION, AuthTokenService.EMAIL_VERIFICATION_TTL);
        authMailer.sendEmailVerification(user, token);
    }
}
