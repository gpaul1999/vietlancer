package com.vietlancer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private static List<BigDecimal> amounts(long... values) {
        return java.util.Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void percentile_trungViTrenDaySoLe() {
        var sorted = amounts(1_000_000, 2_000_000, 3_000_000, 4_000_000, 5_000_000);
        assertThat(PricingService.percentile(sorted, 0.50)).isEqualByComparingTo("3000000");
        assertThat(PricingService.percentile(sorted, 0.25)).isEqualByComparingTo("2000000");
        assertThat(PricingService.percentile(sorted, 0.75)).isEqualByComparingTo("4000000");
    }

    @Test
    void percentile_noiSuyTrenDaySoChan() {
        var sorted = amounts(1_000_000, 2_000_000, 3_000_000, 4_000_000);
        // median = trung bình 2 phần tử giữa
        assertThat(PricingService.percentile(sorted, 0.50)).isEqualByComparingTo("2500000");
    }

    @Test
    void percentile_motPhanTu_vaRong() {
        assertThat(PricingService.percentile(amounts(7_000_000), 0.5)).isEqualByComparingTo("7000000");
        assertThat(PricingService.percentile(List.of(), 0.5)).isNull();
    }
}
