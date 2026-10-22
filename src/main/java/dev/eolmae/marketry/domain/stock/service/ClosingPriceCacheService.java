package dev.eolmae.marketry.domain.stock.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.eolmae.marketry.common.cache.CacheService;
import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.event.MarketCalendarChangedEvent;
import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 조회·정리와 같은 날짜별 종가 선택 기준을 캐시한다. 시간외 시작 전과 빈 기준가는 다시 조회한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClosingPriceCacheService implements CacheService<ClosingPrices> {

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final MarketCalendarTimeService marketCalendarTimeService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final Cache<LocalDate, CachedClosingPrices> closingPricesByDate =
            Caffeine.newBuilder().maximumSize(4).build();

    @Override
    @Transactional(readOnly = true)
    public ClosingPrices getCache() {
        LocalDateTime now = KstClock.now();
        return getFor(now.toLocalDate(), now);
    }

    @Override
    public synchronized void evict() {
        closingPricesByDate.invalidateAll();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarketCalendarChanged(MarketCalendarChangedEvent event) {
        if (event.country() == Country.KR) {
            evict();
        }
    }

    @Transactional(readOnly = true)
    public ClosingPrices getFor(LocalDate baseDate, LocalDateTime now) {
        return getFor(baseDate, now, marketCalendarTimeService.resolve(baseDate));
    }

    @Transactional(readOnly = true)
    public synchronized ClosingPrices getFor(LocalDate baseDate, LocalDateTime now, CalendarDayTimes times) {
        if (times.holiday()) {
            closingPricesByDate.invalidate(baseDate);
            return new ClosingPrices(baseDate, Map.of());
        }
        CachedClosingPrices cached = closingPricesByDate.getIfPresent(baseDate);
        // 시간표 조회가 실패했다가 복구되는 경우에는 이벤트가 없어도 경계 변경을 반영한다.
        if (cached != null && cached.times().equals(times)) {
            return cached.prices();
        }
        closingPricesByDate.invalidate(baseDate);
        ClosingPrices loaded = loadFor(baseDate, times);
        if (now.isBefore(times.closingWindowEnd()) == false
                && loaded.priceByStockCode().isEmpty() == false) {
            closingPricesByDate.put(baseDate, new CachedClosingPrices(times, loaded));
        }
        return loaded;
    }

    @Transactional(readOnly = true)
    public ClosingPrices loadFor(LocalDate baseDate) {
        return loadFor(baseDate, marketCalendarTimeService.resolve(baseDate));
    }

    private ClosingPrices loadFor(LocalDate baseDate, CalendarDayTimes times) {
        if (times.holiday()) {
            return new ClosingPrices(baseDate, Map.of());
        }
        var selected = sectorPriceSnapshotService.findClosingMarketSnapshotTimes(baseDate, times);
        if (selected.isEmpty()) {
            log.debug("[종가기준가없음] | context : {}|{}", baseDate, times.closingWindowEnd());
            return new ClosingPrices(baseDate, Map.of());
        }
        Map<String, BigDecimal> prices = sectorPriceSnapshotRepository.findByMarketSnapshotTimes(selected).stream()
                .collect(Collectors.toUnmodifiableMap(
                        SectorPriceSnapshot::getStockCode, SectorPriceSnapshot::getCurrentPrice));
        return new ClosingPrices(baseDate, prices);
    }

    private record CachedClosingPrices(CalendarDayTimes times, ClosingPrices prices) {}
}
