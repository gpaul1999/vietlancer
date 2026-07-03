package com.vietlancer.job;

import com.vietlancer.topic.Topic;
import com.vietlancer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    public enum Status {
        OPEN,          // Đang nhận bid
        IN_PROGRESS,   // Đã chọn freelancer, tiền trong escrow
        COMPLETED,     // Hoàn thành, đã giải ngân
        CANCELLED      // Đã hủy
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User client;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 10000)
    private String description;

    @Column(precision = 15, scale = 0)
    private BigDecimal budgetMin;

    @Column(precision = 15, scale = 0)
    private BigDecimal budgetMax;

    private LocalDate deadline;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    /** Topic do AI gán — client không tự chọn. Một job có thể thuộc nhiều topic. */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "job_topics")
    private Set<Topic> topics = new LinkedHashSet<>();

    /** Engine AI đã phân loại (local-hybrid / claude). */
    private String aiEngine;

    @Column(length = 2000)
    private String aiExplanation;

    /** Freelancer thắng bid (null khi chưa chọn). */
    @ManyToOne(fetch = FetchType.LAZY)
    private User assignedFreelancer;

    /** Giá trị bid được chấp nhận — số tiền giữ trong escrow (chế độ escrow toàn phần). */
    @Column(precision = 15, scale = 0)
    private BigDecimal escrowAmount;

    /**
     * true = thanh toán theo milestone: không giữ toàn bộ tiền khi accept bid,
     * client nạp escrow và giải ngân theo từng mốc công việc.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean milestoneBased = false;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
