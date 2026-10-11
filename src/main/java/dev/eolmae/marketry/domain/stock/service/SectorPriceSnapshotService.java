package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.SnapshotDaySummary;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.TimeWindow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시장별 최신 종목 가격 스냅샷 조회. */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SectorPriceSnapshotService {

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final MarketCalendarTimeService marketCalendarTimeService;
    private final StockDailyPriceService stockDailyPriceService;

    /** markets 전부가 공통으로 가진 최신 스냅샷 시각 — markets가 하나뿐이면 그 마켓의 최신 시각과 같다. */
    public Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets) {
        return sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                markets, KstClock.now().toLocalDate());
    }

    public boolean existsSnapshot(Market market, LocalDateTime snapshotTime) {
        return sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, snapshotTime);
    }

    public boolean notExistsSnapshot(Market market, LocalDateTime snapshotTime) {
        return !existsSnapshot(market, snapshotTime);
    }

    /** markets를 한 번의 IN 쿼리로 조회해 종목코드 기준으로 합친다 — 종목코드가 마켓 간에 겹치지 않으므로
     * 그대로 하나의 맵으로 합쳐도 안전하다. */
    public Map<String, SectorPriceSnapshot> findPriceByStockCode(List<Market> markets, LocalDateTime snapshotTime) {
        return sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(markets, snapshotTime).stream()
                .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));
    }

    /** market 인자 없이 KOSPI/KOSDAQ 각각 최신 스냅샷 가격을 종목코드 기준으로 합침 — 마켓별로 독립적인
     * "그 마켓의 최신"이라 공통 시각이 아니라 마켓 하나짜리 리스트로 각각 조회한다. */
    public Map<String, SectorPriceSnapshot> findLatestPriceByStockCode() {
        return Arrays.stream(Market.values())
                .flatMap(market -> findLatestCommonSnapshotTime(List.of(market))
                        .map(snapshotTime -> findPriceByStockCode(List.of(market), snapshotTime))
                        .orElse(Map.of())
                        .values()
                        .stream())
                .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));
    }

    /**
     * 달력에서 고를 수 있는 한 달의 날짜와 그 시각. 활성화는 거래일(공통 판정)이고 오늘 이하이며 종가 데이터(KRX)나 현재가 데이터의 종가가
     * 있는 날이다. 현재가 데이터가 있으면 그 종가 시각을 쓰고(정리 배치와 같은 기준, 한 시각이면 그대로, 여러 시각이면 시간외 시작 이전
     * latest), 종가 데이터만 있는 날은 그날 종가 구간 끝 시각을 쓴다. 시장끼리 현재가 데이터 시각이 엇갈린 날은 종가 데이터가 없으면 뺀다(그
     * 시각으로는 모든 시장의 지도를 그릴 수 없다). 명시적 휴장일은 가격이 있어도 뺀다.
     */
    public Map<LocalDate, LocalDateTime> findClosingSnapshotTimes(List<Market> markets, YearMonth month) {
        return findClosingSnapshotTimes(markets, month, KstClock.now().toLocalDate());
    }

    Map<LocalDate, LocalDateTime> findClosingSnapshotTimes(List<Market> markets, YearMonth month, LocalDate today) {
        List<LocalDate> dates = month.atDay(1)
                .datesUntil(month.plusMonths(1).atDay(1))
                .filter(date -> date.isAfter(today) == false)
                .toList();
        if (dates.isEmpty()) {
            return Map.of();
        }
        Map<LocalDate, CalendarDayTimes> tradingDays = marketCalendarTimeService.resolveTradingDaysWithFallback(dates);
        if (tradingDays.isEmpty()) {
            return Map.of();
        }
        Map<LocalDate, LocalDateTime> closingTimes = new TreeMap<>(findSnapshotClosingTimes(markets, tradingDays));
        for (LocalDate date : stockDailyPriceService.findKrxPriceDates(tradingDays.keySet())) {
            closingTimes.putIfAbsent(date, tradingDays.get(date).closingWindowEnd());
        }
        return closingTimes;
    }

    private Map<LocalDate, LocalDateTime> findSnapshotClosingTimes(
            List<Market> markets, Map<LocalDate, CalendarDayTimes> tradingDays) {
        List<TimeWindow> closingWindows = tradingDays.entrySet().stream()
                .map(entry -> new TimeWindow(
                        entry.getKey().atStartOfDay(), entry.getValue().closingWindowEnd()))
                .toList();
        Map<LocalDate, List<MarketSnapshotTime>> closingByDate =
                sectorPriceSnapshotRepository.findSnapshotDaySummaries(closingWindows).stream()
                        .flatMap(summary -> summary.closingSnapshot().stream())
                        .collect(Collectors.groupingBy(
                                closing -> closing.snapshotTime().toLocalDate()));
        Map<LocalDate, LocalDateTime> closingTimes = new TreeMap<>();
        for (Map.Entry<LocalDate, List<MarketSnapshotTime>> entry : closingByDate.entrySet()) {
            Map<Market, LocalDateTime> timeByMarket = entry.getValue().stream()
                    .collect(Collectors.toMap(MarketSnapshotTime::market, MarketSnapshotTime::snapshotTime));
            Set<LocalDateTime> marketTimes =
                    markets.stream().map(timeByMarket::get).collect(Collectors.toSet());
            if (marketTimes.size() == 1 && marketTimes.contains(null) == false) {
                closingTimes.put(entry.getKey(), marketTimes.iterator().next());
            }
        }
        return closingTimes;
    }

    /** 가격이 있는 모든 거래일로 최근 보존 일수를 센다. 정리 실패 날짜도 다음 실행에서 다시 포함된다. */
    public List<LocalDate> findCleanupDates(LocalDate today, int retainedTradingDays) {
        List<LocalDate> olderDates = sectorPriceSnapshotRepository.findTradingSnapshotDates(today).stream()
                .skip(retainedTradingDays)
                .toList();
        if (olderDates.isEmpty()) {
            return List.of();
        }
        return sectorPriceSnapshotRepository.findMultipleSnapshotDates(olderDates.getFirst());
    }

    public List<MarketSnapshotTime> findClosingMarketSnapshotTimes(LocalDate date) {
        return findClosingMarketSnapshotTimes(date, marketCalendarTimeService.resolve(date));
    }

    List<MarketSnapshotTime> findClosingMarketSnapshotTimes(LocalDate date, CalendarDayTimes times) {
        if (times.holiday()) {
            return List.of();
        }
        return sectorPriceSnapshotRepository
                .findSnapshotDaySummaries(List.of(new TimeWindow(date.atStartOfDay(), times.closingWindowEnd())))
                .stream()
                .flatMap(summary -> summary.closingSnapshot().stream())
                .toList();
    }

    /** 지정 날짜 하루만 정리한다. 휴장과 종가 후보 없는 시장은 보존하며, 실패 날짜 재실행에도 같은 로직을 쓴다. */
    @Transactional
    public void cleanupSnapshotsForDate(LocalDate date) {
        CalendarDayTimes times =
                marketCalendarTimeService.resolveTradingDays(List.of(date)).get(date);
        if (times == null) {
            log.debug("[섹터가격스냅샷정리] 확인된 거래일이 아니어서 생략 | context : {}", date);
            return;
        }
        List<SnapshotDaySummary> summaries = sectorPriceSnapshotRepository.findSnapshotDaySummaries(
                List.of(new TimeWindow(date.atStartOfDay(), times.closingWindowEnd())));
        List<MarketSnapshotTime> retained = summaries.stream()
                .filter(summary -> summary.distinctTimeCount() > 1)
                .flatMap(summary -> summary.closingSnapshot().stream())
                .toList();
        if (retained.isEmpty()) {
            log.debug("[섹터가격스냅샷정리] 이미 정리됐거나 종가 후보가 없어 생략 | context : {}", date);
            return;
        }
        long deletedCount = sectorPriceSnapshotRepository.deleteSnapshotsForDate(date, retained);
        log.info("[섹터가격스냅샷정리] 삭제완료 | context : {}|{}", date, deletedCount);
    }
}
