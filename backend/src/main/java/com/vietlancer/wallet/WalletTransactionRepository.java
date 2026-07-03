package com.vietlancer.wallet;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    /** Tổng giá trị giao dịch theo loại — cho thống kê admin (GMV, doanh thu phí). */
    @Query("select coalesce(sum(t.amount), 0) from WalletTransaction t where t.type = :type")
    BigDecimal sumByType(@Param("type") WalletTransaction.Type type);
}
