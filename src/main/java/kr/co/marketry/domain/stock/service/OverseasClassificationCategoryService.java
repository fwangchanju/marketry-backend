package kr.co.marketry.domain.stock.service;

import java.util.List;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OverseasClassificationCategoryService {
    private final OverseasClassificationCategoryRepository categories;

    @Transactional(readOnly = true)
    public List<Category> findAll(String market) {
        CountryRules.of(market);
        return categories.findAllByMarket(market).stream()
                .map(category -> new Category(category.getId(), category.getParentId(), category.getName()))
                .toList();
    }

    public record Category(Long id, Long parentId, String name) {}
}
