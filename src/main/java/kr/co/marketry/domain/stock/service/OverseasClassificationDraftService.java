package kr.co.marketry.domain.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.entity.OverseasClassificationBackup;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategory;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategoryDraft;
import kr.co.marketry.domain.stock.entity.OverseasStockClassification;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationDraft;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationId;
import kr.co.marketry.domain.stock.repository.OverseasClassificationBackupRepository;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryDraftRepository;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockClassificationDraftRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import kr.co.marketry.domain.stock.service.ClassificationSyncPlanner.DesiredCategory;
import kr.co.marketry.domain.stock.service.ClassificationSyncPlanner.LiveCategory;
import kr.co.marketry.domain.stock.service.ClassificationSyncPlanner.Plan;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService.Category;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService.Classification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일본·미국 "내 기준" — 관리자 한 명이 운영 분류와 따로 고치는 초안.
 *
 * <p>편집(추가·이름 변경·이동·삭제·배정)은 운영 분류 편집과 같은 규칙을 초안 표에만 적용한다. 반영하면 초안이 운영 분류가 되고, 반영 직전 운영 분류는 시장마다 한 번분만 백업해 되돌릴 수 있다.
 */
@Service
@RequiredArgsConstructor
public class OverseasClassificationDraftService {
    private static final String UNCLASSIFIED = "미분류";
    // 종목 표의 분류 열 수(섹터·업종·세부 업종). 시장마다 쓰는 단계 수는 CountryRules이 정한다.
    private static final int COLUMNS = 3;
    // 반영 중 이름이 겹치는 순간이 생기지 않게 업종 이름을 잠시 바꿔 두는 접두어. 최종 이름이 정해지면 모두 지워진다.
    private static final String TEMPORARY_NAME_PREFIX = "~";
    private static final String NEW_TEMPORARY_NAME_PREFIX = "~new";

    private final OverseasClassificationCategoryDraftRepository draftCategories;
    private final OverseasStockClassificationDraftRepository draftStocks;
    private final OverseasClassificationCategoryRepository liveCategories;
    private final OverseasStockClassificationRepository liveStocks;
    private final OverseasClassificationBackupRepository backups;
    private final OverseasClassificationPublicationService publications;
    private final OverseasClassificationDraftEditService editTime;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> findEditedAt(String market) {
        return editTime.findEditedAt(market);
    }

    @Transactional
    public DraftView findDraft(String market) {
        CountryRules.of(market);
        if (draftCategories.existsByMarket(market) == false) copyLiveToDraft(market);
        return view(market);
    }

    @Transactional(readOnly = true)
    public Status findStatus(String market) {
        CountryRules.of(market);
        OverseasClassificationBackup backup = backups.findById(market).orElse(null);
        return new Status(
                draftCategories.existsByMarket(market), backup != null, backup == null ? null : backup.getCreatedAt());
    }

    // 고치던 초안을 버리고 지금 운영 분류에서 다시 복사한다.
    @Transactional
    public DraftView discard(String market) {
        editTime.recordEdited(market);
        CountryRules.of(market);
        draftStocks.deleteAllByIdMarket(market);
        draftStocks.flush();
        draftCategories.deleteAllByMarket(market);
        copyLiveToDraft(market);
        return view(market);
    }

    @Transactional
    public Category createCategory(String market, Long parentId, String name) {
        editTime.recordEdited(market);
        int maxDepth = CountryRules.of(market).levels() - 1;
        String normalized = normalizeName(name);
        List<OverseasClassificationCategoryDraft> all = draftCategories.findAllByMarket(market);
        if (parentId != null) {
            OverseasClassificationCategoryDraft parent = find(all, parentId);
            if (isUnclassified(parent) || path(all, parent).size() > maxDepth) invalid();
        }
        ensureUnique(all, normalized, null);
        OverseasClassificationCategoryDraft category =
                draftCategories.save(OverseasClassificationCategoryDraft.create(market, null, parentId, normalized));
        return new Category(category.getId(), category.getParentId(), category.getName());
    }

