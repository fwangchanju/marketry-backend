package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import kr.co.marketry.common.enums.Country;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.client.TossMarketCalendarClient;
import kr.co.marketry.domain.stock.dto.TossMarketCalendarResponse;
import kr.co.marketry.domain.stock.dto.TossMarketCalendarResponse.Day;
import kr.co.marketry.domain.stock.dto.TossMarketCalendarResponse.Result;
import kr.co.marketry.domain.stock.entity.IntegratedSessions;
import kr.co.marketry.domain.stock.entity.TradingSession;
import kr.co.marketry.domain.stock.enums.MarketCalendarStatus;
import kr.co.marketry.domain.stock.service.MarketCalendarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

class MarketCalendarCollectorTest {
    private final TossMarketCalendarClient client = mock(TossMarketCalendarClient.class);
    private final MarketCalendarService service = mock(MarketCalendarService.class);
    private final MarketCalendarCollector collector = new MarketCalendarCollector(client, service);
    private final LocalDate date = LocalDate.of(2026, 10, 9);

    @ParameterizedTest
    @CsvSource({"2026-01-02,15:30,15:40", "2025-11-13,16:30,16:40"})
    void 프리마켓없는특수일의전체세션을보존한다(LocalDate tradingDate, String close, String auctionEnd) {
        var integrated = new IntegratedSessions(
                null,
                session(tradingDate, "10:00", null, null, close),
                session(tradingDate, close, close, auctionEnd, "20:00"));
        var calendars = collector.validateResponse(
                tradingDate, new TossMarketCalendarResponse(new Result(new Day(tradingDate, integrated), null, null)));
        assertThat(calendars).hasSize(1);
        assertThat(calendars.getFirst().getStatus()).isEqualTo(MarketCalendarStatus.TRADING_DAY);
        assertThat(calendars.getFirst().getIntegrated()).isEqualTo(integrated);
    }

    @Test
    void 오늘객체가없어도유효한양옆날짜가있으면휴장이다() {
        var response = new TossMarketCalendarResponse(
                new Result(null, new Day(date.minusDays(1), null), new Day(date.plusDays(1), null)));
        assertThat(collector.validateResponse(date, response)).hasSize(3);
        assertThat(collector.validateResponse(date, response).getFirst().getStatus())
                .isEqualTo(MarketCalendarStatus.HOLIDAY);
    }

    @Test
    void 전체빈응답은휴장이아니라실패이다() {
        assertThatThrownBy(() ->
                        collector.validateResponse(date, new TossMarketCalendarResponse(new Result(null, null, null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 오늘날짜불일치와중복날짜는실패이다() {
        assertThatThrownBy(() -> collector.validateResponse(
                        date, new TossMarketCalendarResponse(new Result(new Day(date.minusDays(1), null), null, null))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> collector.validateResponse(
                        date,
                        new TossMarketCalendarResponse(new Result(new Day(date, null), new Day(date, null), null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 세션필수시각누락과범위밖단일가시각은실패이다() {
        var missingEnd = new TradingSession(time(date, "09:00"), null, null, null);
        var outside = session(date, "09:00", null, "16:00", "15:30");
        for (TradingSession session : new TradingSession[] {missingEnd, outside}) {
            assertThatThrownBy(() -> collector.validateResponse(
                            date,
                            new TossMarketCalendarResponse(new Result(
                                    new Day(date, new IntegratedSessions(null, session, null)), null, null))))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void 정상응답저장실패는요청일실패만별도저장한다() {
        when(client.fetch(date))
                .thenReturn(new TossMarketCalendarResponse(new Result(new Day(date, null), null, null)));
        doThrow(new RuntimeException("DB failure")).when(service).saveSuccess(any(), any());
        assertThat(collector.collect(date)).isFalse();
        verify(service).saveFailure(Country.KR, date);
    }

    @Test
    void 조회와실패기록모두실패해도수집흐름을중단하지않는다() {
        when(client.fetch(date)).thenThrow(new RuntimeException("remote failure"));
        doThrow(new RuntimeException("DB failure")).when(service).saveFailure(Country.KR, date);
        assertThat(collector.collect(date)).isFalse();
    }

    @Test
    void 안전한오류코드와HTTP상태를실패로그에남긴다() {
        Logger logger = (Logger) LoggerFactory.getLogger(MarketCalendarCollector.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(client.fetch(date)).thenThrow(new BadRequestException(ErrorCode.TOSS_CALENDAR_FETCH_FAILED, 403));
            collector.collect(date);
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("TOSS_CALENDAR_FETCH_FAILED", "403"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void DB오류로그는클래스만남기고메시지와스택은노출하지않는다() {
        Logger logger = (Logger) LoggerFactory.getLogger(MarketCalendarCollector.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(client.fetch(date)).thenThrow(new RuntimeException("secret-data"));
            collector.collect(date);
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message ->
                            assertThat(message).contains("RuntimeException").doesNotContain("secret-data"));
            assertThat(appender.list)
                    .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private TradingSession session(LocalDate day, String start, String auctionStart, String auctionEnd, String end) {
        return new TradingSession(time(day, start), time(day, auctionStart), time(day, auctionEnd), time(day, end));
    }

    private OffsetDateTime time(LocalDate day, String hour) {
        return hour == null ? null : OffsetDateTime.parse(day + "T" + hour + ":00+09:00");
    }
}
