package com.vietlancer.milestone;

import com.vietlancer.job.Job;
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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "milestones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Milestone {

    public enum Status {
        PENDING,    // Mới tạo, chưa nạp tiền
        FUNDED,     // Client đã nạp escrow cho mốc này — freelancer yên tâm làm
        SUBMITTED,  // Freelancer báo đã hoàn thành mốc, chờ client duyệt
        RELEASED,   // Client duyệt — tiền đã giải ngân cho freelancer
        CANCELLED   // Hủy (chỉ khi PENDING, hoặc đóng do hủy job/tranh chấp)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Job job;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    private LocalDate dueDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Optimistic locking: chặn double-click fund/release cùng một mốc. */
    @jakarta.persistence.Version
    private long version;
}
