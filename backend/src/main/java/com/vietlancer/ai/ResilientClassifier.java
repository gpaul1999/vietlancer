package com.vietlancer.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bọc classifier chính (vd: Claude) với classifier dự phòng (local):
 * nếu API ngoài lỗi/quá hạn mức, hệ thống vẫn phân loại được và không chặn việc đăng job.
 */
public final class ResilientClassifier implements TopicClassifier {

    private static final Logger log = LoggerFactory.getLogger(ResilientClassifier.class);

    private final TopicClassifier primary;
    private final TopicClassifier fallback;

    public ResilientClassifier(TopicClassifier primary, TopicClassifier fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public ClassificationResult classify(String title, String description) {
        try {
            return primary.classify(title, description);
        } catch (Exception e) {
            log.warn("Classifier chính lỗi ({}), chuyển sang engine dự phòng", e.getMessage());
            return fallback.classify(title, description);
        }
    }
}
