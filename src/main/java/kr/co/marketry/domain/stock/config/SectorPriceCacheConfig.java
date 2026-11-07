package kr.co.marketry.domain.stock.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import kr.co.marketry.common.cache.CacheKey;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// SectorPriceCacheService 전용 매니저 — 기본 cacheManager(config.ApplicationConfig)는 TTL이 없고
// STOCK_INFO 캐시가 쓰므로, 여기에 TTL을 걸면 해당 캐시까지 함께 만료된다. ApplicationConfig에는
// jpaQueryFactory(EntityManager) 빈이 있어 좁은 컨텍스트 테스트(@Cacheable 검증)에 올리면 기동이
// 실패하므로, 그 빈과 분리하기 위해 별도 @Configuration에 둔다.
@Configuration
public class SectorPriceCacheConfig {

    public static final String SECTOR_PRICE_CACHE_MANAGER = "sectorPriceCacheManager";

    @Bean(SECTOR_PRICE_CACHE_MANAGER)
    public CacheManager sectorPriceCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CacheKey.SECTOR_PRICE);
        manager.setCaffeine(
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(2)).maximumSize(100));
        return manager;
    }
}
