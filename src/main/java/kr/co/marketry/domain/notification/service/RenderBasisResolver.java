package kr.co.marketry.domain.notification.service;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.co.marketry.domain.notification.enums.RenderBasis;
import kr.co.marketry.domain.stock.service.CalendarDayTimes;
import kr.co.marketry.domain.stock.service.MarketCalendarTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 발송 시각에 맞는 등락률 기준을 고른다 — 정규장 마감(종가 구간 시작)부터 애프터 마켓 시작 전까지는 종가, 애프터 마켓 시작부터는 별도, 그 전에는 기준을 주지 않는다(누적).
// 수집·종가 기준가를 정하는 마감 구간 계산(MarketCalendarTimeService)을 그대로 쓴다. 거래소 시간표가 없거나 실패한 날에도 설정의 기본 시각(15:30·15:40)으로 대신한다.
@Component
@RequiredArgsConstructor
public class RenderBasisResolver {

    private final MarketCalendarTimeService marketCalendarTimeService;

    public Optional<RenderBasis> resolve(LocalDateTime time) {
        CalendarDayTimes day = marketCalendarTimeService.resolve(time.toLocalDate());
        if (day.holiday()) {
            return Optional.empty();
        }
        if (time.isBefore(day.closingWindowEnd()) == false) {
            return Optional.of(RenderBasis.AFTER_HOURS);
        }
        if (time.isBefore(day.closingWindowStart()) == false) {
            return Optional.of(RenderBasis.CLOSE);
        }
        return Optional.empty();
    }

    public boolean isFinalClosingSnapshot(LocalDateTime time, int collectionIntervalMinutes) {
        CalendarDayTimes day = marketCalendarTimeService.resolve(time.toLocalDate());
        return day.holiday() == false && time.equals(day.closingWindowEnd().minusMinutes(collectionIntervalMinutes));
    }
}