    @Transactional
    public void rename(String market, long id, String name) {
        editTime.recordEdited(market);
        CountryRules.of(market);
        String normalized = normalizeName(name);
        List<OverseasClassificationCategoryDraft> all = draftCategories.findAllByMarket(market);
        OverseasClassificationCategoryDraft category = find(all, id);
        ensureUnique(all, normalized, id);
        if (isUnclassified(category)) invalid();
        List<String> path = path(all, category);
        for (OverseasStockClassificationDraft stock : draftStocks.findAllByIdMarket(market)) {
            if (isUnder(stock, path) == false) continue;
            List<String> next = new ArrayList<>(values(stock));
            next.set(path.size() - 1, normalized);
            apply(stock, next);
        }
        category.rename(normalized);
    }

    @Transactional
    public void move(String market, long id, Long parentId) {
        editTime.recordEdited(market);
        int maxDepth = CountryRules.of(market).levels() - 1;
        List<OverseasClassificationCategoryDraft> all = draftCategories.findAllByMarket(market);
        OverseasClassificationCategoryDraft category = find(all, id);
        if (isUnclassified(category)) invalid();
        if (Objects.equals(category.getParentId(), parentId)) return;
        OverseasClassificationCategoryDraft target = parentId == null ? null : find(all, parentId);
        List<String> targetPath = target == null ? List.of() : path(all, target);
        if (target != null
                && (isUnclassified(target)
                        || isSelfOrDescendant(all, target, id)
                        || targetPath.size() + height(all, id) > maxDepth)) invalid();
        List<String> path = path(all, category);
        for (OverseasStockClassificationDraft stock : draftStocks.findAllByIdMarket(market)) {
            if (isUnder(stock, path) == false) continue;
            List<String> next = new ArrayList<>(targetPath);
            next.add(category.getName());
            next.addAll(values(stock).subList(path.size(), COLUMNS));
            apply(stock, next);
        }
        category.reparent(parentId);
    }

    @Transactional
    public void delete(String market, long id, int expectedMoveCount) {
        editTime.recordEdited(market);
        CountryRules.of(market);
        List<OverseasClassificationCategoryDraft> all = draftCategories.findAllByMarket(market);
        OverseasClassificationCategoryDraft category = find(all, id);
        if (isUnclassified(category) || all.stream().anyMatch(item -> Objects.equals(item.getParentId(), id))) {
            invalid();
        }
        List<String> path = path(all, category);
        List<OverseasStockClassificationDraft> affected = draftStocks.findAllByIdMarket(market).stream()
                .filter(stock -> isUnder(stock, path))
                .toList();
        if (affected.size() != expectedMoveCount) invalid();
        for (OverseasStockClassificationDraft stock : affected) {
            apply(stock, List.of(UNCLASSIFIED));
        }
        draftCategories.delete(category);
    }

    // 고른 분류까지만 남고 그 아래 단계는 비워진다(운영 분류 배정과 같다).
    @Transactional
    public void assign(String market, List<String> codes, long categoryId) {
        editTime.recordEdited(market);
        CountryRules rules = CountryRules.of(market);
        if (codes == null
                || codes.isEmpty()
                || codes.size() > 1000
                || codes.stream().allMatch(rules::isValidStockCode) == false) invalid();
        List<OverseasClassificationCategoryDraft> all = draftCategories.findAllByMarket(market);
        List<String> path = path(all, find(all, categoryId));
        for (String code : codes.stream().distinct().toList()) {
            OverseasStockClassificationId id = new OverseasStockClassificationId(market, code);
            OverseasStockClassificationDraft stock =
                    draftStocks.findById(id).orElseGet(() -> OverseasStockClassificationDraft.create(id));
            apply(stock, path);
            draftStocks.save(stock);
        }
    }

