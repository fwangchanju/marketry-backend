package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.cache.CacheKey;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.config.SectorPriceCacheConfig;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * (마켓, 시각) 하나 단위의 종목코드 → 가격 캐시. buildSectorTree/buildDefaultMarketMap이 매번
 * SectorPriceSnapshotService로 가격 행을 DB에서 새로 읽던 것을 캐시로 감싼다.
 *
 * <p>SectorPriceSnapshotService 안에 두지 않고 별도 빈으로 분리했다 — Spring {@code @Cacheable}은
 * 프록시로 동작해서 같은 클래스 안에서 메서드를 부르면 캐시를 안 타는데,
 * SectorPriceSnapshotService.findLatestPriceByStockCode가 이미 같은 클래스의
 * findPriceByStockCode를 부르고 있어 거기 붙이면 그 경로가 조용히 캐시를 건너뛴다.
 * SectorPriceSnapshotService의 기존 메서드(findPriceByStockCode 등)는 그대로 둔다.
 */
@Service
@RequiredArgsConstructor
public class SectorPriceCacheService {

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;

    /**
     * 빈 결과는 캐시하지 않는다 — 수집 커밋 전에 어떤 시각을 조회하면 빈 맵이 그대로 2시간 동안 굳어
     * 버려서, 그 시각은 그동안 지도·섹터·텔레그램 전부 빈다. 과거 시각의 가격 행은 한 번 쓰이면 안
     * 바뀌므로, 비어 있지 않은 결과만 캐시하면 별도 무효화가 필요 없다.
     */
    @Cacheable(
            value = CacheKey.SECTOR_PRICE,
            cacheManager = SectorPriceCacheConfig.SECTOR_PRICE_CACHE_MANAGER,
            unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public Map<String, CachedStockPrice> getCache(Market market, LocalDateTime snapshotTime) {
        // merge 함수 없는 toMap — findPriceByStockCode와 같다. 같은 종목코드가 중복되면(있을 수 없는
        // 상황) 조용히 덮어쓰지 않고 예외로 드러나야 한다.
        return Map.copyOf(
                sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(market), snapshotTime).stream()
                        .collect(Collectors.toMap(
                                SectorPriceSnapshot::getStockCode, SectorPriceCacheService::toCachedPrice)));
    }

    private static CachedStockPrice toCachedPrice(SectorPriceSnapshot snapshot) {
        return new CachedStockPrice(snapshot.getCurrentPrice(), snapshot.getChangeRate(), snapshot.getSnapshotTime());
    }

    /**
     * JPA 엔티티를 캐시에 그대로 넣지 않기 위한 불변 값 — toMarketMapItem이 쓰는 필드 셋이다. lastPrice는 직전 거래일 종가를 덮어쓸 때만
     * 채우며, 비어 있으면 종목 정보의 전일 종가를 쓴다.
     */
    public record CachedStockPrice(
            BigDecimal currentPrice, BigDecimal changeRate, LocalDateTime snapshotTime, BigDecimal lastPrice) {
        public CachedStockPrice(BigDecimal currentPrice, BigDecimal changeRate, LocalDateTime snapshotTime) {
            this(currentPrice, changeRate, snapshotTime, null);
        }

        public CachedStockPrice withLastPrice(BigDecimal lastPrice) {
            return new CachedStockPrice(currentPrice, changeRate, snapshotTime, lastPrice);
        }
    }
}
