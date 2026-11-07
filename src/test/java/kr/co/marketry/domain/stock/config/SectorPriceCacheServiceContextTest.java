package kr.co.marketry.domain.stock.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.common.cache.CacheKey;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.config.ApplicationConfig;
import kr.co.marketry.domain.stock.entity.SectorPriceSnapshot;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import kr.co.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import kr.co.marketry.domain.stock.service.SectorPriceCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Configuration;

/**
 * SectorPriceCacheService.getCache가 실제로 {@code @Cacheable} 프록시를 타는지 확인한다(5-5).
 * {@code new}로 만든 객체는 프록시가 없어 검증할 수 없어서, SchedulingConfigTest처럼
 * ApplicationContextRunner로 작은 컨텍스트를 띄운다. ApplicationConfig 전체를 그대로 올리면
 * jpaQueryFactory(EntityManager) 빈 때문에 기동이 실패하므로, 캐시 관련 설정만 올린다.
 */
class SectorPriceCacheServiceContextTest {

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository =
            mock(SectorPriceSnapshotRepository.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CachingEnabledConfig.class, SectorPriceCacheConfig.class)
            .withBean(SectorPriceCacheService.class, sectorPriceSnapshotRepository);

    @Test
    void 같은_마켓과_시각으로_두_번_부르면_리포지토리는_한_번만_불린다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of(priceSnapshot("005930")));

        runner.run(context -> {
            SectorPriceCacheService service = context.getBean(SectorPriceCacheService.class);
            service.getCache(Market.KOSPI, snapshotTime);
            service.getCache(Market.KOSPI, snapshotTime);

            verify(sectorPriceSnapshotRepository, times(1))
                    .findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime);
        });
    }

    // unless = "#result == null || #result.isEmpty()" — 빈 결과는 캐시하지 않으므로 두 번째 호출도
    // 리포지토리를 다시 부른다(5-5).
    @Test
    void 빈_결과는_캐시되지_않아_두_번_부르면_리포지토리도_두_번_불린다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of());

        runner.run(context -> {
            SectorPriceCacheService service = context.getBean(SectorPriceCacheService.class);
            service.getCache(Market.KOSPI, snapshotTime);
            service.getCache(Market.KOSPI, snapshotTime);

            verify(sectorPriceSnapshotRepository, times(2))
                    .findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime);
        });
    }

    // 전용 매니저를 별도 @Configuration(SectorPriceCacheConfig)에 둔 이유가 기본 cacheManager에 TTL이
    // 새지 않게 하는 것이었다(결정 5) — 기본 매니저가 여전히 TTL 없이 그대로인지 직접 확인한다.
    @Test
    void 기본_cacheManager는_STOCK_INFO_캐시에_TTL을_걸지_않는다() {
        CaffeineCache stockInfoCache =
                (CaffeineCache) new ApplicationConfig().cacheManager().getCache(CacheKey.STOCK_INFO);

        assertThat(stockInfoCache).isNotNull();
        assertThat(stockInfoCache.getNativeCache().policy().expireAfterWrite()).isEmpty();
    }

    @Configuration
    @EnableCaching
    static class CachingEnabledConfig {}

    private SectorPriceSnapshot priceSnapshot(String stockCode) {
        return SectorPriceSnapshot.create(
                Market.KOSPI,
                LocalDateTime.of(2026, 7, 31, 10, 0),
                stockCode,
                ExchangeType.KRX,
                stockCode,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
