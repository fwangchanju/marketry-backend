package kr.co.marketry.domain.stock.service;

import java.util.Map;
import java.util.stream.Collectors;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.entity.UsaStockKoreanName;
import kr.co.marketry.domain.stock.repository.UsaStockKoreanNameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsaStockKoreanNameService {
    private final UsaStockKoreanNameRepository repository;

    @Transactional(readOnly = true)
    public Map<String, String> findAll() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(UsaStockKoreanName::getSymbol, UsaStockKoreanName::getKoreanName));
    }

    @Transactional
    public void update(String symbol, String koreanName) {
        if (!CountryRules.USA.isValidStockCode(symbol)
                || koreanName == null
                || koreanName.isBlank()
                || koreanName.trim().length() > 100) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
        String value = koreanName.trim();
        UsaStockKoreanName entry =
                repository.findById(symbol).orElseGet(() -> UsaStockKoreanName.create(symbol, value));
        entry.update(value);
        repository.save(entry);
    }
}
