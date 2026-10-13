package dev.eolmae.marketry.domain.stock.collector;

import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.stock.client.TossStockListClient;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketry.domain.stock.service.StockTradingSuspensionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 거래일 아침에 토스로 거래정지 중인 종목을 받아 거래정지 기간을 기록한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSuspensionCollector {

    private final StockInfoCacheService stockInfoCacheService;
    private final TossStockListClient tossStockListClient;
    private final StockTradingSuspensionService suspensionService;
    private final MarketCalendarTimeService marketCalendarTimeService;

    public void sync() {
        LocalDate today = KstClock.now().toLocalDate();
        if (!marketCalendarTimeService.isTradingDay(today)) {
            log.info("거래정지 종목 기록 생략: 휴장일 date={}", today);
            return;
        }
        List<String> stockCodes = stockInfoCacheService.getCache().values().stream()
                .filter(StockInfo::isActiveAndOrdinary)
                .map(StockInfo::getStockCode)
                .toList();
        Set<String> suspended = tossStockListClient.fetchSuspendedStockCodes(stockCodes);
        suspensionService.record(suspended, today);
        log.info("거래정지 종목 기록 완료: 조회 종목 수={}, 거래정지={}", stockCodes.size(), suspended.size());
    }
}
