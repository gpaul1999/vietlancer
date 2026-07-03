package com.vietlancer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vietlancer.bid.BidService;
import com.vietlancer.common.ApiException;
import com.vietlancer.dispute.DisputeService;
import com.vietlancer.job.Job;
import com.vietlancer.job.JobService;
import com.vietlancer.milestone.Milestone;
import com.vietlancer.milestone.MilestoneService;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
class MilestoneAndDisputeIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JobService jobService;
    @Autowired BidService bidService;
    @Autowired MilestoneService milestoneService;
    @Autowired DisputeService disputeService;
    @Autowired WalletService walletService;

    private User newUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName("Test " + role + " " + email)
                .role(role)
                .build());
    }

    private record Setup(User client, User freelancer, Long jobId, Long bidId) {}

    private Setup acceptedJob(String prefix, boolean useMilestones) {
        var client = newUser(prefix + "-client@test.vn", Role.CLIENT);
        var freelancer = newUser(prefix + "-freelancer@test.vn", Role.FREELANCER);
        walletService.deposit(client, new BigDecimal("10000000"), "test");
        var job = jobService.create(client, new JobService.CreateJobRequest(
                "Website công ty " + prefix, "Cần lập trình web React responsive có form liên hệ và hosting.",
                null, null, LocalDate.now().plusDays(30)));
        var bid = bidService.place(freelancer, job.id(),
                new BidService.PlaceBidRequest(new BigDecimal("6000000"), 15, "Nhận ạ"));
        bidService.accept(client, bid.getId(), useMilestones);
        return new Setup(client, freelancer, job.id(), bid.getId());
    }

    @Test
    void milestoneFlow_fundSubmitRelease_tienChiaTheoTungMoc() {
        var s = acceptedJob("ms", true);

        // Chế độ milestone: accept KHÔNG giữ tiền
        var wallet = walletService.getOrCreate(s.client());
        assertThat(wallet.getBalance()).isEqualByComparingTo("10000000");
        assertThat(wallet.getEscrowBalance()).isEqualByComparingTo("0");

        var jobEntity = jobService.find(s.jobId());
        var m1 = milestoneService.create(s.client(), jobEntity,
                new MilestoneService.CreateMilestoneRequest("Thiết kế giao diện", new BigDecimal("2000000"), null));
        var m2 = milestoneService.create(s.client(), jobEntity,
                new MilestoneService.CreateMilestoneRequest("Code + deploy", new BigDecimal("4000000"), null));

        // Freelancer chưa thể submit mốc chưa nạp tiền
        assertThatThrownBy(() -> milestoneService.submit(s.freelancer(), m1.getId()))
                .isInstanceOf(ApiException.class);

        // Nạp mốc 1 → escrow chỉ giữ đúng 2tr (không khóa toàn bộ 6tr)
        milestoneService.fund(s.client(), m1.getId());
        wallet = walletService.getOrCreate(s.client());
        assertThat(wallet.getBalance()).isEqualByComparingTo("8000000");
        assertThat(wallet.getEscrowBalance()).isEqualByComparingTo("2000000");

        // Submit + release mốc 1 → freelancer nhận 90%
        milestoneService.submit(s.freelancer(), m1.getId());
        milestoneService.release(s.client(), m1.getId());
        assertThat(walletService.getOrCreate(s.freelancer()).getBalance()).isEqualByComparingTo("1800000");

        // Chưa xong mốc 2 (chưa nạp) → complete được (mốc PENDING tự hủy)
        milestoneService.fund(s.client(), m2.getId());
        // Còn mốc đang giữ tiền → không complete được
        assertThatThrownBy(() -> jobService.complete(s.client(), s.jobId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("milestone");
        milestoneService.release(s.client(), m2.getId());

        var completed = jobService.complete(s.client(), s.jobId());
        assertThat(completed.status()).isEqualTo(Job.Status.COMPLETED);
        // Tổng cộng freelancer nhận (2tr + 4tr) * 90%
        assertThat(walletService.getOrCreate(s.freelancer()).getBalance()).isEqualByComparingTo("5400000");
        assertThat(walletService.getOrCreate(s.client()).getEscrowBalance()).isEqualByComparingTo("0");
    }

    @Test
    void cancelJobMilestone_hoanTienMocDangGiu() {
        var s = acceptedJob("msc", true);
        var jobEntity = jobService.find(s.jobId());
        var m1 = milestoneService.create(s.client(), jobEntity,
                new MilestoneService.CreateMilestoneRequest("Mốc 1", new BigDecimal("3000000"), null));
        milestoneService.fund(s.client(), m1.getId());

        jobService.cancel(s.client(), s.jobId());

        var wallet = walletService.getOrCreate(s.client());
        assertThat(wallet.getBalance()).isEqualByComparingTo("10000000");
        assertThat(wallet.getEscrowBalance()).isEqualByComparingTo("0");
    }

    @Test
    void disputeFlow_dongBangJob_adminChiaTienEscrow() {
        var admin = newUser("dp-admin@test.vn", Role.ADMIN);
        var s = acceptedJob("dp", false); // escrow toàn phần 6tr

        // Freelancer mở khiếu nại
        var dispute = disputeService.open(s.freelancer(), jobService.find(s.jobId()),
                "Client không phản hồi sau khi tôi bàn giao");
        assertThat(dispute.getHeldAmount()).isEqualByComparingTo("6000000");

        // Job bị đóng băng: complete/cancel đều bị chặn
        assertThatThrownBy(() -> jobService.complete(s.client(), s.jobId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("khiếu nại");
        assertThatThrownBy(() -> jobService.cancel(s.client(), s.jobId()))
                .isInstanceOf(ApiException.class);

        // Admin phân xử: freelancer nhận 4tr (trừ phí còn 3.6tr), client hoàn 2tr
        disputeService.resolve(admin, dispute.getId(), new BigDecimal("4000000"), "Bàn giao được 2/3 khối lượng");

        assertThat(walletService.getOrCreate(s.freelancer()).getBalance()).isEqualByComparingTo("3600000");
        var clientWallet = walletService.getOrCreate(s.client());
        assertThat(clientWallet.getBalance()).isEqualByComparingTo("6000000"); // 4tr còn lại + 2tr hoàn
        assertThat(clientWallet.getEscrowBalance()).isEqualByComparingTo("0");
        assertThat(jobService.get(s.jobId()).status()).isEqualTo(Job.Status.COMPLETED);
    }

    @Test
    void khongTheMoHaiKhieuNaiCungJob_vaRutKhieuNaiMoLaiDuoc() {
        var s = acceptedJob("dp2", false);
        var jobEntity = jobService.find(s.jobId());

        var dispute = disputeService.open(s.client(), jobEntity, "Freelancer trễ hạn");
        assertThatThrownBy(() -> disputeService.open(s.freelancer(), jobService.find(s.jobId()), "Tôi cũng khiếu nại"))
                .isInstanceOf(ApiException.class);

        disputeService.withdraw(s.client(), dispute.getId());
        // Sau khi rút, job hoạt động lại bình thường
        var completed = jobService.complete(s.client(), s.jobId());
        assertThat(completed.status()).isEqualTo(Job.Status.COMPLETED);
    }
}
