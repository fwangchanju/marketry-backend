package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.entity.JapanStockKoreanName;
import dev.eolmae.marketry.domain.stock.repository.JapanStockKoreanNameRepository;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JapanStockKoreanNameService {
    private final JapanStockKoreanNameRepository repository;

    @Transactional(readOnly = true)
    public Map<String, String> findAll() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(JapanStockKoreanName::getStockCode, JapanStockKoreanName::getKoreanName));
    }

    @Transactional
    public void update(String stockCode, String koreanName) {
        if (!stockCode.matches("[0-9][0-9A-Z]{3}")
                || koreanName == null
                || koreanName.isBlank()
                || koreanName.trim().length() > 100) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
        String value = koreanName.trim();
        JapanStockKoreanName entry =
                repository.findById(stockCode).orElseGet(() -> JapanStockKoreanName.create(stockCode, value));
        entry.update(value);
        repository.save(entry);
    }
}
