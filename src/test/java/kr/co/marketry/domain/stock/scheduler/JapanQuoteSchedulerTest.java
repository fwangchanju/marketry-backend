package kr.co.marketry.domain.stock.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.stock.collector.JapanQuoteCollector;
import org.junit.jupiter.api.Test;

class JapanQuoteSchedulerTest {
    @Test
    void 아침_첫_수집_이전에는_제공처에_요청하지_않는다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector, mock(EscalationPublisher.class));
        for (int minute : new int[] {3, 8, 13, 17}) {
            scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, minute));
        }
        verify(collector, never()).collect();
    }

    @Test
    void 첫_수집부터_이후_주기는_정상_실행한다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        when(collector.collect()).thenReturn(new JapanQuoteCollector.Result(false, null, 0, 0, 0, 0, 0, "새 시세가 없습니다"));
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector, mock(EscalationPublisher.class));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, 18));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, 23));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 10, 3));
        verify(collector, times(3)).collect();
    }

    @Test
    void 점심_중단은_시작을_포함하고_재개_시각은_포함하지_않는다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        when(collector.collect()).thenReturn(new JapanQuoteCollector.Result(false, null, 0, 0, 0, 0, 0, "새 시세가 없습니다"));
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector, mock(EscalationPublisher.class));

        scheduler.collect(LocalDateTime.of(2026, 11, 9, 11, 53));
        verify(collector).collect();
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 11, 58));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 12, 33));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 12, 43));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 12, 47, 59));
        verify(collector).collect();
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 12, 48));

        verify(collector, times(2)).collect();
    }

    private static JapanQuoteCollector.Result tooFew() {
        return new JapanQuoteCollector.Result(false, null, 0, 225, 0, 0, 225, "받은 종목이 너무 적습니다: 0/225");
    }

    @Test
    void 야후_요청이_연속_세_번_실패하면_한_번_알리고_이어지면_한_시간마다_다시_알린다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        EscalationPublisher publisher = mock(EscalationPublisher.class);
        when(collector.collect()).thenReturn(tooFew());
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector, publisher);

        for (int i = 0; i < 2; i++) scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, 18 + i * 5));
        verify(publisher, never()).report(anyString(), any());
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, 28));
        verify(publisher, times(1)).report(anyString(), any());
        for (int i = 0; i < 11; i++)
            scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, 33).plusMinutes(i * 5L));
        verify(publisher, times(1)).report(anyString(), any());
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 10, 33));
        verify(publisher, times(2)).report(anyString(), any());
    }

    @Test
    void 정상_저장이나_새_시세_없음이_끼면_연속_실패를_다시_센다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        EscalationPublisher publisher = mock(EscalationPublisher.class);
        JapanQuoteCollector.Result calm =
                new JapanQuoteCollector.Result(false, null, 225, 225, 0, 225, 0, "새 시세가 없습니다");
        when(collector.collect()).thenReturn(tooFew(), tooFew(), calm, tooFew(), tooFew());
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector, publisher);

        for (int i = 0; i < 5; i++) scheduler.collect(LocalDateTime.of(2026, 11, 9, 9, 18 + i * 5));

        verify(publisher, never()).report(anyString(), any());
    }

    @Test
    void 수집_중_예외도_실패로_세고_날이_바뀌면_다시_센다() {
        JapanQuoteCollector collector = mock(JapanQuoteCollector.class);
        EscalationPublisher publisher = mock(EscalationPublisher.class);
        when(collector.collect()).thenThrow(new IllegalStateException("boom"));
        JapanQuoteScheduler scheduler = new JapanQuoteScheduler(collector, publisher);

        scheduler.collect(LocalDateTime.of(2026, 11, 9, 15, 48));
        scheduler.collect(LocalDateTime.of(2026, 11, 9, 15, 53));
        scheduler.collect(LocalDateTime.of(2026, 11, 10, 9, 18));
        verify(publisher, never()).report(anyString(), any());
        scheduler.collect(LocalDateTime.of(2026, 11, 10, 9, 23));
        scheduler.collect(LocalDateTime.of(2026, 11, 10, 9, 28));
        verify(publisher, times(1)).report(anyString(), any());
    }
}
