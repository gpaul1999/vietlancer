package com.vietlancer.wallet;

import com.vietlancer.common.ApiException;
import com.vietlancer.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Transactional
    public Wallet getOrCreate(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> walletRepository.save(Wallet.builder().user(user).build()));
    }

    @Transactional
    public Wallet deposit(User user, BigDecimal amount, String note) {
        requirePositive(amount);
        var wallet = getOrCreate(user);
        wallet.setBalance(wallet.getBalance().add(amount));
        record(wallet, WalletTransaction.Type.DEPOSIT, amount, note);
        return walletRepository.save(wallet);
    }

    /** Trừ tiền trực tiếp từ số dư (vd: mua gói premium). */
    @Transactional
    public void charge(User user, BigDecimal amount, WalletTransaction.Type type, String note) {
        requirePositive(amount);
        var wallet = getOrCreate(user);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw ApiException.badRequest("Số dư ví không đủ. Vui lòng nạp thêm tiền.");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        record(wallet, type, amount.negate(), note);
        walletRepository.save(wallet);
    }

    /** Chuyển tiền từ số dư khả dụng sang escrow khi client chấp nhận bid. */
    @Transactional
    public void holdEscrow(User client, BigDecimal amount, String note) {
        requirePositive(amount);
        var wallet = getOrCreate(client);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw ApiException.badRequest(
                    "Số dư ví không đủ để giữ escrow (%s VND). Vui lòng nạp thêm tiền.".formatted(amount));
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setEscrowBalance(wallet.getEscrowBalance().add(amount));
        record(wallet, WalletTransaction.Type.ESCROW_HOLD, amount.negate(), note);
        walletRepository.save(wallet);
    }

    /** Giải ngân escrow: freelancer nhận tiền trừ phí nền tảng. */
    @Transactional
    public void releaseEscrow(User client, User freelancer, BigDecimal amount, int feePercent, String note) {
        var clientWallet = getOrCreate(client);
        if (clientWallet.getEscrowBalance().compareTo(amount) < 0) {
            throw ApiException.badRequest("Escrow không đủ để giải ngân");
        }
        clientWallet.setEscrowBalance(clientWallet.getEscrowBalance().subtract(amount));
        walletRepository.save(clientWallet);

        var fee = amount.multiply(BigDecimal.valueOf(feePercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        var payout = amount.subtract(fee);

        var freelancerWallet = getOrCreate(freelancer);
        freelancerWallet.setBalance(freelancerWallet.getBalance().add(payout));
        // Sổ cái phải khớp số dư: +amount (gross) rồi -fee → tổng = +payout = thay đổi balance
        record(freelancerWallet, WalletTransaction.Type.PAYOUT, amount, note);
        record(freelancerWallet, WalletTransaction.Type.PLATFORM_FEE, fee.negate(),
                "Phí nền tảng %d%% — %s".formatted(feePercent, note));
        walletRepository.save(freelancerWallet);
    }

    /** Hoàn escrow về số dư khả dụng của client (job bị hủy). */
    @Transactional
    public void refundEscrow(User client, BigDecimal amount, String note) {
        var wallet = getOrCreate(client);
        if (wallet.getEscrowBalance().compareTo(amount) < 0) {
            throw ApiException.badRequest("Escrow không đủ để hoàn");
        }
        wallet.setEscrowBalance(wallet.getEscrowBalance().subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));
        record(wallet, WalletTransaction.Type.ESCROW_REFUND, amount, note);
        walletRepository.save(wallet);
    }

    private void record(Wallet wallet, WalletTransaction.Type type, BigDecimal amount, String note) {
        transactionRepository.save(WalletTransaction.builder()
                .wallet(wallet)
                .type(type)
                .amount(amount)
                .note(note)
                .build());
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("Số tiền phải lớn hơn 0");
        }
    }
}