    // MARKETRY에 반영한다. 반영 직전 운영 분류는 백업해 두고, 초안이 가리키는 운영 업종 id는 그대로 유지한다.
    // 초안이 운영 분류와 같으면 아무것도 하지 않는다 — 변경 없이 한 번 더 눌러서 직전 백업이 "변경 없는 상태"로 덮이는 일을 막는다.
    @Transactional
    public PublishResult publish(String market) {
        CountryRules.of(market);
        List<OverseasClassificationCategoryDraft> drafts = draftCategories.findAllByMarket(market);
        if (drafts.isEmpty()) invalid();
        List<OverseasClassificationCategory> live = liveCategories.findAllByMarket(market);
        List<OverseasStockClassification> stocks = liveStocks.findAllByIdMarket(market);
        List<OverseasStockClassificationDraft> draftStockRows = draftStocks.findAllByIdMarket(market);
        if (isSameAsLive(drafts, draftStockRows, live, stocks)) return new PublishResult(false);
        saveBackup(market, live, stocks);
        publications.recordPublished(market);

        List<DesiredCategory> desired = drafts.stream()
                .map(draft ->
                        new DesiredCategory(draft.getId(), draft.getLiveId(), draft.getParentId(), draft.getName()))
                .toList();
        Map<Long, Long> liveIdByKey = applyCategories(market, live, desired);
        for (OverseasClassificationCategoryDraft draft : drafts) {
            draft.linkLive(liveIdByKey.get(draft.getId()));
        }
        replaceLiveStocks(
                market,
                draftStockRows.stream()
                        .map(stock -> new StockRow(
                                stock.getId().getStockCode(),
                                stock.getSectorName(),
                                stock.getIndustryName(),
                                stock.getSubIndustryName()))
                        .toList());
        return new PublishResult(true);
    }

