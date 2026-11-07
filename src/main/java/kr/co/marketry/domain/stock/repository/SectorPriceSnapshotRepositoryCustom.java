package kr.co.marketry.domain.stock.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.SectorPriceSnapshot;

public interface SectorPriceSnapshotRepositoryCustom {

    /** 여러 마켓을 동시에 보여줄 때(All Stocks 등) 필요한, markets 전부가 공통으로 가진 최신 스냅샷 시각.
     * 한쪽 마켓에만 있고 다른 쪽엔 없는 시각은 제외. */
    Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets, LocalDate today);

    /** 오늘 이하의 확인된 거래일 중 가격 데이터가 있는 날짜. 최신 날짜부터 반환한다. */
    List<LocalDate> findTradingSnapshotDates(LocalDate today);

    /** 이미 한 시각으로 정리된 시장은 제외하고, 여러 시각이 남은 오래된 거래일만 찾는다. */
    List<LocalDate> findMultipleSnapshotDates(LocalDate throughDate);

    /** 하루 전체의 서로 다른 시각 수와 시간외 시작 이전 latest를 시장별로 함께 집계한다. */
    List<SnapshotDaySummary> findSnapshotDaySummaries(List<TimeWindow> windows);

    List<SectorPriceSnapshot> findByMarketSnapshotTimes(List<MarketSnapshotTime> times);

    record SnapshotDaySummary(
            Market market, LocalDateTime latestTime, long distinctTimeCount, LocalDateTime latestBeforeAfterHours) {
        public Optional<MarketSnapshotTime> closingSnapshot() {
            LocalDateTime selected = distinctTimeCount == 1 ? latestTime : latestBeforeAfterHours;
            return Optional.ofNullable(selected).map(time -> new MarketSnapshotTime(market, time));
        }
    }

    /** 지정 날짜에서 보존 시각이 있는 시장만 삭제한다. 보존 시각의 모든 종목 행과 후보 없는 시장은 남긴다. */
    long deleteSnapshotsForDate(LocalDate date, List<MarketSnapshotTime> retainedSnapshotTimes);

    /** 시각 구간 [from, toExclusive). */
    record TimeWindow(LocalDateTime from, LocalDateTime toExclusive) {}

    /** 보존 윈도우 안 후보 하나 — 어느 마켓의 몇 시 스냅샷인지. */
    record MarketSnapshotTime(Market market, LocalDateTime snapshotTime) {}
}
