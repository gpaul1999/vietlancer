package com.vietlancer;

import static org.assertj.core.api.Assertions.assertThat;

import com.vietlancer.ai.MatchingService;
import com.vietlancer.ai.PricingService;
import com.vietlancer.bid.BidService;
import com.vietlancer.job.JobService;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.user.UserRepository;
import com.vietlancer.wallet.WalletService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb4;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
class Phase3IntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JobService jobService;
    @Autowired BidService bidService;
    @Autowired WalletService walletService;
    @Autowired MatchingService matchingService;
    @Autowired PricingService pricingService;

    private User newUser(String email, Role role, String skills) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName("Test " + email)
                .role(role)
                .skills(skills)
                .build());
    }

    @Test
    void matching_xepFreelancerDungChuyenMonLenTruoc() {
        var client = newUser("m-client@test.vn", Role.CLIENT, null);
        var webDev = newUser("m-webdev@test.vn", Role.FREELANCER, "React,Next.js,website,Spring Boot");
        newUser("m-designer@test.vn", Role.FREELANCER, "logo,photoshop,illustrator,banner");

        var job = jobService.create(client, new JobService.CreateJobRequest(
                "Xây website công ty", "Cần lập trình web React responsive, backend Spring Boot, deploy hosting.",
                null, null, null));

        var matches = matchingService.matchFreelancers(job.id(), 5);

        assertThat(matches).isNotEmpty();
        // Người đứng đầu phải là dân web (webDev mới tạo hoặc freelancer demo seed sẵn cũng skill React)
        assertThat(matches.getFirst().freelancer().getSkills().toLowerCase()).contains("react");
        assertThat(matches.getFirst().reasons()).isNotEmpty();

        // webDev phải có mặt; designer (không khớp topic web) phải xếp sau webDev nếu xuất hiện
        var webDevMatch = matches.stream()
                .filter(m -> m.freelancer().getId().equals(webDev.getId()))
                .findFirst();
        assertThat(webDevMatch).as("Freelancer web phải nằm trong danh sách gợi ý").isPresent();
        matches.stream()
                .filter(m -> m.freelancer().getSkills().contains("photoshop"))
                .forEach(designer -> assertThat(designer.score()).isLessThan(webDevMatch.get().score()));
    }

    @Test
    void pricing_thongKeTuBidLichSuTheoTopic() {
        var client = newUser("p-client@test.vn", Role.CLIENT, null);
        walletService.deposit(client, new BigDecimal("100000000"), "test");

        // Tạo 3 job dịch thuật, mỗi job 1 bid → đủ mẫu "toàn bộ chào giá" (>=3)
        long[] amounts = {2_000_000, 3_000_000, 4_000_000};
        for (var i = 0; i < amounts.length; i++) {
            var freelancer = newUser("p-fl" + i + "@test.vn", Role.FREELANCER, "dich thuat,tieng anh");
            var job = jobService.create(client, new JobService.CreateJobRequest(
                    "Dịch tài liệu " + i, "Cần dịch thuật tài liệu tiếng Anh sang tiếng Việt, khoảng 30 trang.",
                    null, null, null));
            bidService.place(freelancer, job.id(), new BidService.PlaceBidRequest(
                    BigDecimal.valueOf(amounts[i]), 7, "Em chuyên dịch thuật tài liệu tiếng Anh ạ"));
        }

        var suggestion = pricingService.suggest("translation");

        assertThat(suggestion.sampleSize()).isGreaterThanOrEqualTo(3);
        assertThat(suggestion.median()).isEqualByComparingTo("3000000");
        assertThat(suggestion.p25()).isNotNull();
        assertThat(suggestion.p75()).isNotNull();
    }

    @Test
    void pricing_chuaDuDuLieu_traSampleNhoDeUiAn() {
        var suggestion = pricingService.suggest("game-development");
        assertThat(suggestion.median()).isNull();
        assertThat(suggestion.sampleSize()).isLessThan(3);
    }
}
