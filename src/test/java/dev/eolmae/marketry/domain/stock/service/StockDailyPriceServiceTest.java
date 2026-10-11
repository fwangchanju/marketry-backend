package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.stock.dto.DailyClosePrice;
import dev.eolmae.marketry.domain.stock.entity.StockDailyPrice;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.repository.StockDailyPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockDailyPriceServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);
    private final StockDailyPriceRepository repository = mock(StockDailyPriceRepository.class);
    private final StockDailyPriceService service = new StockDailyPriceService(repository);

    @Test
    void 기존가격은_변경하지않고_새날짜만_저장한다() {
        var stored = price(DATE, "100", ExchangeType.KRX);
        var fresh = price(DATE.minusDays(1), "120", ExchangeType.KRX);
        when(repository.findByStockCodeAndExchangeTypeAndTradeDateBetween(
                        "005930", ExchangeType.KRX, DATE.minusDays(1), DATE))
                .thenReturn(List.of(stored));
        var result = service.savePage(List.of(price(DATE, "110", ExchangeType.KRX), fresh));
        assertThat(result).isEqualTo(new StockDailyPriceService.SaveResult(1, 1));
        assertThat(stored.getClosePrice()).isEqualByComparingTo("100");
        verify(repository).saveAll(List.of(fresh));
    }

    @Test
    void 빈페이지는_저장하지않고_중복날짜와SOR은_거부한다() {
        assertThat(service.savePage(List.of())).isEqualTo(new StockDailyPriceService.SaveResult(0, 0));
        assertThatThrownBy(() -> service.savePage(
                        List.of(price(DATE, "100", ExchangeType.KRX), price(DATE, "100", ExchangeType.KRX))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.savePage(List.of(price(DATE, "100", ExchangeType.SOR))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 종가는_KRX_일봉만_읽고_0이하_종가는_가격없음으로_뺀다() {
        when(repository.findByExchangeTypeAndTradeDate(ExchangeType.KRX, DATE))
                .thenReturn(List.of(
                        new DailyClosePrice("005930", new BigDecimal("100")),
                        new DailyClosePrice("000660", BigDecimal.ZERO)));

        assertThat(service.findKrxClosePrices(DATE)).containsOnlyKeys("005930");
        verify(repository, never()).findByExchangeTypeAndTradeDate(ExchangeType.NXT, DATE);
    }

    @Test
    void 일봉_존재_날짜는_캘린더_행이_있는_날짜를_한번에_보고_행이_없는_날짜만_따로_확인한다() {
        LocalDate withRow = DATE;
        LocalDate withoutRow = DATE.plusDays(1);
        LocalDate noPrice = DATE.plusDays(2);
        when(repository.findCalendarDatesWithPrice(ExchangeType.KRX, withRow, noPrice))
                .thenReturn(List.of(withRow));
        when(repository.existsByExchangeTypeAndTradeDate(ExchangeType.KRX, withoutRow))
                .thenReturn(true);

        assertThat(service.findKrxPriceDates(List.of(withRow, withoutRow, noPrice)))
                .containsExactlyInAnyOrder(withRow, withoutRow);
        verify(repository, never()).existsByExchangeTypeAndTradeDate(ExchangeType.KRX, withRow);
    }

    @Test
    void 후보_날짜가_없으면_조회하지_않는다() {
        assertThat(service.findKrxPriceDates(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    private StockDailyPrice price(LocalDate date, String close, ExchangeType exchange) {
        return StockDailyPrice.create(
                "005930",
                exchange,
                date,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                new BigDecimal(close),
                100,
                LocalDateTime.of(2026, 10, 11, 17, 0));
    }
}
