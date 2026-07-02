package com.vietlancer.notification;

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

@Entity
@Table(name = "notifications", indexes = @Index(columnList = "user_id, is_read"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    public enum Type {
        NEW_BID,        // Client: có chào giá mới
        BID_ACCEPTED,   // Freelancer: bid được chọn
        BID_REJECTED,   // Freelancer: bid bị từ chối
        JOB_COMPLETED,  // Freelancer: job hoàn thành, đã nhận tiền
        NEW_MESSAGE,    // Có tin nhắn mới
        NEW_REVIEW      // Nhận được đánh giá mới
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false, length = 500)
    private String message;

    /** Đường dẫn frontend để điều hướng khi bấm vào (vd: /jobs/12). */
    private String link;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
