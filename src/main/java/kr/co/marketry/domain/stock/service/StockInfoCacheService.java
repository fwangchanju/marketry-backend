package kr.co.marketry.domain.stock.service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.marketry.common.cache.CacheKey;
import kr.co.marketry.common.cache.CacheService;
import kr.co.marketry.domain.stock.entity.StockInfo;
import kr.co.marketry.domain.stock.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockInfoCacheService implements CacheService<Map<String, StockInfo>> {

    private final StockInfoRepository stockInfoRepository;

    @Override
    @Cacheable(CacheKey.STOCK_INFO)
    @Transactional(readOnly = true)
    public Map<String, StockInfo> getCache() {
        return stockInfoRepository.findAll().stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
    }

    @Override
    @CacheEvict(value = CacheKey.STOCK_INFO, allEntries = true)
    public void evict() {}
}
