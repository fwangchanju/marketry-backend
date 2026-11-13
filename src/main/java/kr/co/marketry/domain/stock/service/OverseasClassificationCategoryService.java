package kr.co.marketry.domain.stock.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategory;
import kr.co.marketry.domain.stock.entity.OverseasStockClassification;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationId;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OverseasClassificationCategoryService {
    private static final String UNCLASSIFIED = "미분류";
    private static final int LEVELS = 3;
    private static final int MAX_DEPTH = LEVELS - 1;

    private final OverseasClassificationCategoryRepository categories;
    private final OverseasStockClassificationRepository stocks;

    @Transactional(readOnly = true)
    public List<Category> findAll(String market) {
        validateReadMarket(market);
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
            if (isUnclassified(parent) || path(all, parent).size() > MAX_DEPTH) invalid();
        }
        ensureUnique(all, normalized, null);
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
        ensureUnique(all, normalized, id);
        if (isUnclassified(category)) invalid();
        List<String> path = path(all, category);
        for (OverseasStockClassification stock : stocks.findAllByIdMarket(market)) {
            if (!isUnder(stock, path)) continue;
            List<String> next = new ArrayList<>(values(stock));
            next.set(path.size() - 1, normalized);
            apply(stock, next);
        }
        category.rename(normalized);
    }

    @Transactional
    public void move(String market, long id, Long parentId) {
        validateMarket(market);
        List<OverseasClassificationCategory> all = categories.findAllByMarket(market);
        OverseasClassificationCategory category = find(all, id);
        if (isUnclassified(category)) invalid();
        if (Objects.equals(category.getParentId(), parentId)) return;
        OverseasClassificationCategory target = parentId == null ? null : find(all, parentId);
        List<String> targetPath = target == null ? List.of() : path(all, target);
        if (target != null
                && (isUnclassified(target)
                        || isSelfOrDescendant(all, target, id)
                        || targetPath.size() + height(all, id) > MAX_DEPTH)) invalid();
        List<String> path = path(all, category);
        for (OverseasStockClassification stock : stocks.findAllByIdMarket(market)) {
            if (!isUnder(stock, path)) continue;
            List<String> next = new ArrayList<>(targetPath);
            next.add(category.getName());
            next.addAll(values(stock).subList(path.size(), LEVELS));
            apply(stock, next);
        }
        category.reparent(parentId);
    }

    @Transactional
    public void delete(String market, long id, int expectedMoveCount) {
        validateMarket(market);
        List<OverseasClassificationCategory> all = categories.findAllByMarket(market);
        OverseasClassificationCategory category = find(all, id);
        if (isUnclassified(category) || all.stream().anyMatch(item -> Objects.equals(item.getParentId(), id))) {
            invalid();
        }
        List<String> path = path(all, category);
        List<OverseasStockClassification> affected = stocks.findAllByIdMarket(market).stream()
                .filter(stock -> isUnder(stock, path))
                .toList();
        if (affected.size() != expectedMoveCount) invalid();
        for (OverseasStockClassification stock : affected) {
            apply(stock, List.of(UNCLASSIFIED));
        }
        categories.delete(category);
    }

    // 국내 종목 표와 같다. 고른 분류까지만 남고 그 아래 단계는 비워진다.
    @Transactional
    public void assign(String market, List<String> codes, long categoryId) {
        validateMarket(market);
        if (codes == null
                || codes.isEmpty()
                || codes.size() > 1000
                || codes.stream().anyMatch(code -> code == null || !code.matches("[0-9][0-9A-Z]{3}"))) invalid();
        List<OverseasClassificationCategory> all = categories.findAllByMarket(market);
        List<String> path = path(all, find(all, categoryId));
        for (String code : codes.stream().distinct().toList()) {
            OverseasStockClassificationId id = new OverseasStockClassificationId(market, code);
            OverseasStockClassification stock =
                    stocks.findById(id).orElseGet(() -> OverseasStockClassification.create(id));
            apply(stock, path);
            stocks.save(stock);
        }
    }

    private List<String> path(List<OverseasClassificationCategory> all, OverseasClassificationCategory category) {
        List<String> path = new ArrayList<>();
        for (OverseasClassificationCategory current = category;
                current != null;
                current = current.getParentId() == null ? null : find(all, current.getParentId())) {
            path.addFirst(current.getName());
        }
        return path;
    }

    private int height(List<OverseasClassificationCategory> all, long id) {
        return all.stream()
                .filter(item -> Objects.equals(item.getParentId(), id))
                .mapToInt(item -> height(all, item.getId()) + 1)
                .max()
                .orElse(0);
    }

    private boolean isSelfOrDescendant(
            List<OverseasClassificationCategory> all, OverseasClassificationCategory category, long id) {
        for (OverseasClassificationCategory current = category;
                current != null;
                current = current.getParentId() == null ? null : find(all, current.getParentId())) {
            if (current.getId().equals(id)) return true;
        }
        return false;
    }

    private boolean isUnclassified(OverseasClassificationCategory category) {
        return category.getParentId() == null && UNCLASSIFIED.equals(category.getName());
    }

    private List<String> values(OverseasStockClassification stock) {
        return Arrays.asList(stock.getSectorName(), stock.getIndustryName(), stock.getSubIndustryName());
    }

    private boolean isUnder(OverseasStockClassification stock, List<String> path) {
        return values(stock).subList(0, path.size()).equals(path);
    }

    private void apply(OverseasStockClassification stock, List<String> path) {
        stock.updateSector(path.get(0));
        stock.updateIndustry(path.size() > 1 ? path.get(1) : null);
        stock.updateSubIndustry(path.size() > 2 ? path.get(2) : null);
    }

    private OverseasClassificationCategory find(List<OverseasClassificationCategory> all, long id) {
        return all.stream()
                .filter(category -> category.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_INPUT));
    }

    // 지도가 분류를 이름으로 찾으므로 이름은 단계와 상위 분류에 상관없이 시장 안에서 하나뿐이어야 한다.
    private void ensureUnique(List<OverseasClassificationCategory> all, String name, Long excludedId) {
        if (all.stream()
                .anyMatch(category ->
                        category.getName().equalsIgnoreCase(name) && !Objects.equals(category.getId(), excludedId)))
            invalid();
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 12) invalid();
        String normalized = name.trim();
        // 국내 전용 보호 이름이다. 화면이 이 이름을 잠그므로 해외 분류에서는 만들지 못하게 한다.
        if ("신규 상장".equals(normalized) || "거래 정지".equals(normalized)) invalid();
        return normalized;
    }

    // 읽기는 일본과 미국, 쓰기는 일본만 연다. 미국 분류는 아직 관리자 화면에서 고치지 않는다.
    private void validateReadMarket(String market) {
        if (!"JAPAN".equals(market) && !"USA".equals(market)) invalid();
    }

    private void validateMarket(String market) {
        if (!"JAPAN".equals(market)) invalid();
    }

    private void invalid() {
        throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }

    public record Category(Long id, Long parentId, String name) {}
}
