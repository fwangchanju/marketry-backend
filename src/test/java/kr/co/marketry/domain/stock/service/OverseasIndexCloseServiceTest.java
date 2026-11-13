package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import org.junit.jupiter.api.Test;

class OverseasIndexCloseServiceTest {
    private final MarketIndexDailyRepository repository = mock(MarketIndexDailyRepository.class);
    private final OverseasIndexCloseService service = new OverseasIndexCloseService(repository);

    @Test
    void 날짜_순서대로_날짜와_종가만_돌려준다() {
        LocalDate from = LocalDate.of(2026, 11, 5);
        LocalDate to = LocalDate.of(2026, 11, 6);
        when(repository.findByIdMarketAndIdTradeDateBetweenOrderByIdTradeDateAsc("NIKKEI225", from, to))
                .thenReturn(List.of(index("2026-11-05", "49000"), index("2026-11-06", "50000")));

        List<OverseasIndexCloseService.IndexClose> closes = service.findCloses("NIKKEI225", from, to);

        assertThat(closes)
                .extracting(OverseasIndexCloseService.IndexClose::date)
                .containsExactly(from, to);
        assertThat(closes.getLast().close()).isEqualByComparingTo("50000");
    }

    @Test
    void 알_수_없는_지수는_거절하고_조회하지_않는다() {
        assertThatThrownBy(() -> service.findCloses("KOSPI", LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6)))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never())
                .findByIdMarketAndIdTradeDateBetweenOrderByIdTradeDateAsc(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private static MarketIndexDaily index(String date, String close) {
        BigDecimal value = new BigDecimal(close);
        return MarketIndexDaily.create(
                "NIKKEI225",
                LocalDate.parse(date),
                value,
                value,
                value,
                value,
                1,
                null,
                java.time.LocalDateTime.of(2026, 11, 6, 18, 0));
    }
}
