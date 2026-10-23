package dev.eolmae.marketry.domain.notification.service;

import dev.eolmae.marketry.domain.notification.enums.RenderBasis;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse;
import dev.eolmae.marketry.domain.view.service.MarketTradingScheduleService;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 발송 시각에 맞는 등락률 기준을 고른다 — 정규장 마감부터 애프터 마켓 시작 전까지는 종가, 애프터 마켓 시작부터는 별도, 그 전에는 기준을 주지 않는다(누적).
@Component
@RequiredArgsConstructor
public class RenderBasisResolver {

    private final MarketTradingScheduleService marketTradingScheduleService;

    public Optional<RenderBasis> resolve(LocalDateTime time) {
        MarketTradingScheduleResponse schedule = marketTradingScheduleService.getTradingSchedule(time.toLocalDate());
        if (schedule.status() != MarketCalendarStatus.TRADING_DAY
                || schedule.regularMarket() == null
                || schedule.afterMarket() == null) {
            return Optional.empty();
        }
        if (time.isBefore(schedule.afterMarket().startTime()) == false) {
            return Optional.of(RenderBasis.AFTER_HOURS);
        }
        if (time.isBefore(schedule.regularMarket().endTime()) == false) {
            return Optional.of(RenderBasis.CLOSE);
        }
        return Optional.empty();
    }
}
