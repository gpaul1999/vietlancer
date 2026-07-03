package com.vietlancer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vietlancer.bid.BidService;
import com.vietlancer.job.JobService;
import com.vietlancer.subscription.SubscriptionService;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.user.UserRepository;
import com.vietlancer.wallet.WalletRepository;
import com.vietlancer.wallet.WalletService;
import com.vietlancer.wallet.WalletTransaction;
import com.vietlancer.wallet.WalletTransactionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Kiểm chứng các fix từ đợt review: sổ cái ví, optimistic locking, thứ tự Premium. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb3;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
class ReviewFixesIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JobService jobService;
    @Autowired BidService bidService;
    @Autowired WalletService walletService;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository transactionRepository;
    @Autowired SubscriptionService subscriptionService;

    private User newUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName("Test " + email)
                .role(role)
                .build());
    }

    @Test
    void soCaiViKhopVoiSoDu_sauKhiGiaiNganEscrow() {
        var client = newUser("ledger-client@test.vn", Role.CLIENT);
        var freelancer = newUser("ledger-freelancer@test.vn", Role.FREELANCER);
        walletService.deposit(client, new BigDecimal("10000000"), "test");

        var job = jobService.create(client, new JobService.CreateJobRequest(
                "Website landing page", "Cần lập trình web landing page bằng React, giao trong tuần.",
                null, null, null));
        var bid = bidService.place(freelancer, job.id(),
                new BidService.PlaceBidRequest(new BigDecimal("4000000"), 7, "Nhận ạ"));
        bidService.accept(client, bid.getId(), false);
        jobService.complete(client, job.id());

        // Sổ cái từng ví phải khớp chính xác số dư (balance + escrow)
        for (var user : new User[] {client, freelancer}) {
            var wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
            var ledgerSum = transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                    .map(WalletTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(ledgerSum)
                    .as("Tổng giao dịch của %s phải bằng balance + escrow", user.getEmail())
                    .isEqualByComparingTo(wallet.getBalance().add(wallet.getEscrowBalance()));
        }
        // Freelancer nhận 4tr - 10% = 3.6tr
        assertThat(walletService.getOrCreate(freelancer).getBalance()).isEqualByComparingTo("3600000");
    }

    @Test
    void optimisticLocking_chanThaoTacDongThoiTrenVi() {
        var user = newUser("lock-user@test.vn", Role.CLIENT);
        walletService.deposit(user, new BigDecimal("1000000"), "test");

        // Hai bản sao detached của cùng một ví (mô phỏng 2 request đồng thời)
        var copy1 = walletRepository.findByUserId(user.getId()).orElseThrow();
        var copy2 = walletRepository.findByUserId(user.getId()).orElseThrow();

        copy1.setBalance(copy1.getBalance().add(BigDecimal.ONE));
        walletRepository.saveAndFlush(copy1); // version tăng

        copy2.setBalance(copy2.getBalance().add(BigDecimal.TEN));
        assertThatThrownBy(() -> walletRepository.saveAndFlush(copy2))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void jobCuaClientPremium_xepTruocTrongKetQuaTimKiem() {
        var normalClient = newUser("order-normal@test.vn", Role.CLIENT);
        var premiumClient = newUser("order-premium@test.vn", Role.CLIENT);
        walletService.deposit(premiumClient, new BigDecimal("1000000"), "test");
        subscriptionService.subscribe(premiumClient);

        // Job của client Premium tạo TRƯỚC (cũ hơn) — nếu chỉ sort theo createdAt sẽ đứng sau
        var premiumJob = jobService.create(premiumClient, new JobService.CreateJobRequest(
                "Dịch tài liệu hợp đồng tiếng Anh", "Cần dịch thuật 20 trang hợp đồng tiếng Anh sang tiếng Việt.",
                null, null, null));
        jobService.create(normalClient, new JobService.CreateJobRequest(
                "Dịch phụ đề video tiếng Anh", "Cần dịch thuật phụ đề subtitle cho 10 video tiếng Anh.",
                null, null, null));

        var result = jobService.search("translation", null, 0, 10);
        assertThat(result.jobs().getFirst().id())
                .as("Job của client Premium phải đứng đầu dù đăng trước")
                .isEqualTo(premiumJob.id());
        assertThat(result.jobs().getFirst().client().premium()).isTrue();
    }
}
