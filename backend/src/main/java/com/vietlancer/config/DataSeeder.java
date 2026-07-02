package com.vietlancer.config;

import com.vietlancer.job.JobService;
import com.vietlancer.topic.Topic;
import com.vietlancer.topic.TopicRepository;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.user.UserRepository;
import com.vietlancer.wallet.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JobService jobService;
    private final WalletService walletService;

    @Override
    @Transactional
    public void run(String... args) {
        seedTopics();
        seedDemoData();
    }

    private void seedTopics() {
        if (topicRepository.count() > 0) {
            return;
        }
        // slug -> [tên tiếng Việt, tên tiếng Anh, icon]
        var topics = new String[][] {
                {"web-development", "Lập trình Web", "Web Development", "🌐"},
                {"mobile-app", "Ứng dụng di động", "Mobile Apps", "📱"},
                {"ui-ux-design", "Thiết kế UI/UX", "UI/UX Design", "🎨"},
                {"graphic-design", "Thiết kế đồ họa", "Graphic Design", "🖼️"},
                {"content-writing", "Viết nội dung", "Content Writing", "✍️"},
                {"translation", "Dịch thuật", "Translation", "🌍"},
                {"digital-marketing", "Digital Marketing", "Digital Marketing", "📣"},
                {"seo", "SEO", "SEO", "🔍"},
                {"video-editing", "Dựng video", "Video Editing", "🎬"},
                {"audio-music", "Âm thanh & Nhạc", "Audio & Music", "🎵"},
                {"data-entry", "Nhập liệu", "Data Entry", "⌨️"},
                {"data-science-ai", "Data & AI", "Data Science & AI", "🤖"},
                {"devops-cloud", "DevOps & Cloud", "DevOps & Cloud", "☁️"},
                {"game-development", "Phát triển Game", "Game Development", "🎮"},
                {"ecommerce", "Thương mại điện tử", "E-commerce", "🛒"},
                {"business-consulting", "Tư vấn kinh doanh", "Business Consulting", "💼"},
                {"accounting-finance", "Kế toán & Tài chính", "Accounting & Finance", "📊"},
                {"other", "Khác", "Other", "📌"},
        };
        for (var t : topics) {
            topicRepository.save(Topic.builder().slug(t[0]).name(t[1]).nameEn(t[2]).icon(t[3]).build());
        }
        log.info("Đã seed {} topic", topics.length);
    }

    private void seedDemoData() {
        if (userRepository.count() > 0) {
            return;
        }
        var client = userRepository.save(User.builder()
                .email("client@demo.vn")
                .password(passwordEncoder.encode("password123"))
                .fullName("Nguyễn Văn Khách")
                .role(Role.CLIENT)
                .bio("Chủ doanh nghiệp nhỏ, thường xuyên cần thuê freelancer.")
                .build());
        var freelancer = userRepository.save(User.builder()
                .email("freelancer@demo.vn")
                .password(passwordEncoder.encode("password123"))
                .fullName("Trần Thị Tự Do")
                .role(Role.FREELANCER)
                .bio("Fullstack developer 5 năm kinh nghiệm React/Spring Boot.")
                .skills("React,Next.js,Spring Boot,PostgreSQL")
                .hourlyRate(new BigDecimal("250000"))
                .build());

        walletService.deposit(client, new BigDecimal("20000000"), "Số dư demo");
        walletService.deposit(freelancer, new BigDecimal("1000000"), "Số dư demo");

        // Job mẫu — AI classifier tự gán topic từ mô tả
        var demoJobs = Map.of(
                "Thiết kế website bán hàng cho shop thời trang",
                "Cần làm website bán hàng online cho shop thời trang, có giỏ hàng, thanh toán, quản lý sản phẩm. "
                        + "Ưu tiên dùng React hoặc Next.js, giao diện đẹp chuẩn mobile. Có kinh nghiệm Shopee/WooCommerce là lợi thế.",
                "Dựng video quảng cáo TikTok cho nhãn hàng mỹ phẩm",
                "Cần freelancer dựng 10 video ngắn 15-30s để chạy quảng cáo TikTok. Biết dùng CapCut hoặc Premiere, "
                        + "có sẵn kho hiệu ứng, bắt trend tốt. Kèm kịch bản ngắn cho từng video.",
                "Xây chatbot AI tư vấn khách hàng bằng tiếng Việt",
                "Công ty cần chatbot AI trả lời khách hàng tự động trên website, hiểu tiếng Việt tự nhiên, "
                        + "tích hợp LLM và dữ liệu nội bộ. Ưu tiên bạn có kinh nghiệm Python, NLP và triển khai cloud.");

        demoJobs.forEach((title, description) -> jobService.create(client, new JobService.CreateJobRequest(
                title, description, new BigDecimal("3000000"), new BigDecimal("15000000"),
                LocalDate.now().plusDays(30))));

        log.info("Đã seed dữ liệu demo: client@demo.vn / freelancer@demo.vn (mật khẩu: password123)");
    }
}