    // 직전에 반영하기 전 운영 분류로 되돌린다. 되돌리면 백업은 지운다(한 번만 되돌릴 수 있다).
    @Transactional
    public void rollback(String market) {
        CountryRules.of(market);
        OverseasClassificationBackup backup =
                backups.findById(market).orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_INPUT));
        BackupPayload payload = readPayload(backup.getPayload());
        List<OverseasClassificationCategory> live = liveCategories.findAllByMarket(market);
        List<DesiredCategory> desired = payload.categories().stream()
                .map(category ->
                        new DesiredCategory(category.id(), category.id(), category.parentId(), category.name()))
                .toList();
        applyCategories(market, live, desired);
        replaceLiveStocks(market, payload.stocks());
        backups.delete(backup);
        publications.recordPublished(market);
    }

    // 초안의 업종 구성과 종목 분류가 운영 분류와 모두 같은지. 초안 업종은 가리키는 운영 업종(liveId)의 이름·부모와 비교한다.
    private boolean isSameAsLive(
            List<OverseasClassificationCategoryDraft> drafts,
            List<OverseasStockClassificationDraft> draftStockRows,
            List<OverseasClassificationCategory> live,
            List<OverseasStockClassification> stocks) {
        if (drafts.size() != live.size() || draftStockRows.size() != stocks.size()) return false;
        Map<Long, OverseasClassificationCategory> liveById =
                live.stream().collect(Collectors.toMap(OverseasClassificationCategory::getId, Function.identity()));
        Map<Long, Long> liveIdByDraftId = new HashMap<>();
        for (OverseasClassificationCategoryDraft draft : drafts) {
            if (draft.getLiveId() == null || liveById.containsKey(draft.getLiveId()) == false) return false;
            liveIdByDraftId.put(draft.getId(), draft.getLiveId());
        }
        for (OverseasClassificationCategoryDraft draft : drafts) {
            OverseasClassificationCategory row = liveById.get(draft.getLiveId());
            Long expectedParentId = draft.getParentId() == null ? null : liveIdByDraftId.get(draft.getParentId());
            if (row.getName().equals(draft.getName()) == false
                    || Objects.equals(row.getParentId(), expectedParentId) == false) {
                return false;
            }
        }
        Map<String, OverseasStockClassification> liveStockByCode =
                stocks.stream().collect(Collectors.toMap(stock -> stock.getId().getStockCode(), Function.identity()));
        for (OverseasStockClassificationDraft draft : draftStockRows) {
            OverseasStockClassification stock =
                    liveStockByCode.get(draft.getId().getStockCode());
            if (stock == null
                    || Objects.equals(stock.getSectorName(), draft.getSectorName()) == false
                    || Objects.equals(stock.getIndustryName(), draft.getIndustryName()) == false
                    || Objects.equals(stock.getSubIndustryName(), draft.getSubIndustryName()) == false) {
                return false;
            }
        }
        return true;
    }

    private void copyLiveToDraft(String market) {
        List<OverseasClassificationCategory> live = liveCategories.findAllByMarket(market);
        Map<Long, OverseasClassificationCategory> liveById =
                live.stream().collect(Collectors.toMap(OverseasClassificationCategory::getId, Function.identity()));
        Map<Long, OverseasClassificationCategoryDraft> draftByLiveId = new HashMap<>();
        List<OverseasClassificationCategory> parentFirst = live.stream()
                .sorted(Comparator.comparingInt(category -> depth(liveById, category)))
                .toList();
        for (OverseasClassificationCategory category : parentFirst) {
            Long parentId = category.getParentId() == null
                    ? null
                    : draftByLiveId.get(category.getParentId()).getId();
            draftByLiveId.put(
                    category.getId(),
                    draftCategories.save(OverseasClassificationCategoryDraft.create(
                            market, category.getId(), parentId, category.getName())));
        }
        for (OverseasStockClassification stock : liveStocks.findAllByIdMarket(market)) {
            OverseasStockClassificationDraft draft = OverseasStockClassificationDraft.create(stock.getId());
            draft.updateSector(stock.getSectorName());
            draft.updateIndustry(stock.getIndustryName());
            draft.updateSubIndustry(stock.getSubIndustryName());
            draftStocks.save(draft);
        }
    }

    // 원하는 업종 구성에 맞게 운영 업종을 고친다. 돌려주는 값은 원하는 항목(key)별 운영 업종 id다.
    private Map<Long, Long> applyCategories(
            String market, List<OverseasClassificationCategory> live, List<DesiredCategory> desired) {
        Plan plan = ClassificationSyncPlanner.plan(
                live.stream()
                        .map(row -> new LiveCategory(row.getId(), row.getParentId()))
                        .toList(),
                desired);
        Map<Long, OverseasClassificationCategory> rowById = new HashMap<>();
        for (OverseasClassificationCategory row : live) {
            rowById.put(row.getId(), row);
        }
        for (Long id : plan.temporaryRenames()) {
            rowById.get(id).rename(TEMPORARY_NAME_PREFIX + id);
        }
        liveCategories.flush();

        Map<Long, Long> liveIdByKey = new HashMap<>();
        for (DesiredCategory category : desired) {
            if (category.liveId() != null && rowById.containsKey(category.liveId())) {
                liveIdByKey.put(category.key(), category.liveId());
            }
        }
        for (DesiredCategory category : plan.creations()) {
            OverseasClassificationCategory created = liveCategories.saveAndFlush(
                    OverseasClassificationCategory.create(market, null, NEW_TEMPORARY_NAME_PREFIX + category.key()));
            rowById.put(created.getId(), created);
            liveIdByKey.put(category.key(), created.getId());
        }
        for (DesiredCategory category : desired) {
            OverseasClassificationCategory row = rowById.get(liveIdByKey.get(category.key()));
            row.reparent(category.parentKey() == null ? null : liveIdByKey.get(category.parentKey()));
            row.rename(category.name());
        }
        liveCategories.flush();

        for (Long id : plan.deletions()) {
            liveCategories.delete(rowById.get(id));
        }
        liveCategories.flush();
        return liveIdByKey;
    }

    private void replaceLiveStocks(String market, List<StockRow> rows) {
        liveStocks.deleteAllByIdMarket(market);
        liveStocks.flush();
        for (StockRow row : rows) {
            OverseasStockClassification stock =
                    OverseasStockClassification.create(new OverseasStockClassificationId(market, row.code()));
            stock.updateSector(row.sector());
            stock.updateIndustry(row.industry());
            stock.updateSubIndustry(row.subIndustry());
            liveStocks.save(stock);
        }
        liveStocks.flush();
    }

    private void saveBackup(
            String market, List<OverseasClassificationCategory> live, List<OverseasStockClassification> stocks) {
        BackupPayload payload = new BackupPayload(
                live.stream()
                        .map(category ->
                                new BackupCategory(category.getId(), category.getParentId(), category.getName()))
                        .toList(),
                stocks.stream()
                        .map(stock -> new StockRow(
                                stock.getId().getStockCode(),
                                stock.getSectorName(),
                                stock.getIndustryName(),
                                stock.getSubIndustryName()))
                        .toList());
        String json = writePayload(payload);
        LocalDateTime now = LocalDateTime.now(clock);
        OverseasClassificationBackup backup = backups.findById(market).orElse(null);
        if (backup == null) {
            backups.save(OverseasClassificationBackup.create(market, json, now));
            return;
        }
        backup.overwrite(json, now);
    }

    private String writePayload(BackupPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }

    private BackupPayload readPayload(String json) {
        try {
            return objectMapper.readValue(json, BackupPayload.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }

    private DraftView view(String market) {
        List<Category> categories = draftCategories.findAllByMarket(market).stream()
                .map(category -> new Category(category.getId(), category.getParentId(), category.getName()))
                .toList();
        Map<String, Classification> classifications = draftStocks.findAllByIdMarket(market).stream()
                .collect(Collectors.toMap(
                        entry -> entry.getId().getStockCode(),
                        entry -> new Classification(
                                entry.getSectorName(), entry.getIndustryName(), entry.getSubIndustryName())));
        return new DraftView(categories, classifications);
    }

    private int depth(Map<Long, OverseasClassificationCategory> liveById, OverseasClassificationCategory category) {
        int depth = 0;
        for (Long parentId = category.getParentId();
                parentId != null;
                parentId = liveById.get(parentId).getParentId()) {
            depth++;
        }
        return depth;
    }

    private List<String> path(
            List<OverseasClassificationCategoryDraft> all, OverseasClassificationCategoryDraft category) {
        List<String> path = new ArrayList<>();
        for (OverseasClassificationCategoryDraft current = category;
                current != null;
                current = current.getParentId() == null ? null : find(all, current.getParentId())) {
            path.addFirst(current.getName());
        }
        return path;
    }

    private int height(List<OverseasClassificationCategoryDraft> all, long id) {
        return all.stream()
                .filter(item -> Objects.equals(item.getParentId(), id))
                .mapToInt(item -> height(all, item.getId()) + 1)
                .max()
                .orElse(0);
    }

    private boolean isSelfOrDescendant(
            List<OverseasClassificationCategoryDraft> all, OverseasClassificationCategoryDraft category, long id) {
        for (OverseasClassificationCategoryDraft current = category;
                current != null;
                current = current.getParentId() == null ? null : find(all, current.getParentId())) {
            if (current.getId().equals(id)) return true;
        }
        return false;
    }

    private boolean isUnclassified(OverseasClassificationCategoryDraft category) {
        return category.getParentId() == null && UNCLASSIFIED.equals(category.getName());
    }

    private List<String> values(OverseasStockClassificationDraft stock) {
        return Arrays.asList(stock.getSectorName(), stock.getIndustryName(), stock.getSubIndustryName());
    }

    private boolean isUnder(OverseasStockClassificationDraft stock, List<String> path) {
        return values(stock).subList(0, path.size()).equals(path);
    }

    private void apply(OverseasStockClassificationDraft stock, List<String> path) {
        stock.updateSector(path.get(0));
        stock.updateIndustry(path.size() > 1 ? path.get(1) : null);
        stock.updateSubIndustry(path.size() > 2 ? path.get(2) : null);
    }

    private OverseasClassificationCategoryDraft find(List<OverseasClassificationCategoryDraft> all, long id) {
        return all.stream()
                .filter(category -> category.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_INPUT));
    }

    // 지도가 분류를 이름으로 찾으므로 이름은 단계와 상위 분류에 상관없이 시장 안에서 하나뿐이어야 한다(운영 분류와 같다).
    private void ensureUnique(List<OverseasClassificationCategoryDraft> all, String name, Long excludedId) {
        if (all.stream()
                .anyMatch(category -> category.getName().equalsIgnoreCase(name)
                        && Objects.equals(category.getId(), excludedId) == false)) invalid();
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 12) invalid();
        String normalized = name.trim();
        // 국내 전용 보호 이름이다. 화면이 이 이름을 잠그므로 해외 분류에서는 만들지 못하게 한다.
        if ("신규 상장".equals(normalized) || "거래 정지".equals(normalized)) invalid();
        if (normalized.startsWith(TEMPORARY_NAME_PREFIX)) invalid();
        return normalized;
    }

    private void invalid() {
        throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }

    public record DraftView(List<Category> categories, Map<String, Classification> classifications) {}

    public record Status(boolean hasDraft, boolean canRollback, LocalDateTime backupCreatedAt) {}

    /** changed가 false면 초안이 운영 분류와 같아서 반영하지 않았다는 뜻이다. */
    public record PublishResult(boolean changed) {}

    record StockRow(String code, String sector, String industry, String subIndustry) {}

    record BackupCategory(long id, Long parentId, String name) {}

    record BackupPayload(List<BackupCategory> categories, List<StockRow> stocks) {}
}
