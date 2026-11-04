package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.entity.OverseasClassificationCategory;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassification;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassificationId;
import dev.eolmae.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import dev.eolmae.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OverseasClassificationCategoryService {
    private final OverseasClassificationCategoryRepository categories;
    private final OverseasStockClassificationRepository stocks;

    @Transactional(readOnly = true)
    public List<Category> findAll(String market) {
        validateMarket(market);
        return categories.findAllByMarket(market).stream()
                .map(category -> new Category(category.getId(), category.getParentId(), category.getName()))
                .toList();
    }

    @Transactional
    public Category create(String market, Long parentId, String name) {
        validateMarket(market);
        String normalized = normalizeName(name);
        List<OverseasClassificationCategory> all = categories.findAllByMarket(market);
        if (parentId != null) {
            OverseasClassificationCategory parent = find(all, parentId);
            if (parent.getParentId() != null || "미분류".equals(parent.getName())) invalid();
        }
        ensureUnique(all, parentId, normalized, null);
        OverseasClassificationCategory category =
                categories.save(OverseasClassificationCategory.create(market, parentId, normalized));
        return new Category(category.getId(), category.getParentId(), category.getName());
    }

    @Transactional
    public void rename(String market, long id, String name) {
        validateMarket(market);
        String normalized = normalizeName(name);
        List<OverseasClassificationCategory> all = categories.findAllByMarket(market);
        OverseasClassificationCategory category = find(all, id);
        ensureUnique(all, category.getParentId(), normalized, id);
        if (category.getParentId() == null && "미분류".equals(category.getName())) invalid();
        String oldName = category.getName();
        String sector = category.getParentId() == null
                ? oldName
                : find(all, category.getParentId()).getName();
        for (OverseasStockClassification stock : stocks.findAllByIdMarket(market)) {
            if (!sector.equals(stock.getSectorName())) continue;
            if (category.getParentId() == null) stock.updateSector(normalized);
            else if (oldName.equals(stock.getIndustryName())) stock.updateIndustry(normalized);
        }
        category.rename(normalized);
    }

    @Transactional
    public void move(String market, long id, Long parentId) {
        validateMarket(market);
        List<OverseasClassificationCategory> all = categories.findAllByMarket(market);
        OverseasClassificationCategory category = find(all, id);
        if ("미분류".equals(category.getName()) && category.getParentId() == null) invalid();
        if (java.util.Objects.equals(category.getParentId(), parentId)) return;
        OverseasClassificationCategory target = parentId == null ? null : find(all, parentId);
        if (target != null
                && (target.getParentId() != null
                        || "미분류".equals(target.getName())
                        || target.getId().equals(id)
                        || all.stream().anyMatch(item -> java.util.Objects.equals(item.getParentId(), id)))) invalid();
        ensureUnique(all, parentId, category.getName(), id);
        String previousSector = category.getParentId() == null
                ? category.getName()
                : find(all, category.getParentId()).getName();
        for (OverseasStockClassification stock : stocks.findAllByIdMarket(market)) {
            if (!previousSector.equals(stock.getSectorName())) continue;
            if (category.getParentId() != null && !category.getName().equals(stock.getIndustryName())) continue;
            stock.updateSector(target == null ? category.getName() : target.getName());
            stock.updateIndustry(target == null ? null : category.getName());
        }
        category.reparent(parentId);
    }

    @Transactional
    public void delete(String market, long id, int expectedMoveCount) {
        validateMarket(market);
        List<OverseasClassificationCategory> all = categories.findAllByMarket(market);
        OverseasClassificationCategory category = find(all, id);
        if (("미분류".equals(category.getName()) && category.getParentId() == null)
                || all.stream().anyMatch(item -> java.util.Objects.equals(item.getParentId(), id))) invalid();
        String sector = category.getParentId() == null
                ? category.getName()
                : find(all, category.getParentId()).getName();
        List<OverseasStockClassification> affected = stocks.findAllByIdMarket(market).stream()
                .filter(stock -> sector.equals(stock.getSectorName()))
                .filter(stock ->
                        category.getParentId() == null || category.getName().equals(stock.getIndustryName()))
                .toList();
        if (affected.size() != expectedMoveCount) invalid();
        for (OverseasStockClassification stock : affected) {
            stock.updateSector("미분류");
            stock.updateIndustry(null);
        }
        categories.delete(category);
    }

    @Transactional
    public void assign(String market, List<String> codes, long categoryId) {
        validateMarket(market);
        if (codes == null
                || codes.isEmpty()
                || codes.size() > 1000
                || codes.stream().anyMatch(code -> code == null || !code.matches("[0-9][0-9A-Z]{3}"))) invalid();
        List<OverseasClassificationCategory> all = categories.findAllByMarket(market);
        OverseasClassificationCategory category = find(all, categoryId);
        OverseasClassificationCategory sector =
                category.getParentId() == null ? category : find(all, category.getParentId());
        for (String code : codes.stream().distinct().toList()) {
            OverseasStockClassificationId id = new OverseasStockClassificationId(market, code);
            OverseasStockClassification stock =
                    stocks.findById(id).orElseGet(() -> OverseasStockClassification.create(id));
            if (!sector.getName().equals(stock.getSectorName())) stock.updateIndustry(null);
            stock.updateSector(sector.getName());
            if (category.getParentId() != null) stock.updateIndustry(category.getName());
            stocks.save(stock);
        }
    }

    private OverseasClassificationCategory find(List<OverseasClassificationCategory> all, long id) {
        return all.stream()
                .filter(category -> category.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_INPUT));
    }

    private void ensureUnique(List<OverseasClassificationCategory> all, Long parent, String name, Long excludedId) {
        if (all.stream()
                .anyMatch(category -> java.util.Objects.equals(category.getParentId(), parent)
                        && category.getName().equalsIgnoreCase(name)
                        && !java.util.Objects.equals(category.getId(), excludedId))) invalid();
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 12) invalid();
        return name.trim();
    }

    private void validateMarket(String market) {
        if (!"JAPAN".equals(market)) invalid();
    }

    private void invalid() {
        throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }

    public record Category(Long id, Long parentId, String name) {}
}
