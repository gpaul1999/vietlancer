package com.vietlancer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vietlancer.bid.BidService;
import com.vietlancer.chat.ChatService;
import com.vietlancer.common.ApiException;
import com.vietlancer.job.JobService;
import com.vietlancer.notification.Notification;
import com.vietlancer.notification.NotificationRepository;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.user.UserRepository;
import com.vietlancer.wallet.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Kiểm tra toàn bộ luồng nghiệp vụ cốt lõi:
 * đăng job (AI gán topic) → bid → chặn chat → accept (escrow) → chat →
 * complete (giải ngân trừ phí) → thông báo.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
class MarketplaceFlowIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JobService jobService;
    @Autowired BidService bidService;
    @Autowired ChatService chatService;
    @Autowired WalletService walletService;
    @Autowired NotificationRepository notificationRepository;

    private User newUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName("Test " + role)
                .role(role)
                .skills(role == Role.FREELANCER ? "React,Spring Boot,website" : null)
                .build());
    }

    @Test
    void luongNghiepVuDayDu_tuDangJobDenGiaiNgan() {
        var client = newUser("it-client@test.vn", Role.CLIENT);
        var freelancer = newUser("it-freelancer@test.vn", Role.FREELANCER);
        walletService.deposit(client, new BigDecimal("10000000"), "test");

        // 1. Đăng job → AI tự gán topic (client không chọn)
        var job = jobService.create(client, new JobService.CreateJobRequest(
                "Làm website giới thiệu công ty",
                "Cần lập trình web bằng React và Spring Boot, giao diện responsive, có form liên hệ.",
                new BigDecimal("5000000"), new BigDecimal("8000000"), LocalDate.now().plusDays(30)));
        assertThat(job.topics()).isNotEmpty();
        assertThat(job.topics().stream().map(t -> t.slug())).contains("web-development");
        assertThat(job.aiEngine()).isEqualTo("local-hybrid");

        // 2. Freelancer chào giá → client nhận thông báo NEW_BID
        var bid = bidService.place(freelancer, job.id(),
                new BidService.PlaceBidRequest(new BigDecimal("6000000"), 10, "Em làm được ạ"));
        assertThat(notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(client.getId()))
                .anyMatch(n -> n.getType() == Notification.Type.NEW_BID);

        // 3. Chưa accept → không được chat (không ai Premium)
        var jobEntity = jobService.find(job.id());
        assertThatThrownBy(() -> chatService.open(freelancer, jobEntity, freelancer))
                .isInstanceOf(ApiException.class);

        // 4. Accept bid → tiền vào escrow, job IN_PROGRESS, freelancer nhận thông báo
        bidService.accept(client, bid.getId(), false);
        var clientWallet = walletService.getOrCreate(client);
        assertThat(clientWallet.getBalance()).isEqualByComparingTo("4000000");
        assertThat(clientWallet.getEscrowBalance()).isEqualByComparingTo("6000000");
        assertThat(notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(freelancer.getId()))
                .anyMatch(n -> n.getType() == Notification.Type.BID_ACCEPTED);

        // 5. Sau accept → chat được
        var conversation = chatService.open(freelancer, jobService.find(job.id()), freelancer);
        var message = chatService.send(freelancer, conversation.getId(), "Chào anh!");
        assertThat(message.getId()).isNotNull();

        // 6. Complete → escrow giải ngân, freelancer nhận 90% (phí nền tảng 10%)
        var completed = jobService.complete(client, job.id());
        assertThat(completed.status().name()).isEqualTo("COMPLETED");
        var freelancerWallet = walletService.getOrCreate(freelancer);
        assertThat(freelancerWallet.getBalance()).isEqualByComparingTo("5400000");
        assertThat(walletService.getOrCreate(client).getEscrowBalance()).isEqualByComparingTo("0");
    }

    @Test
    void khongDuTienTrongVi_khongTheAcceptBid() {
        var client = newUser("ngheo-client@test.vn", Role.CLIENT);
        var freelancer = newUser("bid-freelancer@test.vn", Role.FREELANCER);

        var job = jobService.create(client, new JobService.CreateJobRequest(
                "Viết bài chuẩn SEO", "Cần viết 10 bài blog content chuẩn SEO về du lịch.",
                null, null, null));
        var bid = bidService.place(freelancer, job.id(),
                new BidService.PlaceBidRequest(new BigDecimal("2000000"), 7, "Nhận ạ"));

        assertThatThrownBy(() -> bidService.accept(client, bid.getId(), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Số dư ví không đủ");
    }

    @Test
    void motFreelancerKhongTheBidHaiLanCungJob() {
        var client = newUser("dup-client@test.vn", Role.CLIENT);
        var freelancer = newUser("dup-freelancer@test.vn", Role.FREELANCER);

        var job = jobService.create(client, new JobService.CreateJobRequest(
                "Dựng video TikTok", "Cần dựng 5 video ngắn bằng CapCut, bắt trend tốt.",
                null, null, null));
        bidService.place(freelancer, job.id(),
                new BidService.PlaceBidRequest(new BigDecimal("1000000"), 5, "Lần 1"));

        assertThatThrownBy(() -> bidService.place(freelancer, job.id(),
                new BidService.PlaceBidRequest(new BigDecimal("900000"), 4, "Lần 2")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void goiYJobChoFreelancer_theoKyNangAiPhanTich() {
        var client = newUser("sug-client@test.vn", Role.CLIENT);
        var freelancer = newUser("sug-freelancer@test.vn", Role.FREELANCER);
        jobService.create(client, new JobService.CreateJobRequest(
                "Xây website bán hàng", "Cần lập trình web React, backend Spring Boot, deploy hosting.",
                null, null, null));

        var suggestions = jobService.suggestedFor(freelancer);

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.getFirst().topics().stream().map(t -> t.slug()))
                .contains("web-development");
    }
}
