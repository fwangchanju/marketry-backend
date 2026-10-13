package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.stock.entity.StockTradingSuspension;
import dev.eolmae.marketry.domain.stock.repository.StockTradingSuspensionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class StockTradingSuspensionServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private final StockTradingSuspensionRepository repository = Mockito.mock(StockTradingSuspensionRepository.class);
    private final MarketCalendarTimeService calendar = Mockito.mock(MarketCalendarTimeService.class);
    private final StockTradingSuspensionService service = new StockTradingSuspensionService(repository, calendar);

    @Test
    void 새로_정지된_종목은_기록을_열고_바로_제외하지_않는다() {
        when(repository.findByEndedOnIsNull()).thenReturn(List.of());

        service.record(Set.of("005930"), TODAY);

        var captor = ArgumentCaptor.forClass(StockTradingSuspension.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStockCode()).isEqualTo("005930");
        assertThat(captor.getValue().getStartedOn()).isEqualTo(TODAY);
        assertThat(captor.getValue().getExcludedFrom()).isNull();
    }

    @Test
    void 연속_5거래일에_이르면_그날부터_제외한다() {
        var open =
                StockTradingSuspension.start("005930", LocalDate.of(2026, 10, 8), LocalDateTime.of(2026, 10, 8, 7, 0));
        when(repository.findByEndedOnIsNull()).thenReturn(List.of(open));
        when(calendar.countTradingDays(LocalDate.of(2026, 10, 7), TODAY)).thenReturn(5L);

        service.record(Set.of("005930"), TODAY);

        assertThat(open.getExcludedFrom()).isEqualTo(TODAY);
    }

    @Test
    void 연속_5거래일에_못_이르면_제외하지_않는다() {
        var open = StockTradingSuspension.start(
                "005930", LocalDate.of(2026, 10, 12), LocalDateTime.of(2026, 10, 12, 7, 0));
        when(repository.findByEndedOnIsNull()).thenReturn(List.of(open));
        when(calendar.countTradingDays(LocalDate.of(2026, 10, 11), TODAY)).thenReturn(3L);

        service.record(Set.of("005930"), TODAY);

        assertThat(open.getExcludedFrom()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void 더는_정지가_아닌_종목은_오늘_기록을_닫는다() {
        var open =
                StockTradingSuspension.start("005930", LocalDate.of(2026, 10, 8), LocalDateTime.of(2026, 10, 8, 7, 0));
        open.markExcluded(LocalDate.of(2026, 10, 13), LocalDateTime.of(2026, 10, 13, 7, 0));
        when(repository.findByEndedOnIsNull()).thenReturn(List.of(open));

        service.record(Set.of(), TODAY);

        assertThat(open.getEndedOn()).isEqualTo(TODAY);
        assertThat(open.isExcludedOn(TODAY)).isFalse();
    }

    @Test
    void 제외할_종목코드는_날짜_기준으로_모은다() {
        var excluded =
                StockTradingSuspension.start("005930", LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 7, 0));
        excluded.markExcluded(LocalDate.of(2026, 10, 7), LocalDateTime.of(2026, 10, 7, 7, 0));
        var resumedBefore =
                StockTradingSuspension.start("000660", LocalDate.of(2026, 9, 1), LocalDateTime.of(2026, 9, 1, 7, 0));
        resumedBefore.markExcluded(LocalDate.of(2026, 9, 8), LocalDateTime.of(2026, 9, 8, 7, 0));
        resumedBefore.end(LocalDate.of(2026, 10, 2), LocalDateTime.of(2026, 10, 2, 7, 0));
        when(repository.findByExcludedFromLessThanEqualAndEndedOnIsNull(TODAY)).thenReturn(List.of(excluded));
        when(repository.findByExcludedFromLessThanEqualAndEndedOnGreaterThan(TODAY, TODAY))
                .thenReturn(List.of(resumedBefore));

        assertThat(service.findExcludedStockCodes(TODAY)).containsExactly("005930");
    }
}
