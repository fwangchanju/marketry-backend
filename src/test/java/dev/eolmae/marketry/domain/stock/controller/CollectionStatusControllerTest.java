package dev.eolmae.marketry.domain.stock.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.collector.MarketCalendarCollector;
import dev.eolmae.marketry.domain.stock.scheduler.CollectionScheduler;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CollectionStatusControllerTest {

    @Test
    void 시간표_실패를_코드로_보여주고_반복조회는_캐시한다() {
        CollectionScheduler scheduler = mock(CollectionScheduler.class);
        MarketCalendarCollector calendarCollector = mock(MarketCalendarCollector.class);
        doThrow(new BadRequestException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED, 401))
                .when(calendarCollector)
                .probe(any(LocalDate.class));
        CollectionStatusController controller = new CollectionStatusController(scheduler, calendarCollector);

        var first = controller.get();
        var second = controller.get();

        assertFalse(first.calendarProbeSuccess());
        assertEquals(first.calendarFailure(), second.calendarFailure());
        assertTrue(first.calendarFailure().contains("TOSS_TOKEN_ISSUE_FAILED"));
        verify(calendarCollector).probe(first.calendarDate());
        verifyNoMoreInteractions(calendarCollector);
    }
}
