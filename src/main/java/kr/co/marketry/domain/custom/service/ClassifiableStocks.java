package kr.co.marketry.domain.custom.service;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.stock.collector.JapanMarketReference;
import kr.co.marketry.domain.stock.collector.UsaConstituents;
import kr.co.marketry.domain.stock.entity.StockInfo;
import kr.co.marketry.domain.stock.repository.StockInfoRepository;
import kr.co.marketry.domain.stock.service.CountryRules;
import kr.co.marketry.domain.stock.service.StockInfoCacheService;
import org.springframework.stereotype.Component;

/** 시장마다 다른 "분류할 수 있는 종목"을 한 곳에서 답한다. */
@Component
public class ClassifiableStocks {

    private final StockInfoCacheService stockInfoCacheService;
    private final StockInfoRepository stockInfoRepository;
    private final Set<String> japanCodes;
    private final Set<String> usaCodes;

    public ClassifiableStocks(
            StockInfoCacheService stockInfoCacheService,
            StockInfoRepository stockInfoRepository,
            UsaConstituents usaConstituents) {
        this.stockInfoCacheService = stockInfoCacheService;
        this.stockInfoRepository = stockInfoRepository;
        this.japanCodes = JapanMarketReference.constituentCodes();
        this.usaCodes = usaConstituents.all().stream()
                .map(UsaConstituents.Constituent::symbol)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 지금 사용자가 업종에 넣을 수 있는 종목인지. */
    public boolean canClassify(ClassificationMarket market, String stockCode) {
        return switch (market) {
            case KOREA -> {
                StockInfo stock = stockInfoCacheService.getCache().get(stockCode);
                yield stock != null && stock.isActiveAndOrdinary();
            }
            case JAPAN -> japanCodes.contains(stockCode);
            case USA -> usaCodes.contains(stockCode);
        };
    }

    /**
     * 저장해 둔 버전의 종목이 그 시장의 종목인지. 지수에서 빠진 종목이 든 옛 버전도 되돌릴 수 있게,
     * 일본·미국은 지금 목록이 아니라 종목 코드 모양만 본다(한국도 상장 폐지된 종목을 종목 정보에 남겨 둔다).
     */
    public boolean existsAll(ClassificationMarket market, Collection<String> stockCodes) {
        return switch (market) {
            case KOREA ->
                stockInfoRepository.findAllById(stockCodes).stream()
                        .map(StockInfo::getStockCode)
                        .collect(Collectors.toSet())
                        .containsAll(stockCodes);
            case JAPAN -> stockCodes.stream().allMatch(CountryRules.JAPAN::isValidStockCode);
            case USA -> stockCodes.stream().allMatch(CountryRules.USA::isValidStockCode);
        };
    }
}
