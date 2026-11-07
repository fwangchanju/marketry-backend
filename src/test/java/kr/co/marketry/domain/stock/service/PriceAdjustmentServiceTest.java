package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.stock.repository.StockDailyPriceRepository;
import org.junit.jupiter.api.Test;

class PriceAdjustmentServiceTest {
    private final StockDailyPriceRepository repository = mock(StockDailyPriceRepository.class);
    private final PriceAdjustmentService service = new PriceAdjustmentService(repository);

    private static StockDailyPriceRepository.PriceAdjustmentRow row(String code, String date, String factor) {
        return new StockDailyPriceRepository.PriceAdjustmentRow() {
            @Override
            public String getStockCode() {
                return code;
            }

            @Override
            public LocalDate getTradeDate() {
                return LocalDate.parse(date);
            }

            @Override
            public BigDecimal getFactor() {
                return new BigDecimal(factor);
            }
        };
    }

    private void given(StockDailyPriceRepository.PriceAdjustmentRow... rows) {
        when(repository.findLatestTradeDateBefore(any(), any())).thenReturn(Optional.of(LocalDate.of(2026, 11, 6)));
        when(repository.findPriceAdjustments()).thenReturn(List.of(rows));
    }

    @Test
    void 기간_안의_비율만_곱한다() {
        given(row("A", "2021-01-05", "0.02"), row("A", "2022-01-05", "0.5"), row("A", "2023-01-05", "0.1"));

        var factors = service.factorsBetween(LocalDate.of(2021, 1, 4), LocalDate.of(2022, 1, 5));

        assertThat(factors.get("A")).isEqualByComparingTo("0.01");
    }

    @Test
    void 기준일_당일의_변경은_이미_기준가에_반영돼_곱하지_않는다() {
        given(row("A", "2021-01-05", "0.02"));

        assertThat(service.factorsBetween(LocalDate.of(2021, 1, 5), LocalDate.of(2022, 1, 5)))
                .isEmpty();
    }

    @Test
    void 일봉_최신일이_같으면_다시_읽지_않는다() {
        given(row("A", "2021-01-05", "0.02"));

        service.factorsBetween(LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 5));
        service.factorsBetween(LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 5));

        verify(repository, times(1)).findPriceAdjustments();
    }
}
