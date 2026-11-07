package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MarketCalendarManualOptionsTest {
    private final LocalDate today = LocalDate.of(2026, 10, 10);

    @ParameterizedTest
    @CsvSource({
        "202601,2026-01-01,2026-01-31,31",
        "202604,2026-04-01,2026-04-30,30",
        "202502,2025-02-01,2025-02-28,28",
        "202402,2024-02-01,2024-02-29,29",
        "202512,2025-12-01,2025-12-31,31"
    })
    void 월의첫날부터마지막날까지휴장일도포함한다(String month, LocalDate start, LocalDate end, int days) {
        var options = MarketCalendarManualOptions.resolve(month, "", "", today);

        assertThat(options.startDate()).isEqualTo(start);
        assertThat(options.endDate()).isEqualTo(end);
        assertThat(options.dates())
                .hasSize(days)
                .containsExactlyElementsOf(start.datesUntil(end.plusDays(1)).toList());
    }

    @Test
    void 현재월은오늘까지만수집한다() {
        var options = MarketCalendarManualOptions.resolve("202610", "", "", today);

        assertThat(options.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(options.endDate()).isEqualTo(today);
        assertThat(options.dates()).hasSize(10);
    }

    @Test
    void 미래월은거부한다() {
        assertThatThrownBy(() -> MarketCalendarManualOptions.resolve("202611", "", "", today))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("미래 월");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01", "20261", "20260101", "202600", "202613", "abcdef", "000001"})
    void 월의형식과유효성을검증한다(String month) {
        assertThatThrownBy(() -> MarketCalendarManualOptions.resolve(month, "", "", today))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("calendar.month");
    }

    @Test
    void 월과날짜범위를같이지정하면거부한다() {
        assertThatThrownBy(() -> MarketCalendarManualOptions.resolve("202601", "2026-01-01", "", today))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("함께 지정");
        assertThatThrownBy(() -> MarketCalendarManualOptions.resolve("202601", "", "2026-01-31", today))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("함께 지정");
    }

    @Test
    void 기존단일날짜입력을유지한다() {
        var date = LocalDate.of(2026, 1, 2);
        var options = MarketCalendarManualOptions.resolve("", date.toString(), "", today);

        assertThat(options.dates()).containsExactly(date);
    }

    @Test
    void 기존날짜범위와today입력을유지한다() {
        var start = LocalDate.of(2026, 10, 8);
        var options = MarketCalendarManualOptions.resolve("", start.toString(), "today", today);

        assertThat(options.dates()).containsExactly(start, start.plusDays(1), today);
    }

    @Test
    void 아무입력도없으면오늘하루다() {
        assertThat(MarketCalendarManualOptions.resolve("", "", "", today).dates())
                .containsExactly(today);
        assertThat(MarketCalendarManualOptions.resolve("", "today", "", today).dates())
                .containsExactly(today);
    }

    @Test
    void 종료일이시작일보다빠르면거부한다() {
        assertThatThrownBy(() -> MarketCalendarManualOptions.resolve("", "2026-01-10", "2026-01-01", today))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("종료일");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-1-2", "20260101", "not-a-date"})
    void 유효하지않은날짜는거부한다(String date) {
        assertThatThrownBy(() -> MarketCalendarManualOptions.resolve("", date, "", today))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("calendar.date");
        assertThatThrownBy(() -> MarketCalendarManualOptions.resolve("", "2026-01-01", date, today))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("calendar.end-date");
    }
}
