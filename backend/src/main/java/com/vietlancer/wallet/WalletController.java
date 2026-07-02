package com.vietlancer.wallet;

import com.vietlancer.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WalletTransactionRepository transactionRepository;

    public record WalletDto(BigDecimal balance, BigDecimal escrowBalance) {}

    public record TransactionDto(Long id, WalletTransaction.Type type, BigDecimal amount, String note, Instant createdAt) {}

    public record DepositRequest(@NotNull @Positive BigDecimal amount) {}

    @GetMapping
    public WalletDto get(@AuthenticationPrincipal User user) {
        var wallet = walletService.getOrCreate(user);
        return new WalletDto(wallet.getBalance(), wallet.getEscrowBalance());
    }

    /** MVP: nạp tiền mô phỏng. Phase 2 sẽ tích hợp VNPay/MoMo. */
    @PostMapping("/deposit")
    public WalletDto deposit(@AuthenticationPrincipal User user, @Valid @RequestBody DepositRequest request) {
        var wallet = walletService.deposit(user, request.amount(), "Nạp tiền (mô phỏng)");
        return new WalletDto(wallet.getBalance(), wallet.getEscrowBalance());
    }

    @GetMapping("/transactions")
    public List<TransactionDto> transactions(@AuthenticationPrincipal User user) {
        var wallet = walletService.getOrCreate(user);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(t -> new TransactionDto(t.getId(), t.getType(), t.getAmount(), t.getNote(), t.getCreatedAt()))
                .toList();
    }
}
