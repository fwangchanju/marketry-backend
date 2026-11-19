package kr.co.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.stock.entity.OverseasStockDaily;
import kr.co.marketry.domain.stock.entity.StockDailyPrice;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import kr.co.marketry.domain.stock.repository.OverseasStockDailyRepository;
import kr.co.marketry.domain.stock.repository.StockDailyPriceRepository;
import kr.co.marketry.domain.view.dto.StockDailyCandle;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StockChartQueryServiceTest {

    private final StockDailyPriceRepository repository = mock(StockDailyPriceRepository.class);
    private final OverseasStockDailyRepository overseasRepository = mock(OverseasStockDailyRepository.class);
    private final StockChartQueryService service = new StockChartQueryService(repository, overseasRepository);

    private static StockDailyPrice price(String date, String open, String high, String low, String close, long volume) {
        return StockDailyPrice.create(
                "005930",
                ExchangeType.KRX,
                LocalDate.parse(date),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                volume,
                LocalDateTime.of(2026, 11, 19, 20, 0));
    }

    @Test
    void 일봉을_날짜_순서_그대로_캔들로_바꾼다() {
        when(repository.findByStockCodeAndExchangeTypeAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                        eq("005930"), eq(ExchangeType.KRX), any()))
                .thenReturn(List.of(
                        price("2026-11-17", "190000", "195000", "189000", "194000", 1000),
                        price("2026-11-18", "194000", "198000", "193000", "197000", 2000)));

        List<StockDailyCandle> candles = service.getDailyCandles("005930", 30);

        assertThat(candles)
                .extracting(StockDailyCandle::date)
                .containsExactly(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18));
        assertThat(candles.get(1).close()).isEqualByComparingTo("197000");
        assertThat(candles.get(1).volume()).isEqualTo(2000);
    }

    @Test
    void 거래정지일처럼_종가가_0인_일봉은_뺀다() {
        when(repository.findByStockCodeAndExchangeTypeAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                        eq("005930"), eq(ExchangeType.KRX), any()))
                .thenReturn(List.of(
                        price("2026-11-17", "0", "0", "0", "0", 0),
                        price("2026-11-18", "194000", "198000", "193000", "197000", 2000)));

        assertThat(service.getDailyCandles("005930", 30)).hasSize(1);
    }

    @Test
    void 조회_기간은_요청한_일수만큼_오늘에서_거슬러_올라가고_최대치를_넘지_않는다() {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        ArgumentCaptor<LocalDate> startDate = ArgumentCaptor.forClass(LocalDate.class);

        service.getDailyCandles("005930", 30);
        service.getDailyCandles("005930", 100_000);

        verify(repository, org.mockito.Mockito.times(2))
                .findByStockCodeAndExchangeTypeAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                        eq("005930"), eq(ExchangeType.KRX), startDate.capture());
        assertThat(startDate.getAllValues().get(0)).isEqualTo(today.minusDays(30));
        assertThat(startDate.getAllValues().get(1)).isEqualTo(today.minusDays(StockChartQueryService.MAX_DAYS));
    }

    @Test
    void 일본_일봉은_일본_시장_코드로_조회해_날짜_순서_그대로_캔들로_바꾼다() {
        when(overseasRepository.findByIdMarketAndIdStockCodeAndIdTradeDateGreaterThanEqualOrderByIdTradeDateAsc(
                        eq("JAPAN"), eq("7203"), any()))
                .thenReturn(List.of(OverseasStockDaily.create(
                        "JAPAN",
                        "7203",
                        LocalDate.of(2026, 11, 18),
                        new BigDecimal("3400"),
                        new BigDecimal("3437"),
                        new BigDecimal("3352"),
                        new BigDecimal("3378"),
                        19040000,
                        LocalDateTime.of(2026, 11, 19, 20, 0))));

        List<StockDailyCandle> candles = service.getJapanDailyCandles("7203", 30);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().date()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(candles.getFirst().high()).isEqualByComparingTo("3437");
        assertThat(candles.getFirst().volume()).isEqualTo(19040000);
    }

    @Test
    void 미국_일봉은_미국_시장_코드로_조회한다() {
        when(overseasRepository.findByIdMarketAndIdStockCodeAndIdTradeDateGreaterThanEqualOrderByIdTradeDateAsc(
                        eq("USA"), eq("AAPL"), any()))
                .thenReturn(List.of(OverseasStockDaily.create(
                        "USA",
                        "AAPL",
                        LocalDate.of(2026, 11, 18),
                        new BigDecimal("351.94"),
                        new BigDecimal("356.42"),
                        new BigDecimal("351.47"),
                        new BigDecimal("352.45"),
                        34210000,
                        LocalDateTime.of(2026, 11, 19, 20, 0))));

        List<StockDailyCandle> candles = service.getUsaDailyCandles("AAPL", 30);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().close()).isEqualByComparingTo("352.45");
        verify(overseasRepository, org.mockito.Mockito.never())
                .findByIdMarketAndIdStockCodeAndIdTradeDateGreaterThanEqualOrderByIdTradeDateAsc(
                        eq("JAPAN"), any(), any());
    }
}
