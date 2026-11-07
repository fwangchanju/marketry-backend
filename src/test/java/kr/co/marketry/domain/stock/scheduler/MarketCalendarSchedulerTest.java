package kr.co.marketry.domain.stock.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import kr.co.marketry.domain.stock.collector.MarketCalendarCollector;
import org.junit.jupiter.api.Test;

class MarketCalendarSchedulerTest {
    private final MarketCalendarCollector collector = mock(MarketCalendarCollector.class);
    private final MarketCalendarScheduler scheduler = new MarketCalendarScheduler(collector);
    private final LocalDate date = LocalDate.of(2026, 10, 9);

    @Test
    void 실패후재시도성공하면다음재시도는생략한다() {
        when(collector.collect(date)).thenReturn(false, true);
        scheduler.refresh(date);
        scheduler.retry(date);
        scheduler.retry(date);
        verify(collector, times(2)).collect(date);
    }

    @Test
    void 전날실패는다음날재시도하지않는다() {
        scheduler.refresh(date);
        scheduler.retry(date.plusDays(1));
        verify(collector, never()).collect(date.plusDays(1));
    }

    @Test
    void 생성후_정기조회전에는_재시도해도_API를_호출하지_않는다() {
        scheduler.retry(date);
        verify(collector, never()).collect(date);
    }

    @Test
    void 정기조회가_실패하면_정해진_두차례_재시도한다() {
        scheduler.refresh(date);
        scheduler.retry(date);
        scheduler.retry(date);
        verify(collector, times(3)).collect(date);
    }
}
