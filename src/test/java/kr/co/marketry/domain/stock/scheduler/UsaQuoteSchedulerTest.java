package kr.co.marketry.domain.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.stock.collector.UsaCloseMeasurer;
import kr.co.marketry.domain.stock.collector.UsaQuoteCollector;
import kr.co.marketry.domain.stock.collector.UsaQuoteCollector.Result;
import kr.co.marketry.domain.stock.collector.UsaQuoteCollector.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsaQuoteSchedulerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private UsaQuoteCollector collector;
    private UsaCloseMeasurer measurer;
    private EscalationPublisher publisher;

    @BeforeEach
    void setUp() {
        collector = mock(UsaQuoteCollector.class);
        measurer = mock(UsaCloseMeasurer.class);
        publisher = mock(EscalationPublisher.class);
    }

    @Test
    void 켜지_않으면_수집하지_않는다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, false, false);

        scheduler.collect(at(6, 30));

        verify(collector, never()).collect();
    }

    @Test
    void 켜지_않으면_측정하지_않는다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, true, false);

        scheduler.measureClose();

        verify(measurer, never()).measure();
    }

    @Test
    void 마지막_수집_시각_08시_50분이_지난_회차는_건너뛴다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, true, false);
        when(collector.collect()).thenReturn(result(Status.ALREADY_SAVED, "이미 저장했습니다"));

        scheduler.collect(at(8, 50));
        scheduler.collect(at(8, 55));

        verify(collector, times(1)).collect();
    }

    @Test
    void 측정기가_도는_동안에는_수집_회차를_기다리지_않고_건너뛴다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, true, true);
        when(measurer.measure()).thenAnswer(invocation -> {
            scheduler.collect(at(6, 35));
            return new UsaCloseMeasurer.Measurement(DAY, null, null, null, 0, 0, 0, null, null, "건너뜀");
        });

        scheduler.measureClose();

        verify(collector, never()).collect();
        // 측정이 끝나면 다음 회차는 수집한다.
        when(collector.collect()).thenReturn(result(Status.ALREADY_SAVED, "이미 저장했습니다"));
        scheduler.collect(at(6, 40));
        verify(collector, times(1)).collect();
    }

    @Test
    void 개장_전_07시_5분_회차까지_저장되지_않았으면_하루에_한_번만_알린다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, true, false);
        when(collector.collect()).thenReturn(result(Status.WAITING, "마감 후 대기 중입니다"));

        scheduler.collect(at(7, 0));
        verify(publisher, never()).report(any(), any());
        scheduler.collect(at(7, 5));
        scheduler.collect(at(7, 10));

        ArgumentCaptor<EscalateException> captor = ArgumentCaptor.forClass(EscalateException.class);
        verify(publisher, times(1)).report(eq("미국"), captor.capture());
        assertThat(captor.getValue().getErrorCode()).isEqualTo(ErrorCode.USA_QUOTE_MISSING);
    }

    @Test
    void 이미_저장했다는_응답은_저장_완료나_휴장일이라_알리지_않는다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, true, false);
        when(collector.collect()).thenReturn(result(Status.ALREADY_SAVED, "이미 저장했습니다"));

        scheduler.collect(at(7, 5));
        scheduler.collect(at(8, 0));

        verify(publisher, never()).report(any(), any());
    }

    @Test
    void 저장에_성공하면_알리지_않는다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, true, false);
        when(collector.collect()).thenReturn(result(Status.SAVED, null));

        scheduler.collect(at(7, 5));

        verify(publisher, never()).report(any(), any());
    }

    @Test
    void 야후_호출이_실패해도_07시_5분_이후에는_한_번_알린다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, true, false);
        when(collector.collect()).thenThrow(new IllegalStateException("야후 응답에 장 시간이 없습니다"));

        scheduler.collect(at(7, 5));
        scheduler.collect(at(7, 10));

        ArgumentCaptor<EscalateException> captor = ArgumentCaptor.forClass(EscalateException.class);
        verify(publisher, times(1)).report(eq("미국"), captor.capture());
        assertThat(captor.getValue().getErrorCode()).isEqualTo(ErrorCode.USA_QUOTE_MISSING);
    }

    @Test
    void 받은_종목이_적어_연속_세_번_실패하면_수집_실패로_알리고_대기_회차는_세지_않는다() {
        UsaQuoteScheduler scheduler = new UsaQuoteScheduler(collector, measurer, publisher, true, false);
        when(collector.collect())
                .thenReturn(result(Status.WAITING, "마감 후 대기 중입니다"))
                .thenReturn(result(Status.TOO_FEW, "받은 종목이 너무 적습니다: 0/500"));

        scheduler.collect(at(6, 25));
        scheduler.collect(at(6, 30));
        scheduler.collect(at(6, 35));
        scheduler.collect(at(6, 40));

        ArgumentCaptor<EscalateException> captor = ArgumentCaptor.forClass(EscalateException.class);
        verify(publisher, times(1)).report(eq("미국"), captor.capture());
        assertThat(captor.getValue().getErrorCode()).isEqualTo(ErrorCode.USA_QUOTE_COLLECT_FAILED);
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 13, hour, minute);
    }

    private static Result result(Status status, String reason) {
        return new Result(status, DAY, null, 0, 0, 0, reason);
    }
}
