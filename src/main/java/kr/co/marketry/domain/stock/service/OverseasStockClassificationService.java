package kr.co.marketry.domain.stock.service;

import java.util.Map;
import java.util.stream.Collectors;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.entity.OverseasStockClassification;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationId;
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

    @Transactional
    public void update(String market, String stockCode, String field, String value) {
        validateMarket(market);
        if (stockCode == null
                || !stockCode.matches("[0-9][0-9A-Z]{3}")
                || !("sector".equals(field) || "industry".equals(field) || "subIndustry".equals(field))) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if (normalized != null && normalized.length() > 12) throw new BadRequestException(ErrorCode.INVALID_INPUT);
        OverseasStockClassificationId id = new OverseasStockClassificationId(market, stockCode);
        OverseasStockClassification entry =
                repository.findById(id).orElseGet(() -> OverseasStockClassification.create(id));
        if ("sector".equals(field)) entry.updateSector(normalized);
        else if ("industry".equals(field)) entry.updateIndustry(normalized);
        else entry.updateSubIndustry(normalized);
        repository.save(entry);
    }

    // 읽기는 일본과 미국, 쓰기는 일본만 연다. 미국 분류는 아직 관리자 화면에서 고치지 않는다.
    private void validateReadMarket(String market) {
        if (!"JAPAN".equals(market) && !"USA".equals(market)) throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }

    private void validateMarket(String market) {
        if (!"JAPAN".equals(market)) throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }

    public record Classification(String sector, String industry, String subIndustry) {}
}
