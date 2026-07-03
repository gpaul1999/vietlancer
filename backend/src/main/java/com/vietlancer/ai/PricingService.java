package com.vietlancer.ai;

import com.vietlancer.bid.Bid;
import com.vietlancer.bid.BidRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI gợi ý giá: thống kê giá bid lịch sử theo topic (p25 / median / p75).
 * Ưu tiên dữ liệu bid ĐÃ ĐƯỢC CHẤP NHẬN (giá thị trường thật); nếu mẫu quá ít
 * thì mở rộng sang toàn bộ bid của topic.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final int MIN_ACCEPTED_SAMPLES = 5;
    private static final int MIN_SAMPLES = 3;

    private final BidRepository bidRepository;

    public record PriceSuggestion(
            String topicSlug, BigDecimal p25, BigDecimal median, BigDecimal p75,
            long sampleSize, String source) {}

    @Transactional(readOnly = true)
    public PriceSuggestion suggest(String topicSlug) {
        var accepted = bidRepository.amountsByTopic(topicSlug, Bid.Status.ACCEPTED);
        if (accepted.size() >= MIN_ACCEPTED_SAMPLES) {
            return build(topicSlug, accepted, "bid được chấp nhận");
        }
        var all = bidRepository.amountsByTopic(topicSlug, null);
        if (all.size() >= MIN_SAMPLES) {
            return build(topicSlug, all, "toàn bộ chào giá");
        }
        // Chưa đủ dữ liệu — trả sampleSize để UI ẩn gợi ý
        return new PriceSuggestion(topicSlug, null, null, null, all.size(), "chưa đủ dữ liệu");
    }

    private static PriceSuggestion build(String slug, List<BigDecimal> amounts, String source) {
        var sorted = amounts.stream().sorted().toList();
        return new PriceSuggestion(
                slug,
                percentile(sorted, 0.25),
                percentile(sorted, 0.50),
                percentile(sorted, 0.75),
                sorted.size(),
                source);
    }

    /** Percentile theo phương pháp nearest-rank nội suy tuyến tính. */
    static BigDecimal percentile(List<BigDecimal> sorted, double p) {
        if (sorted.isEmpty()) {
            return null;
        }
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }
        var rank = p * (sorted.size() - 1);
        var low = (int) Math.floor(rank);
        var high = (int) Math.ceil(rank);
        if (low == high) {
            return sorted.get(low);
        }
        var fraction = BigDecimal.valueOf(rank - low);
        return sorted.get(low)
                .add(sorted.get(high).subtract(sorted.get(low)).multiply(fraction))
                .setScale(0, RoundingMode.HALF_UP);
    }
}
