package com.vietlancer.auth;

import com.vietlancer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Token dùng một lần cho xác thực email và đặt lại mật khẩu.
 *
 * <p>BẢO MẬT: chỉ lưu SHA-256 của token, KHÔNG lưu token gốc — nếu database bị lộ,
 * kẻ tấn công vẫn không dựng lại được link đặt lại mật khẩu.
 */
@Entity
@Table(name = "verification_tokens", indexes = @Index(columnList = "tokenHash"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationToken {

    public enum Type {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Khác null nghĩa là token đã dùng — không dùng lại được. */
    private Instant usedAt;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
