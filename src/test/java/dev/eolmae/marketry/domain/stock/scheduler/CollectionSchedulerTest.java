package dev.eolmae.marketry.domain.stock.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketry.domain.notification.schedule.TelegramSendSchedule;
import dev.eolmae.marketry.domain.notification.service.TelegramCollectionFailureNotifier;
import dev.eolmae.marketry.domain.notification.service.TelegramReportDispatcher;
import dev.eolmae.marketry.domain.stock.collector.MarketSnapshotCollector;
import dev.eolmae.marketry.domain.stock.collector.StockInfoCollector;
import dev.eolmae.marketry.domain.stock.service.CalendarDayTimes;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
    private final CollectionScheduler scheduler = new CollectionScheduler(
            snapshotCollector,
            stockInfoCollector,
            dispatcher,
            failureNotifier,
            schedule,
            escalationPublisher,
            timeService);

    @BeforeEach
    void 정상_시간표() {
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
    void 지연개장일은_10시전_수집과_앞두_지도발송을_생략한다() {
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
    void 마지막_20시10분_섹터발송은_실제_당일_수집시각을_사용한다() {
        when(schedule.due(DATE.atTime(20, 10), false)).thenReturn(true);

        scheduler.collectMarketData(DATE.atTime(19, 55));
        scheduler.collectMarketData(DATE.atTime(20, 10));

        verify(dispatcher).sendSector(DATE.atTime(19, 55));
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
        scheduler.collectMarketData(DATE.atTime(20, 10));

        verify(failureNotifier).notify(DATE.atTime(20, 0));
        verify(dispatcher, never()).sendSector(any());
    }

    @Test
    void 전날_실패결과가_오늘_성공발송을_막지_않는다() {
        doThrow(new RuntimeException("collection failed"))
                .when(snapshotCollector)
                .collect(DATE.minusDays(1).atTime(20, 0));
        when(schedule.due(DATE.atTime(8, 10), true)).thenReturn(true);
        scheduler.collectMarketData(DATE.minusDays(1).atTime(20, 0));

        scheduler.collectMarketData(DATE.atTime(8, 10));

        verify(dispatcher).sendSector(DATE.atTime(8, 10));
        verifyNoInteractions(failureNotifier);
    }

    @Test
    void 수집실패여도_지도발송_정책은_유지한다() {
        LocalDateTime tick = DATE.atTime(8, 15);
        doThrow(new RuntimeException("collection failed"))
                .when(snapshotCollector)
                .collect(tick);
        when(schedule.dueForMap(tick, true, DATE.atTime(15, 30))).thenReturn(true);

        scheduler.collectMarketData(tick);

        verify(dispatcher).sendMap(tick);
    }

    @Test
    void 지도는_정규종료_tick의_수집이_끝난_뒤_발송한다() {
        LocalDateTime tick = DATE.atTime(16, 35);
        when(timeService.resolveForCollection(DATE))
                .thenReturn(new CalendarDayTimes(
                        false,
                        DATE.atTime(10, 0),
                        DATE.atTime(20, 0),
                        DATE.atTime(16, 32),
                        DATE.atTime(16, 32),
                        DATE.atTime(16, 42)));
        when(schedule.dueForMap(tick, true, DATE.atTime(16, 32))).thenReturn(true);

        scheduler.collectMarketData(tick);

        InOrder order = inOrder(snapshotCollector, dispatcher);
        order.verify(snapshotCollector).collect(tick);
        order.verify(dispatcher).sendMap(tick);
    }

    @Test
    void 정규종료_이후_첫tick이_수집불가여도_오늘_마지막_지도를_보낸다() {
        when(timeService.resolveForCollection(DATE))
                .thenReturn(new CalendarDayTimes(
                        false,
                        DATE.atTime(10, 0),
                        DATE.atTime(16, 32),
                        DATE.atTime(16, 32),
                        DATE.atTime(15, 30),
                        DATE.atTime(15, 40)));
        when(schedule.dueForMap(DATE.atTime(16, 35), false, DATE.atTime(16, 32)))
                .thenReturn(true);
        scheduler.collectMarketData(DATE.atTime(16, 30));

        scheduler.collectMarketData(DATE.atTime(16, 35));

        verify(dispatcher).sendMap(DATE.atTime(16, 30));
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
        verifyNoInteractions(timeService);
    }
}
