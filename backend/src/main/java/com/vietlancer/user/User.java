package com.vietlancer.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(length = 2000)
    private String bio;

    /** Kỹ năng, phân tách bằng dấu phẩy. */
    private String skills;

    private BigDecimal hourlyRate;

    private String avatarUrl;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Trạng thái xác minh danh tính (KYC) — VERIFIED được huy hiệu "Đã xác minh". */
    public enum KycStatus {
        NONE,      // Chưa nộp hồ sơ
        PENDING,   // Đã nộp, chờ admin duyệt
        VERIFIED,  // Đã xác minh
        REJECTED   // Bị từ chối (xem kycNote)
    }

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus = KycStatus.NONE;

    /** Số CCCD/CMND người dùng khai khi nộp KYC (MVP: chưa upload ảnh giấy tờ). */
    private String kycIdNumber;

    /** Ghi chú của admin khi duyệt/từ chối. */
    private String kycNote;
}
