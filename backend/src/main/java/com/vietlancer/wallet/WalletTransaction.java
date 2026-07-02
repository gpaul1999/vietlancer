package com.vietlancer.wallet;

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
@Table(name = "wallet_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    public enum Type {
        DEPOSIT,          // Nạp tiền vào ví
        ESCROW_HOLD,      // Giữ tiền khi chấp nhận bid
        ESCROW_REFUND,    // Hoàn escrow khi hủy job
        PAYOUT,           // Freelancer nhận tiền khi job hoàn thành
        PLATFORM_FEE,     // Phí nền tảng
        SUBSCRIPTION      // Mua gói premium
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** Dương = cộng vào ví, âm = trừ khỏi ví. */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    private String note;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
