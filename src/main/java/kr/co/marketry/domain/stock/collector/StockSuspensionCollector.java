package kr.co.marketry.domain.stock.collector;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import kr.co.marketry.common.event.StockSuspensionSyncedEvent;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.stock.client.TossStockListClient;
import kr.co.marketry.domain.stock.dto.StockSuspensionStatus;
import kr.co.marketry.domain.stock.entity.StockInfo;
import kr.co.marketry.domain.stock.service.MarketCalendarTimeService;
import kr.co.marketry.domain.stock.service.StockInfoCacheService;
import kr.co.marketry.domain.stock.service.StockTradingSuspensionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
        if (stockCodes.isEmpty()) {
            // 조회 대상이 비면 열린 정지 기록을 모두 닫게 되므로 그날은 기록하지 않는다.
            log.warn("거래정지 종목 기록 생략: 조회 대상 종목이 없음 date={}", today);
            return;
        }
        StockSuspensionStatus status = tossStockListClient.fetchSuspensionStatus(stockCodes);
        suspensionService.record(status.suspended(), status.resumed(), Set.copyOf(stockCodes), today);
        log.info(
                "거래정지 종목 기록 완료: 조회 종목 수={}, 거래정지={}, 재개 확인={}",
                stockCodes.size(),
                status.suspended().size(),
                status.resumed().size());
        // 연속 5거래일에 이른 종목과 재개가 확인된 종목을 알려, 사용자의 업종 분류에 반영하게 한다.
        eventPublisher.publishEvent(
                new StockSuspensionSyncedEvent(suspensionService.findExcludedStockCodes(today), status.resumed()));
    }
}
