package kr.co.marketry.domain.stock.service;

import java.util.Map;
import java.util.stream.Collectors;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OverseasStockClassificationService {
    private final OverseasStockClassificationRepository repository;

    @Transactional(readOnly = true)
    public Map<String, Classification> findAll(String market) {
        validateReadMarket(market);
        return repository.findAllByIdMarket(market).stream()
                .collect(Collectors.toMap(
                        entry -> entry.getId().getStockCode(),
                        entry -> new Classification(
                                entry.getSectorName(), entry.getIndustryName(), entry.getSubIndustryName())));
    }

    private void validateReadMarket(String market) {
        if (!"JAPAN".equals(market) && !"USA".equals(market)) throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }

    public record Classification(String sector, String industry, String subIndustry) {}
}
