package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.stock.entity.StockTradingSuspension;
import dev.eolmae.marketry.domain.stock.repository.StockTradingSuspensionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래정지가 이어진 기간을 기록하고, 연속 5거래일에 이른 종목을 지도와 평균에서 뺄 때 알려 준다. 정지가 풀리면 그날 기록을
 * 닫아서 가격이 다시 들어온 날부터 평균과 지도에 돌아온다.
 */
@Service
@RequiredArgsConstructor
public class StockTradingSuspensionService {

    /** 거래정지가 이 거래일 수만큼 이어지면 지도와 평균에서 뺀다. */
    static final int EXCLUDE_AFTER_TRADING_DAYS = 5;

    private final StockTradingSuspensionRepository suspensionRepository;
    private final MarketCalendarTimeService marketCalendarTimeService;

    /**
     * 오늘 거래정지 중인 종목으로 기록을 맞춘다. 거래일 아침에 한 번 부른다 — 새로 정지된 종목은 기록을 열고, 이어지는 종목은
     * 연속 거래일 수를 세 5거래일에 이르면 제외를 시작하며, 더는 정지가 아닌 종목은 기록을 닫는다.
     */
    @Transactional
    public void record(Set<String> suspendedStockCodes, LocalDate today) {
        LocalDateTime now = KstClock.now();
        Map<String, StockTradingSuspension> openByStockCode = new HashMap<>();
        for (StockTradingSuspension open : suspensionRepository.findByEndedOnIsNull()) {
            openByStockCode.putIfAbsent(open.getStockCode(), open);
        }

        for (String stockCode : suspendedStockCodes) {
            StockTradingSuspension open = openByStockCode.get(stockCode);
            if (open == null) {
                suspensionRepository.save(StockTradingSuspension.start(stockCode, today, now));
                continue;
            }
            // 시작일을 포함해 오늘까지의 거래일 수 — 시작일 하루 전을 기준으로 세면 시작일이 거래일일 때 그날도 센다.
            long tradingDays = marketCalendarTimeService.countTradingDays(
                    open.getStartedOn().minusDays(1), today);
            if (tradingDays >= EXCLUDE_AFTER_TRADING_DAYS) {
                open.markExcluded(today, now);
            }
        }

        for (StockTradingSuspension open : suspensionRepository.findByEndedOnIsNull()) {
            if (!suspendedStockCodes.contains(open.getStockCode())) {
                open.end(today, now);
            }
        }
    }

    /** {@code date} 기준으로 지도와 평균에서 뺄 종목코드. 지난 날짜를 볼 때도 그날 기준으로 판단한다. */
    @Transactional(readOnly = true)
    public Set<String> findExcludedStockCodes(LocalDate date) {
        Set<String> excluded = new HashSet<>();
        addExcluded(excluded, suspensionRepository.findByExcludedFromLessThanEqualAndEndedOnIsNull(date), date);
        addExcluded(
                excluded, suspensionRepository.findByExcludedFromLessThanEqualAndEndedOnGreaterThan(date, date), date);
        return excluded;
    }

    private static void addExcluded(Set<String> excluded, List<StockTradingSuspension> suspensions, LocalDate date) {
        for (StockTradingSuspension suspension : suspensions) {
            if (suspension.isExcludedOn(date)) {
                excluded.add(suspension.getStockCode());
            }
        }
    }
}
