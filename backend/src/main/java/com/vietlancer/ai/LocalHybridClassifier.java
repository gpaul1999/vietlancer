package com.vietlancer.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classifier miễn phí chạy tại chỗ (không gọi API ngoài):
 * chuẩn hóa tiếng Việt (bỏ dấu) rồi chấm điểm theo từ điển keyword có trọng số
 * cho từng topic. Multi-label: một post có thể thuộc nhiều topic.
 */
public final class LocalHybridClassifier implements TopicClassifier {

    public static final String ENGINE = "local-hybrid";
    public static final String FALLBACK_SLUG = "other";

    /** Từ khóa + trọng số (1 = yếu, 2 = trung bình, 3 = mạnh). */
    private record Kw(String term, int weight) {}

    private static final Map<String, List<Kw>> TOPIC_KEYWORDS = buildDictionary();

    /** Ngưỡng tin cậy tối thiểu để gán topic. */
    private static final double THRESHOLD = 0.35;
    /** Số topic tối đa cho một post. */
    private static final int MAX_TOPICS = 4;

    @Override
    public ClassificationResult classify(String title, String description) {
        var normTitle = normalize(title == null ? "" : title);
        var normDesc = normalize(description == null ? "" : description);

        var scores = new HashMap<String, Double>();
        var reasons = new HashMap<String, List<String>>();

        for (var entry : TOPIC_KEYWORDS.entrySet()) {
            double score = 0;
            var matched = new ArrayList<String>();
            for (var kw : entry.getValue()) {
                var pattern = wordPattern(kw.term());
                // Từ khóa xuất hiện trong tiêu đề được nhân đôi trọng số
                if (pattern.matcher(normTitle).find()) {
                    score += kw.weight() * 2;
                    matched.add(kw.term());
                } else if (pattern.matcher(normDesc).find()) {
                    score += kw.weight();
                    matched.add(kw.term());
                }
            }
            if (score > 0) {
                scores.put(entry.getKey(), score);
                reasons.put(entry.getKey(), matched);
            }
        }

        var topics = scores.entrySet().stream()
                .map(e -> new TopicScore(
                        e.getKey(),
                        confidence(e.getValue()),
                        "Khớp từ khóa: " + String.join(", ", reasons.get(e.getKey()))))
                .sorted(Comparator.comparingDouble(TopicScore::confidence).reversed())
                .toList();

        var selected = topics.stream()
                .filter(t -> t.confidence() >= THRESHOLD)
                .limit(MAX_TOPICS)
                .toList();

        // Luôn có ít nhất 1 topic: lấy topic điểm cao nhất, hoặc "other" nếu không khớp gì
        if (selected.isEmpty()) {
            selected = topics.isEmpty()
                    ? List.of(new TopicScore(FALLBACK_SLUG, 0.3, "Không tìm thấy từ khóa đặc trưng"))
                    : List.of(topics.getFirst());
        }

        var explanation = "Phân loại dựa trên từ khóa đặc trưng trong tiêu đề và mô tả (engine miễn phí chạy tại chỗ).";
        return new ClassificationResult(selected, explanation, ENGINE);
    }

    /** Điểm thô → độ tin cậy [0..1): score/(score+4) tăng dần và bão hòa. */
    private static double confidence(double score) {
        return Math.round(score / (score + 4.0) * 100.0) / 100.0;
    }

    /** Bỏ dấu tiếng Việt, lowercase: "Lập trình Web" → "lap trinh web". */
    public static String normalize(String input) {
        var lower = input.toLowerCase();
        var decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return decomposed.replaceAll("[^a-z0-9+/#.\\s-]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Pattern wordPattern(String term) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])");
    }

