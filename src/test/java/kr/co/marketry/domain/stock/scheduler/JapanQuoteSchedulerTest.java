package kr.co.marketry.domain.stock.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import kr.co.marketry.domain.stock.collector.JapanQuoteCollector;
import org.junit.jupiter.api.Test;

class JapanQuoteSchedulerTest {
    @Test
    void 아침_첫_수집_이전에는_제공처에_요청하지_않는다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector);
        for (int minute : new int[] {3, 8, 13, 17}) {
            scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, minute));
        }
        verify(collector, never()).collect();
    }

    @Test
    void 첫_수집부터_이후_주기는_정상_실행한다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        when(collector.collect()).thenReturn(new JapanQuoteCollector.Result(false, null, 0, 0, 0, 0, 0, "새 시세가 없습니다"));
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector);
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, 18));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, 23));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 10, 3));
        verify(collector, times(3)).collect();
    }

    @Test
    void 개장_시세_측정은_수집기를_호출한다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 9, 15);
        when(collector.measureOpeningFreshness())
                .thenReturn(new JapanQuoteCollector.OpeningMeasurement(now, now, 203, 225, 225, now, now, null));
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector);

        scheduler.measureOpeningFreshness();

        verify(collector).measureOpeningFreshness();
    }
}
