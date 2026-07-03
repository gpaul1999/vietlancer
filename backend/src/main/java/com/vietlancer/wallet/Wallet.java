package com.vietlancer.wallet;

import com.vietlancer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true)
    private User user;

    /** Số dư khả dụng (VND). */
    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal balance = BigDecimal.ZERO;

    /** Số tiền đang bị giữ trong escrow cho các job đang thực hiện. */
    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal escrowBalance = BigDecimal.ZERO;

    /** Optimistic locking: chặn lost update khi 2 giao dịch cùng sửa số dư. */
    @jakarta.persistence.Version
    private long version;
}
