package com.vietlancer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietlancer.topic.TopicRepository;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;

/**
 * Classifier dùng Claude API (bật khi có kinh phí: APP_AI_CLASSIFIER=claude + ANTHROPIC_API_KEY).
 * Độ chính xác cao với tiếng Việt, kèm giải thích tự nhiên vì sao post thuộc topic.
 */
public final class ClaudeClassifier implements TopicClassifier {

    public static final String ENGINE = "claude";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;
    private final TopicRepository topicRepository;

    public ClaudeClassifier(AiProperties props, TopicRepository topicRepository) {
        this.model = props.claudeModel();
        this.topicRepository = topicRepository;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", props.anthropicApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    /**
     * Retry với exponential backoff cho lỗi tạm thời (rule CLAUDE.md §2):
     * 3 lần thử, chờ 500ms → 1s. Hết retry mới ném lỗi để ResilientClassifier fallback local.
     * Chạy trên virtual threads nên sleep không tốn platform thread.
     */
    private String postWithRetry(Object body) {
        var maxAttempts = 3;
        var delayMs = 500L;
        org.springframework.web.client.RestClientException lastError = null;
        for (var attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.post().uri("/v1/messages").body(body).retrieve().body(String.class);
            } catch (org.springframework.web.client.RestClientException e) {
                lastError = e;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    delayMs *= 2;
                }
            }
        }
        throw lastError;
    }

    @Override
    public ClassificationResult classify(String title, String description) {
        var topicList = topicRepository.findAll().stream()
                .map(t -> "- %s: %s (%s)".formatted(t.getSlug(), t.getName(), t.getNameEn()))
                .collect(Collectors.joining("\n"));

        // Java 25 text block: prompt dễ đọc, dễ chỉnh
        var prompt = """
                Bạn là bộ phân loại topic cho nền tảng freelance Việt Nam.
                Cho danh sách topic sau (slug: tên):
                %s

                Phân tích job post dưới đây và gán 1-4 topic LIÊN QUAN NHẤT (multi-label).
                Chỉ trả về JSON thuần, không markdown, theo đúng cấu trúc:
                {"topics":[{"slug":"...","confidence":0.0,"reason":"..."}],"explanation":"..."}

                Tiêu đề: %s
                Mô tả: %s
                """.formatted(topicList, title, description);

        var body = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", new Object[] {Map.of("role", "user", "content", prompt)});

        var response = postWithRetry(body);

        try {
            var root = objectMapper.readTree(response);
            var text = root.path("content").path(0).path("text").asText()
                    .replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            var parsed = objectMapper.readTree(text);
            var topics = new ArrayList<TopicScore>();
            parsed.path("topics").forEach(node -> topics.add(new TopicScore(
                    node.path("slug").asText(),
                    node.path("confidence").asDouble(0.5),
                    node.path("reason").asText(""))));
            if (topics.isEmpty()) {
                throw new IllegalStateException("Claude không trả về topic nào");
            }
            return new ClassificationResult(topics, parsed.path("explanation").asText(""), ENGINE);
        } catch (Exception e) {
            throw new IllegalStateException("Không parse được phản hồi từ Claude: " + e.getMessage(), e);
        }
    }
}
