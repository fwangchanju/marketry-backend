package dev.eolmae.marketry.domain.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.event.DailyPriceCollectionFailedEvent;
import dev.eolmae.marketry.domain.stock.collector.StockDailyPriceCollector;
import dev.eolmae.marketry.domain.stock.dto.MissingDailyPriceStock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class StockDailyPriceSchedulerTest {
    @Test
    void 토요일도_금요일을_대상으로_실행한다() {
        var collector = mock(StockDailyPriceCollector.class);
        List<Object> events = new CopyOnWriteArrayList<>();
        var scheduler = new StockDailyPriceScheduler(collector, events::add);
        when(collector.collect(any(), anyList(), any(), any()))
                .thenReturn(new StockDailyPriceCollector.Result(
                        StockDailyPriceCollector.Status.COMPLETE, 1, 1, 1, List.of()));
        var deadline = Instant.now().plusSeconds(60);
        try {
            scheduler.collect(LocalDate.of(2026, 10, 10), deadline);
            verify(collector)
                    .collect(
                            org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 10, 9)),
                            org.mockito.ArgumentMatchers.eq(List.of()),
                            org.mockito.ArgumentMatchers.eq(deadline.minusSeconds(10)),
                            any());
            assertThat(events).isEmpty();
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void 종료기한에는_실행중인_작업을_취소하고_확인필요목록을_이벤트로_발행한다() throws InterruptedException {
        var collector = mock(StockDailyPriceCollector.class);
        List<Object> events = new CopyOnWriteArrayList<>();
        var scheduler = new StockDailyPriceScheduler(collector, events::add);
        var interrupted = new CountDownLatch(1);
        when(collector.collect(any(), anyList(), any(), any())).thenAnswer(call -> {
            Consumer<List<MissingDailyPriceStock>> progress = call.getArgument(3);
            progress.accept(List.of(new MissingDailyPriceStock("005930", "삼성전자", false, true)));
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw e;
            }
            return null;
        });
        try {
            scheduler.collect(LocalDate.of(2026, 10, 10), Instant.now().plusMillis(10250));
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(events).hasSize(1);
            var event = (DailyPriceCollectionFailedEvent) events.getFirst();
            assertThat(event.targetDate()).isEqualTo("2026-10-09");
            assertThat(event.reason()).contains("종료 기한");
            assertThat(event.missingStocks()).containsExactly("005930(삼성전자) [NXT]");
        } finally {
            scheduler.shutdown();
        }
    }
}
