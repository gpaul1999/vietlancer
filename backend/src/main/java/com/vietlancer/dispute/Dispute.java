package com.vietlancer.dispute;

import com.vietlancer.job.Job;
import com.vietlancer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dispute {

    public enum Status {
        OPEN,      // Đang chờ admin phân xử — job bị đóng băng
        RESOLVED,  // Admin đã phân xử, tiền đã chia
        WITHDRAWN  // Người mở tự rút khiếu nại
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User raisedBy;

    @Column(nullable = false, length = 4000)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    /** Số tiền escrow bị đóng băng tại thời điểm mở khiếu nại. */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal heldAmount;

    /** Kết quả phân xử: số tiền trả cho freelancer (phần còn lại hoàn client). */
    @Column(precision = 15, scale = 0)
    private BigDecimal amountToFreelancer;

    @Column(length = 2000)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    private User resolvedBy;

    private Instant resolvedAt;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
