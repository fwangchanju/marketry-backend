package kr.co.marketry.domain.stock.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.co.marketry.domain.stock.entity.IndustryInfo;
import kr.co.marketry.domain.stock.entity.StockIndustryOverride;
import kr.co.marketry.domain.stock.entity.StockInfo;
import kr.co.marketry.domain.stock.repository.IndustryInfoRepository;
import kr.co.marketry.domain.stock.repository.StockIndustryOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 종목의 거래소 분류 이름을 구한다. 키움 업종이 있으면 그 값을, 없을 때만 보정표 값을 쓴다. */
@Service
@RequiredArgsConstructor
public class StockIndustryNameResolver {

    private final IndustryInfoRepository industryInfoRepository;
    private final StockIndustryOverrideRepository stockIndustryOverrideRepository;

    /** 종목코드 → 거래소 분류 이름. 분류를 알 수 없는 종목은 담지 않는다. */
    public Map<String, String> resolve(Collection<StockInfo> stocks) {
        Map<String, Long> overrideIdByStockCode = stockIndustryOverrideRepository.findAll().stream()
                .collect(Collectors.toMap(StockIndustryOverride::getStockCode, StockIndustryOverride::getIndustryId));
        Map<String, Long> industryIdByStockCode = new HashMap<>();
        for (StockInfo stock : stocks) {
            Long industryId = stock.getIndustryId() != null
                    ? stock.getIndustryId()
                    : overrideIdByStockCode.get(stock.getStockCode());
            if (industryId != null) {
                industryIdByStockCode.put(stock.getStockCode(), industryId);
            }
        }
        Set<Long> industryIds =
                industryIdByStockCode.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (industryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> industryNameById = industryInfoRepository.findAllById(industryIds).stream()
                .collect(Collectors.toMap(IndustryInfo::getId, IndustryInfo::getName));
        Map<String, String> result = new HashMap<>();
        industryIdByStockCode.forEach((stockCode, industryId) -> {
            String name = industryNameById.get(industryId);
            if (name != null) {
                result.put(stockCode, name);
            }
        });
        return result;
    }
}