    private static Map<String, List<Kw>> buildDictionary() {
        var dict = new LinkedHashMap<String, List<Kw>>();
        dict.put("web-development", kws(
                "website:3", "trang web:3", "web:2", "landing page:3", "wordpress:3", "react:3",
                "nextjs:3", "next.js:3", "vue:2", "angular:2", "frontend:3", "backend:3",
                "fullstack:3", "php:2", "laravel:3", "nodejs:3", "node.js:3", "spring boot:3",
                "django:2", "html:2", "css:2", "javascript:2", "typescript:2", "api:2",
                "web app:3", "thiet ke web:3", "lap trinh web:3", "ten mien:1", "hosting:1"));
        dict.put("mobile-app", kws(
                "mobile:3", "app:2", "ung dung:2", "dien thoai:2", "android:3", "ios:3",
                "flutter:3", "react native:3", "kotlin:2", "swift:2", "app store:2",
                "google play:2", "ung dung di dong:3"));
        dict.put("ui-ux-design", kws(
                "ui:3", "ux:3", "figma:3", "giao dien:2", "trai nghiem nguoi dung:3",
                "wireframe:3", "prototype:2", "thiet ke giao dien:3", "design system:3",
                "mockup:2", "user flow:2"));
        dict.put("graphic-design", kws(
                "logo:3", "banner:3", "poster:3", "brochure:2", "photoshop:3", "illustrator:3",
                "thiet ke do hoa:3", "do hoa:2", "nhan dien thuong hieu:3", "bo nhan dien:3",
                "name card:2", "in an:2", "catalogue:2", "infographic:2", "ve minh hoa:2",
                "illustration:2", "canva:2"));
        dict.put("content-writing", kws(
                "viet bai:3", "content:3", "bai viet:3", "noi dung:2", "copywriting:3",
                "blog:2", "kich ban:2", "bien tap:2", "viet lach:3", "chuan seo:2",
                "slogan:2", "bai pr:2"));
        dict.put("translation", kws(
                "dich thuat:3", "dich:2", "translation:3", "translate:3", "phien dich:3",
                "tieng anh:2", "tieng nhat:2", "tieng trung:2", "tieng han:2", "song ngu:2",
                "phu de:2", "subtitle:2", "ban dich:3"));
        dict.put("digital-marketing", kws(
                "marketing:3", "quang cao:3", "facebook ads:3", "google ads:3", "tiktok:2",
                "fanpage:2", "social media:3", "chay ads:3", "truyen thong:2",
                "chien dich:2", "email marketing:3", "influencer:2", "kols:2"));
        dict.put("seo", kws(
                "seo:3", "tu khoa:2", "keyword:2", "backlink:3", "len top:2",
                "toi uu tim kiem:3", "search engine:2", "google ranking:2", "audit website:2"));
        dict.put("video-editing", kws(
                "video:2", "dung video:3", "edit video:3", "premiere:3", "after effects:3",
                "capcut:2", "quay phim:2", "motion graphics:3", "animation:2", "intro:1",
                "youtube:2", "vlog:2", "dung phim:3"));
        dict.put("audio-music", kws(
                "am thanh:3", "audio:2", "nhac:2", "thu am:3", "mixing:3", "mastering:3",
                "podcast:2", "long tieng:3", "voice over:3", "beat:2", "hoa am:2",
                "phoi khi:2", "soundtrack:2"));
        dict.put("data-entry", kws(
                "nhap lieu:3", "data entry:3", "excel:2", "danh may:3", "xu ly du lieu:2",
                "tong hop du lieu:2", "thu thap du lieu:2", "crawl:2", "google sheet:2"));
        dict.put("data-science-ai", kws(
                "machine learning:3", "ai:1", "tri tue nhan tao:3", "data science:3",
                "phan tich du lieu:3", "python:2", "deep learning:3", "chatbot:3", "nlp:3",
                "computer vision:3", "du bao:2", "dashboard:2", "power bi:2", "tableau:2",
                "llm:3", "gpt:2", "claude:2", "mo hinh:1"));
        dict.put("devops-cloud", kws(
                "devops:3", "aws:3", "azure:2", "gcp:2", "docker:3", "kubernetes:3",
                "ci/cd:3", "server:2", "trien khai:2", "cloud:2", "linux:2", "vps:2",
                "terraform:3", "ha tang:2"));
        dict.put("game-development", kws(
                "game:3", "unity:3", "unreal:3", "gamedev:3", "lam game:3",
                "thiet ke game:3", "godot:3", "asset game:2", "2d:1", "3d:1"));
        dict.put("ecommerce", kws(
                "shopee:3", "lazada:3", "tiki:2", "thuong mai dien tu:3", "ban hang online:3",
                "gian hang:2", "shopify:3", "woocommerce:3", "dang san pham:2",
                "quan ly don hang:2", "livestream:2", "san thuong mai:3"));
        dict.put("business-consulting", kws(
                "tu van:2", "ke hoach kinh doanh:3", "business plan:3", "khoi nghiep:2",
                "startup:2", "nghien cuu thi truong:3", "phap ly:2", "hop dong:2",
                "chien luoc:2", "van hanh:1"));
        dict.put("accounting-finance", kws(
                "ke toan:3", "tai chinh:3", "bao cao thue:3", "thue:2", "so sach:2",
                "hoa don:2", "bao cao tai chinh:3", "kiem toan:3", "bhxh:2", "dong luong:1"));
        return dict;
    }

    private static List<Kw> kws(String... entries) {
        return List.of(entries).stream()
                .map(e -> {
                    var idx = e.lastIndexOf(':');
                    return new Kw(e.substring(0, idx), Integer.parseInt(e.substring(idx + 1)));
                })
                .toList();
    }
}
