package com.vietlancer.ai;

import java.util.List;

/**
 * Bộ phân loại topic cho job post: nhận tiêu đề + mô tả, trả về danh sách topic
 * liên quan (multi-label) kèm độ tin cậy và giải thích.
 *
 * <p>Java 25 sealed interface: chỉ cho phép đúng các hiện thực đã biết,
 * giúp switch pattern matching kiểm soát đầy đủ các nhánh.
 */
public sealed interface TopicClassifier
        permits LocalHybridClassifier, ClaudeClassifier, ResilientClassifier {

    ClassificationResult classify(String title, String description);

    /** Một topic được gán kèm độ tin cậy [0..1] và lý do. */
    record TopicScore(String slug, double confidence, String reason) {}

    /** Kết quả phân loại: danh sách topic, giải thích tổng quan, engine đã dùng. */
    record ClassificationResult(List<TopicScore> topics, String explanation, String engine) {}
}
