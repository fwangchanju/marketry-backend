package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import org.junit.jupiter.api.Test;

class JapanHistoryBackfillServiceTest {
    private static DailyBar bar(String date, String close) {
        BigDecimal price = new BigDecimal(close);
        return new DailyBar(LocalDate.parse(date), price, price, price, price, 1);
    }

    @Test
    void 시가총액은_최신값을_주가_비율로_환산한다() {
        var value = JapanHistoryBackfillService.marketValueOf(
                new BigDecimal("39315789530794"), new BigDecimal("3338"), new BigDecimal("1669"));

        assertThat(value).isEqualByComparingTo("19657894765397");
    }

    @Test
    void 시작일_앞은_전일_종가로만_쓰고_첫_봉은_저장하지_않는다() {
        List<DailyBar> bars = List.of(bar("2019-12-26", "90"), bar("2019-12-27", "100"), bar("2019-12-30", "110"));

        var rows = JapanHistoryBackfillService.toQuotes(
                "7203", bars, new BigDecimal("1100"), new BigDecimal("110"), LocalDate.of(2019, 12, 27));

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().getLastPrice()).isEqualByComparingTo("90");
        assertThat(rows.getFirst().getChangeRate()).isEqualByComparingTo("11.11");
        assertThat(rows.getLast().getTotalMarketValue()).isEqualByComparingTo("1100");
        assertThat(rows.getFirst().getId().getSnapshotTime().toLocalTime().toString())
                .isEqualTo("15:30");
    }

    @Test
    void 상장일에는_전일_종가가_없어_저장하지_않는다() {
        List<DailyBar> bars = List.of(bar("2024-12-18", "100"), bar("2024-12-19", "110"));

        var rows = JapanHistoryBackfillService.toQuotes(
                "285A", bars, new BigDecimal("1100"), new BigDecimal("110"), LocalDate.of(2019, 12, 27));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getId().getSnapshotTime().toLocalDate()).isEqualTo(LocalDate.of(2024, 12, 19));
    }

    @Test
    void 시가총액_날짜의_종가를_찾는다() {
        List<DailyBar> bars = List.of(bar("2026-11-05", "100"), bar("2026-11-06", "110"));

        assertThat(JapanHistoryBackfillService.closeOn(bars, LocalDate.of(2026, 11, 8)))
                .isEqualByComparingTo("110");
        assertThat(JapanHistoryBackfillService.closeOn(bars, LocalDate.of(2026, 11, 5)))
                .isEqualByComparingTo("100");
    }
}
