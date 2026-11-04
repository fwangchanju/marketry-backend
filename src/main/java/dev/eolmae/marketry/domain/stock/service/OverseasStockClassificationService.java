package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassification;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassificationId;
import dev.eolmae.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OverseasStockClassificationService {
    private final OverseasStockClassificationRepository repository;

    @Transactional(readOnly = true)
    public Map<String, Classification> findAll(String market) {
        validateMarket(market);
        return repository.findAllByIdMarket(market).stream()
                .collect(Collectors.toMap(
                        entry -> entry.getId().getStockCode(),
                        entry -> new Classification(entry.getSectorName(), entry.getIndustryName())));
    }

    @Transactional
    public void update(String market, String stockCode, String field, String value) {
        validateMarket(market);
        if (stockCode == null
                || !stockCode.matches("[0-9][0-9A-Z]{3}")
                || !("sector".equals(field) || "industry".equals(field))) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if (normalized != null && normalized.length() > 12) throw new BadRequestException(ErrorCode.INVALID_INPUT);
        OverseasStockClassificationId id = new OverseasStockClassificationId(market, stockCode);
        OverseasStockClassification entry =
                repository.findById(id).orElseGet(() -> OverseasStockClassification.create(id));
        if ("sector".equals(field)) entry.updateSector(normalized);
        else entry.updateIndustry(normalized);
        repository.save(entry);
    }

    private void validateMarket(String market) {
        if (!"JAPAN".equals(market)) throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }

    public record Classification(String sector, String industry) {}
}
