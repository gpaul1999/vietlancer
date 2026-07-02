package com.vietlancer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocalHybridClassifierTest {

    private final LocalHybridClassifier classifier = new LocalHybridClassifier();

    private List<String> slugsOf(TopicClassifier.ClassificationResult result) {
        return result.topics().stream().map(TopicClassifier.TopicScore::slug).toList();
    }

    @Test
    void phanLoaiMultiLabel_jobThietKeWebBanHang() {
        var result = classifier.classify(
                "Thiết kế website bán hàng",
                "Cần website bán hàng online bằng React, tích hợp Shopee, giao diện mobile đẹp.");

        assertThat(slugsOf(result)).contains("web-development", "ecommerce");
        assertThat(result.topics()).hasSizeLessThanOrEqualTo(4);
        assertThat(result.engine()).isEqualTo(LocalHybridClassifier.ENGINE);
    }

    @Test
    void hieuTiengVietCoDau_vaKhongDau() {
        var coDau = classifier.classify("Cần dịch thuật tài liệu", "Dịch tài liệu tiếng Anh sang tiếng Việt");
        var khongDau = classifier.classify("Can dich thuat tai lieu", "Dich tai lieu tieng Anh sang tieng Viet");

        assertThat(slugsOf(coDau)).contains("translation");
        assertThat(slugsOf(khongDau)).contains("translation");
    }

    @Test
    void luonCoItNhatMotTopic_fallbackKhiKhongKhopGi() {
        var result = classifier.classify("Xyz", "Nội dung hoàn toàn không liên quan lĩnh vực nào qqq zzz");

        assertThat(result.topics()).isNotEmpty();
    }

    @Test
    void moTaKhongKhopGi_traVeTopicOther() {
        var result = classifier.classify("aaa", "bbb ccc ddd");

        assertThat(slugsOf(result)).containsExactly(LocalHybridClassifier.FALLBACK_SLUG);
    }

    @Test
    void tieuDeCoTrongSoCaoHon_moTa() {
        // Cùng một keyword: đặt ở tiêu đề phải cho điểm cao hơn đặt ở mô tả
        var inTitle = classifier.classify("logo", "công việc cần làm gấp trong tuần này");
        var inDesc = classifier.classify("việc gấp", "công việc cần làm logo trong tuần này");

        var titleScore = inTitle.topics().stream()
                .filter(t -> t.slug().equals("graphic-design")).findFirst().orElseThrow().confidence();
        var descScore = inDesc.topics().stream()
                .filter(t -> t.slug().equals("graphic-design")).findFirst().orElseThrow().confidence();
        assertThat(titleScore).isGreaterThan(descScore);
    }

    @Test
    void chuanHoaBoDauTiengViet() {
        assertThat(LocalHybridClassifier.normalize("Lập trình Web Đà Nẵng")).isEqualTo("lap trinh web da nang");
    }
}
