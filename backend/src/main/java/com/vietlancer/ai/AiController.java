package com.vietlancer.ai;

import com.vietlancer.topic.TopicRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final TopicClassifier topicClassifier;
    private final TopicRepository topicRepository;

    public record PreviewRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String description) {}

    public record PreviewTopic(String slug, String name, String icon, double confidence, String reason) {}

    public record PreviewResponse(List<PreviewTopic> topics, String explanation, String engine) {}

    /**
     * Xem trước topic AI sẽ gán cho job trước khi đăng.
     * Client không tự chọn topic — đây chỉ là bước minh bạch hóa kết quả AI.
     */
    @PostMapping("/classify-preview")
    public PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        var result = topicClassifier.classify(request.title(), request.description());
        var topics = result.topics().stream()
                .map(score -> topicRepository.findBySlug(score.slug())
                        .map(t -> new PreviewTopic(t.getSlug(), t.getName(), t.getIcon(),
                                score.confidence(), score.reason()))
                        .orElse(null))
                .filter(t -> t != null)
                .toList();
        return new PreviewResponse(topics, result.explanation(), result.engine());
    }
}
