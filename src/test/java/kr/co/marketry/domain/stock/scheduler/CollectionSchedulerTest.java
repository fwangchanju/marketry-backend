package kr.co.marketry.domain.stock.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.notification.schedule.TelegramSendSchedule;
import kr.co.marketry.domain.notification.service.TelegramCollectionFailureNotifier;
import kr.co.marketry.domain.notification.service.TelegramReportDispatcher;
import kr.co.marketry.domain.stock.collector.MarketSnapshotCollector;
import kr.co.marketry.domain.stock.collector.StockInfoCollector;
import kr.co.marketry.domain.stock.collector.StockSuspensionCollector;
import kr.co.marketry.domain.stock.service.CalendarDayTimes;
import kr.co.marketry.domain.stock.service.MarketCalendarTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CollectionSchedulerTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 2);
    private final MarketSnapshotCollector snapshotCollector = Mockito.mock(MarketSnapshotCollector.class);
    private final TelegramReportDispatcher dispatcher = Mockito.mock(TelegramReportDispatcher.class);
    private final TelegramCollectionFailureNotifier failureNotifier =
            Mockito.mock(TelegramCollectionFailureNotifier.class);
    private final TelegramSendSchedule schedule = Mockito.mock(TelegramSendSchedule.class);
    private final MarketCalendarTimeService timeService = Mockito.mock(MarketCalendarTimeService.class);
    private final EscalationPublisher escalationPublisher = Mockito.mock(EscalationPublisher.class);
    private final StockInfoCollector stockInfoCollector = Mockito.mock(StockInfoCollector.class);
    private final StockSuspensionCollector stockSuspensionCollector = Mockito.mock(StockSuspensionCollector.class);
    private final CollectionScheduler scheduler = new CollectionScheduler(
            snapshotCollector,
            stockInfoCollector,
            stockSuspensionCollector,
            dispatcher,
            failureNotifier,
            schedule,
            escalationPublisher,
            timeService);

    @BeforeEach
    void 정상_시간표() {
        when(schedule.sendEnabled()).thenReturn(true);
        when(timeService.resolveForCollection(any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            return new CalendarDayTimes(
                    false,
                    date.atTime(8, 0),
                    date.atTime(20, 0),
                    date.atTime(15, 30),
                    date.atTime(15, 30),
                    date.atTime(15, 40));
        });
    }

    @Test
    void 시작과_종료_정각은_수집하고_경계밖은_수집하지_않는다() {
        scheduler.collectMarketData(DATE.atTime(7, 55));
        scheduler.collectMarketData(DATE.atTime(8, 0));
        scheduler.collectMarketData(DATE.atTime(20, 0));
        scheduler.collectMarketData(DATE.atTime(20, 5));

        verify(snapshotCollector, never()).collect(DATE.atTime(7, 55));
        verify(snapshotCollector).collect(DATE.atTime(8, 0));
        verify(snapshotCollector).collect(DATE.atTime(20, 0));
        verify(snapshotCollector, never()).collect(DATE.atTime(20, 5));
    }

    @Test
    void 지연개장일은_10시전_수집과_발송을_생략한다() {
        when(timeService.resolveForCollection(DATE))
                .thenReturn(new CalendarDayTimes(
                        false,
                        DATE.atTime(10, 0),
                        DATE.atTime(20, 0),
                        DATE.atTime(16, 30),
                        DATE.atTime(16, 30),
                        DATE.atTime(16, 40)));

        scheduler.collectMarketData(DATE.atTime(8, 15));
        scheduler.collectMarketData(DATE.atTime(9, 15));

        verifyNoInteractions(snapshotCollector, dispatcher);
        scheduler.collectMarketData(DATE.atTime(10, 0));
        verify(snapshotCollector).collect(DATE.atTime(10, 0));
    }

    @Test
    void 휴장일에는_수집과_모든_리포트를_생략한다() {
        when(timeService.resolveForCollection(DATE))
                .thenReturn(new CalendarDayTimes(true, null, null, null, null, null));

        scheduler.collectMarketData(DATE.atTime(8, 15));
        scheduler.collectMarketData(DATE.atTime(20, 10));

        verifyNoInteractions(snapshotCollector, dispatcher, failureNotifier, schedule);
    }

    @Test
    void 마지막_20시10분_홈발송은_실제_당일_수집시각을_사용한다() {
        when(schedule.due(DATE.atTime(20, 10), false)).thenReturn(true);

        scheduler.collectMarketData(DATE.atTime(19, 55));
        scheduler.collectMarketData(DATE.atTime(20, 10));

        verify(dispatcher).sendHome(DATE.atTime(19, 55), true);
        verify(snapshotCollector, never()).collect(DATE.atTime(20, 10));
    }

    @Test
    void 당일_수집없이_20시10분만_호출되면_전날_데이터를_보내지_않는다() {
        when(schedule.due(any(), anyBoolean())).thenReturn(true);
        scheduler.collectMarketData(DATE.minusDays(1).atTime(20, 0));
        Mockito.clearInvocations(dispatcher, failureNotifier);

        scheduler.collectMarketData(DATE.atTime(20, 10));

        verifyNoInteractions(dispatcher, failureNotifier);
    }

    @Test
    void 마지막_수집실패는_20시10분에_당일_실패알림을_보낸다() {
        doThrow(new RuntimeException("collection failed"))
                .when(snapshotCollector)
                .collect(DATE.atTime(20, 0));
        when(schedule.due(DATE.atTime(20, 10), false)).thenReturn(true);

        scheduler.collectMarketData(DATE.atTime(20, 0));
        assertEquals(DATE.atTime(20, 0), scheduler.lastCollectionStatus().snapshotTime());
        assertFalse(scheduler.lastCollectionStatus().success());
        assertEquals("RuntimeException", scheduler.lastCollectionStatus().failureCode());
        scheduler.collectMarketData(DATE.atTime(20, 10));

        verify(failureNotifier).notify(DATE.atTime(20, 0));
        verify(dispatcher, never()).sendHome(any(), anyBoolean());
    }

    @Test
    void 다음_수집이_성공하면_진단실패코드를_지운다() {
        doThrow(new RuntimeException("collection failed"))
                .when(snapshotCollector)
                .collect(DATE.atTime(8, 0));

        scheduler.collectMarketData(DATE.atTime(8, 0));
        scheduler.collectMarketData(DATE.atTime(8, 5));

        assertTrue(scheduler.lastCollectionStatus().success());
        assertEquals(DATE.atTime(8, 5), scheduler.lastCollectionStatus().snapshotTime());
        assertEquals(null, scheduler.lastCollectionStatus().failureCode());
    }

    @Test
    void 전날_실패결과가_오늘_성공발송을_막지_않는다() {
        doThrow(new RuntimeException("collection failed"))
                .when(snapshotCollector)
                .collect(DATE.minusDays(1).atTime(20, 0));
        when(schedule.due(DATE.atTime(8, 10), true)).thenReturn(true);
        scheduler.collectMarketData(DATE.minusDays(1).atTime(20, 0));

        scheduler.collectMarketData(DATE.atTime(8, 10));

        verify(dispatcher).sendHome(DATE.atTime(8, 10), false);
        verifyNoInteractions(failureNotifier);
    }

    @Test
    void 발송_스위치가_꺼져_있으면_홈을_보내지_않는다() {
        LocalDateTime tick = DATE.atTime(8, 15);
        when(schedule.sendEnabled()).thenReturn(false);
        when(schedule.due(tick, true)).thenReturn(true);

        scheduler.collectMarketData(tick);

        verifyNoInteractions(dispatcher);
    }

    @Test
    void 발송_스위치가_꺼져_있어도_수집실패_알림은_보낸다() {
        doThrow(new RuntimeException("collection failed"))
                .when(snapshotCollector)
                .collect(DATE.atTime(20, 0));
        when(schedule.sendEnabled()).thenReturn(false);
        when(schedule.due(DATE.atTime(20, 10), false)).thenReturn(true);

        scheduler.collectMarketData(DATE.atTime(20, 0));
        scheduler.collectMarketData(DATE.atTime(20, 10));

        verify(failureNotifier).notify(DATE.atTime(20, 0));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void 수집중_휴장_시간표가_적재되면_다음_tick부터_수집을_생략한다() {
        when(timeService.resolveForCollection(DATE))
                .thenReturn(
                        new CalendarDayTimes(
                                false,
                                DATE.atTime(8, 0),
                                DATE.atTime(20, 0),
                                DATE.atTime(15, 30),
                                DATE.atTime(15, 30),
                                DATE.atTime(15, 40)),
                        new CalendarDayTimes(true, null, null, null, null, null));

        scheduler.collectMarketData(DATE.atTime(8, 0));
        scheduler.collectMarketData(DATE.atTime(8, 5));

        verify(snapshotCollector).collect(DATE.atTime(8, 0));
        verify(snapshotCollector, never()).collect(DATE.atTime(8, 5));
    }

    @Test
    void 평일_07시_종목동기화는_유지한다() {
        scheduler.syncStockInfo();

        verify(stockInfoCollector).sync();
        verify(stockSuspensionCollector).sync();
        verifyNoInteractions(timeService);
    }
}
