package dev.eolmae.marketry.domain.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.event.DailyPriceCollectionFailedEvent;
import dev.eolmae.marketry.domain.stock.collector.StockDailyPriceCollector;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class StockDailyPriceSchedulerTest {
    private final StockDailyPriceCollector collector = mock(StockDailyPriceCollector.class);
    private final List<Object> events = new CopyOnWriteArrayList<>();
    private final StockDailyPriceScheduler scheduler =
            new StockDailyPriceScheduler(collector, events::add, LocalTime.of(5, 50), Clock.system(Zone.KST.zoneId()));

    @Test
    void 토요일_실행은_금요일을_일요일_실행은_토요일을_대상으로_하고_05시50분을_기한으로_넘긴다() {
        when(collector.collect(any(), anyList(), any()))
                .thenReturn(new StockDailyPriceCollector.Result(
                        StockDailyPriceCollector.Status.COMPLETE, 1, 1, 1, List.of()));
        Instant deadline = LocalDate.of(2026, 10, 10)
                .atTime(5, 50)
                .atZone(Zone.KST.zoneId())
                .toInstant();

        scheduler.collect(LocalDate.of(2026, 10, 10));
        scheduler.collect(LocalDate.of(2026, 10, 11));

        verify(collector).collect(eq(LocalDate.of(2026, 10, 9)), eq(List.of()), eq(deadline));
        verify(collector)
                .collect(eq(LocalDate.of(2026, 10, 10)), eq(List.of()), eq(deadline.plusSeconds(24 * 60 * 60)));
        assertThat(events).isEmpty();
    }

    @Test
    void 세회차_뒤_남은_실패가_있으면_종목목록이_담긴_최종_이벤트를_한번_발행한다() {
        when(collector.collect(any(), anyList(), any()))
                .thenReturn(new StockDailyPriceCollector.Result(
                        StockDailyPriceCollector.Status.INCOMPLETE,
                        3,
                        10,
                        0,
                        List.of(
                                new StockDailyPriceCollector.FailedStock(
                                        "005930", "삼성전자", List.of(ExchangeType.KRX, ExchangeType.NXT)),
                                new StockDailyPriceCollector.FailedStock(
                                        "000660", "SK하이닉스", List.of(ExchangeType.NXT)))));

        scheduler.collect(LocalDate.of(2026, 10, 9));

        assertThat(events).hasSize(1);
        var event = (DailyPriceCollectionFailedEvent) events.getFirst();
        assertThat(event.targetDate()).isEqualTo("2026-10-08");
        assertThat(event.reason()).isEqualTo(StockDailyPriceScheduler.INCOMPLETE_REASON);
        assertThat(event.failedStocks()).containsExactly("005930(삼성전자) [KRX, NXT]", "000660(SK하이닉스) [NXT]");
    }

    @Test
    void 제한시간_초과는_종목목록없이_시간초과_문구만_알린다() {
        when(collector.collect(any(), anyList(), any()))
                .thenReturn(new StockDailyPriceCollector.Result(
                        StockDailyPriceCollector.Status.DEADLINE, 2, 100, 50, List.of()));

        scheduler.collect(LocalDate.of(2026, 10, 9));

        assertThat(events).hasSize(1);
        var event = (DailyPriceCollectionFailedEvent) events.getFirst();
        assertThat(event.reason()).isEqualTo("일봉 수집을 제한 시간 내에 완료하지 못했습니다");
        assertThat(event.failedStocks()).isEmpty();
    }

    @Test
    void 완료_휴장_중단에서는_알리지_않는다() {
        for (var status : List.of(
                StockDailyPriceCollector.Status.COMPLETE,
                StockDailyPriceCollector.Status.HOLIDAY,
                StockDailyPriceCollector.Status.INTERRUPTED)) {
            when(collector.collect(any(), anyList(), any()))
                    .thenReturn(new StockDailyPriceCollector.Result(status, 1, 1, 0, List.of()));
            scheduler.collect(LocalDate.of(2026, 10, 9));
        }

        assertThat(events).isEmpty();
    }

    @Test
    void 수집기가_예외로_끝나면_종류만_담은_이벤트를_발행한다() {
        when(collector.collect(any(), anyList(), any())).thenThrow(new IllegalStateException("private-detail"));

        scheduler.collect(LocalDate.of(2026, 10, 9));

        assertThat(events).hasSize(1);
        var event = (DailyPriceCollectionFailedEvent) events.getFirst();
        assertThat(event.reason()).isEqualTo("수집 실행 실패: IllegalStateException");
        assertThat(event.failedStocks()).isEmpty();
    }
}
