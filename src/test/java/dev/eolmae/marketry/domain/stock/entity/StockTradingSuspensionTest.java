package dev.eolmae.marketry.domain.stock.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StockTradingSuspensionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 7, 0);

    @Test
    void 제외가_시작되기_전에는_제외하지_않는다() {
        var suspension = StockTradingSuspension.start("005930", LocalDate.of(2026, 10, 7), NOW);

        assertThat(suspension.isExcludedOn(LocalDate.of(2026, 10, 14))).isFalse();
    }

    @Test
    void 제외가_시작된_날부터_재개_전까지_제외한다() {
        var suspension = StockTradingSuspension.start("005930", LocalDate.of(2026, 10, 7), NOW);
        suspension.markExcluded(LocalDate.of(2026, 10, 13), NOW);

        assertThat(suspension.isExcludedOn(LocalDate.of(2026, 10, 12))).isFalse();
        assertThat(suspension.isExcludedOn(LocalDate.of(2026, 10, 13))).isTrue();
        assertThat(suspension.isExcludedOn(LocalDate.of(2026, 10, 20))).isTrue();
    }

    @Test
    void 재개된_날부터는_다시_넣는다() {
        var suspension = StockTradingSuspension.start("005930", LocalDate.of(2026, 10, 7), NOW);
        suspension.markExcluded(LocalDate.of(2026, 10, 13), NOW);
        suspension.end(LocalDate.of(2026, 10, 16), NOW);

        assertThat(suspension.isExcludedOn(LocalDate.of(2026, 10, 15))).isTrue();
        assertThat(suspension.isExcludedOn(LocalDate.of(2026, 10, 16))).isFalse();
        assertThat(suspension.isOpen()).isFalse();
    }

    @Test
    void 제외를_시작한_날은_다시_정해도_처음_날짜를_유지한다() {
        var suspension = StockTradingSuspension.start("005930", LocalDate.of(2026, 10, 7), NOW);
        suspension.markExcluded(LocalDate.of(2026, 10, 13), NOW);
        suspension.markExcluded(LocalDate.of(2026, 10, 14), NOW);

        assertThat(suspension.getExcludedFrom()).isEqualTo(LocalDate.of(2026, 10, 13));
    }
}
