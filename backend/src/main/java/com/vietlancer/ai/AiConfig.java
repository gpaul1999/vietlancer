package com.vietlancer.ai;

import com.vietlancer.topic.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public TopicClassifier topicClassifier(AiProperties props, TopicRepository topicRepository) {
        var local = new LocalHybridClassifier();
        // Java 25 pattern matching switch để chọn engine theo config
        return switch (props.classifier() == null ? "local" : props.classifier().toLowerCase()) {
            case "claude" -> {
                if (props.anthropicApiKey() == null || props.anthropicApiKey().isBlank()) {
                    log.warn("APP_AI_CLASSIFIER=claude nhưng thiếu ANTHROPIC_API_KEY → dùng engine local");
                    yield local;
                }
                log.info("AI classifier: Claude ({}) với fallback local", props.claudeModel());
                yield new ResilientClassifier(new ClaudeClassifier(props, topicRepository), local);
            }
            default -> {
                log.info("AI classifier: local-hybrid (miễn phí, chạy tại chỗ)");
                yield local;
            }
        };
    }
}
