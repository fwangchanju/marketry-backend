package kr.co.marketry.domain.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.stock.collector.UsaIntradayCollector;
import kr.co.marketry.domain.stock.collector.UsaIntradayCollector.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsaIntradaySchedulerTest {
    private UsaIntradayCollector collector;
    private EscalationPublisher publisher;

    @BeforeEach
    void setUp() {
        collector = mock(UsaIntradayCollector.class);
        publisher = mock(EscalationPublisher.class);
    }

    private UsaIntradayScheduler scheduler(boolean enabled) {
        return new UsaIntradayScheduler(collector, publisher, enabled);
    }

    private static Result failed() {
        return new Result(false, null, 0, 0, "응답 종목 부족: 0/500", true);
    }

    private static Result skipped() {
        return new Result(false, null, 0, 0, "미국 장 시간이 아닙니다", false);
    }

    private static Result saved() {
        return new Result(true, LocalDateTime.of(2026, 11, 27, 4, 5), 500, 500, null, false);
    }

    @Test
    void 연속_세_번_실패하면_한_번_알리고_네_번째부터는_알리지_않는다() {
        UsaIntradayScheduler scheduler = scheduler(true);
        when(collector.collect()).thenReturn(failed());

        for (int index = 0; index < 4; index++) {
            scheduler.collect();
        }

        ArgumentCaptor<EscalateException> captor = ArgumentCaptor.forClass(EscalateException.class);
        verify(publisher, times(1)).report(eq("미국"), captor.capture());
        assertThat(captor.getValue().getErrorCode()).isEqualTo(ErrorCode.USA_INTRADAY_COLLECT_FAILED);
    }

    @Test
    void 저장이나_정상_건너뜀이_끼면_연속으로_세지_않는다() {
        UsaIntradayScheduler scheduler = scheduler(true);
        when(collector.collect())
                .thenReturn(failed(), failed(), saved(), failed(), failed(), skipped(), failed(), failed());

        for (int index = 0; index < 8; index++) {
            scheduler.collect();
        }

        verify(publisher, never()).report(any(), any());
    }

    @Test
    void 실패가_계속되면_열두_회차마다_다시_알린다() {
        UsaIntradayScheduler scheduler = scheduler(true);
        when(collector.collect()).thenReturn(failed());

        // 3번째 알림, 이후 15번째(3 + 12)에 재알림
        for (int index = 0; index < 15; index++) {
            scheduler.collect();
        }

        verify(publisher, times(2)).report(eq("미국"), any(EscalateException.class));
    }

    @Test
    void 수집이_예외를_던져도_실패로_세고_알림은_예외를_다시_던지지_않는다() {
        UsaIntradayScheduler scheduler = scheduler(true);
        when(collector.collect()).thenThrow(new IllegalStateException("야후 응답 이상"));
        org.mockito.Mockito.doThrow(new IllegalStateException("알림 실패"))
                .when(publisher)
                .report(any(), any());

        for (int index = 0; index < 3; index++) {
            scheduler.collect();
        }

        verify(publisher, times(1)).report(eq("미국"), any(EscalateException.class));
    }

    @Test
    void 수집이_꺼져_있으면_아무것도_하지_않는다() {
        scheduler(false).collect();

        verify(collector, never()).collect();
    }
}
