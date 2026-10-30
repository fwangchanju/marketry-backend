package dev.eolmae.marketry.domain.custom.service;

import dev.eolmae.marketry.common.exception.ConflictException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.NotFoundException;
import dev.eolmae.marketry.domain.auth.service.CurrentUser;
import dev.eolmae.marketry.domain.custom.dto.SectorDeletePreview;
import dev.eolmae.marketry.domain.custom.dto.SectorItem;
import dev.eolmae.marketry.domain.custom.dto.StockSectorItem;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketry.domain.custom.enums.ProtectedSector;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 섹터 추가/삭제/재부모화. 항상 라이브(현재 표시 중인) 트리만을 대상으로 한다. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CustomSectorService {

    private final CustomSectorRepository customSectorRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final StockInfoCacheService stockInfoCacheService;
    private final ProtectedSectorService protectedSectorService;

    @Transactional(readOnly = true)
    public List<SectorItem> getSectors() {
        return findAllSectors(CurrentUser.requireId()).stream()
                .map(this::toItem)
                .toList();
    }

    public void reset() {
        Long userId = CurrentUser.requireId();
        customStockSectorRepository.deleteAllByIdUserId(userId);
        customSectorRepository.deleteAllOfUser(userId);
        protectedSectorService.ensureForUser(userId);
    }

    public SectorItem createParent(String name) {
        Long userId = CurrentUser.requireId();
        requireNotReservedName(name);
        if (customSectorRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException(ErrorCode.SECTOR_NAME_DUPLICATE, name);
        }
        CustomSector sector = CustomSector.createParent(userId, name);
        return toItem(customSectorRepository.save(sector));
    }

    public SectorItem createChild(String name, Long parentId) {
        Long userId = CurrentUser.requireId();
        requireNotReservedName(name);
        if (customSectorRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException(ErrorCode.SECTOR_NAME_DUPLICATE, name);
        }
        CustomSector parent = customSectorRepository
                .findByIdAndUserId(parentId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SECTOR_NOT_FOUND, parentId));
        requireNotProtected(parent);
        CustomSector sector = CustomSector.createChild(userId, name, parent);
        return toItem(customSectorRepository.save(sector));
    }

    public void exclude(Long sectorId) {
        CustomSector target = findOwnedSector(sectorId, CurrentUser.requireId());
        target.exclude();
    }

    public void include(Long sectorId) {
        CustomSector target = findOwnedSector(sectorId, CurrentUser.requireId());
        target.include();
    }

    public void resetExcludes() {
        findAllSectors(CurrentUser.requireId()).forEach(CustomSector::include);
    }

    public void rename(Long sectorId, String name) {
        Long userId = CurrentUser.requireId();
        CustomSector target = findOwnedSector(sectorId, userId);
        requireNotProtected(target);
        if (target.getName().equals(name)) {
            return;
        }
        requireNotReservedName(name);
        if (customSectorRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException(ErrorCode.SECTOR_NAME_DUPLICATE, name);
        }
        target.rename(name);
    }

    /** sectorId를 newParentId의 자식으로 옮긴다. newParentId가 null이면 최상위(루트)로 옮긴다.
     * newParentId가 sectorId 자신이거나 그 하위 섹터면 순환 구조가 되므로 409로 막는다.
     * 하위 섹터 전체는 depth 변화량만큼 함께 갱신한다. */
    public void reparent(Long sectorId, Long newParentId) {
        SectorMaps maps = getSectorMaps();
        Map<Long, CustomSector> sectorById = maps.sectorById();
        Map<Long, List<CustomSector>> sectorByParentId = maps.sectorByParentId();
        CustomSector target = findSector(sectorId, sectorById);
        requireNotProtected(target);

        List<Long> subSectorIds = collectSubSectorIds(sectorId, sectorByParentId);
        if (subSectorIds.contains(newParentId)) {
            throw new ConflictException(ErrorCode.SECTOR_CIRCULAR_REFERENCE, sectorId, newParentId);
        }

        int newDepth = 0;
        if (newParentId != null) {
            CustomSector newParent = findSector(newParentId, sectorById);
            requireNotProtected(newParent);
            newDepth = newParent.getDepth() + 1;
        }
        int depthDifference = newDepth - target.getDepth();
        target.changeParent(newParentId);

        // target을 포함한 하위 섹터 전체 depth를 depthDifference만큼 일괄 이동
        if (depthDifference != 0) {
            subSectorIds.stream()
                    .map(sectorById::get)
                    .forEach(sector -> sector.changeDepth(sector.getDepth() + depthDifference));
        }
    }

    @Transactional(readOnly = true)
    public SectorDeletePreview deletePreview(Long sectorId) {
        Long userId = CurrentUser.requireId();
        SectorMaps maps = getSectorMaps();
        Map<Long, CustomSector> sectorById = maps.sectorById();
        CustomSector target = findSector(sectorId, sectorById);
        if (target.isProtected()) {
            return SectorDeletePreview.blocked(target.getName(), List.of());
        }

        List<Long> subSectorIds = collectSubSectorIds(sectorId, maps.sectorByParentId());
        List<CustomStockSector> stockSectors =
                customStockSectorRepository.findByIdUserIdAndSectorIdIn(userId, subSectorIds);
        List<CustomStockSector> blockingStockSectors = findBlockingStockSectors(stockSectors);
        if (!blockingStockSectors.isEmpty()) {
            return SectorDeletePreview.blocked(
                    target.getName(), toBlockingStockSectorItems(blockingStockSectors, sectorById));
        }
        return SectorDeletePreview.deletable(
                target.getName(), toDeletableSectorNames(sectorId, subSectorIds, sectorById));
    }

    public void delete(Long sectorId) {
        Long userId = CurrentUser.requireId();
        SectorMaps maps = getSectorMaps();
        Map<Long, CustomSector> sectorById = maps.sectorById();
        requireNotProtected(findSector(sectorId, sectorById));

        List<Long> subSectorIds = collectSubSectorIds(sectorId, maps.sectorByParentId());
        List<CustomStockSector> stockSectors =
                customStockSectorRepository.findByIdUserIdAndSectorIdIn(userId, subSectorIds);
        if (!findBlockingStockSectors(stockSectors).isEmpty()) {
            throw new ConflictException(ErrorCode.SECTOR_HAS_ASSIGNED_STOCK, sectorId);
        }

        // 활성 주권 배정은 없다고 확인했지만(위 판정), 비활성 종목의 배정 행은 여전히 남아있을 수 있다 —
        // custom_sector를 가리키는 FK라 섹터 삭제 전에 먼저 지워야 한다(결정 1).
        if (!stockSectors.isEmpty()) {
            log.info("[섹터삭제] 비활성 배정 행 삭제 | sectorId={}|count={}", sectorId, stockSectors.size());
            customStockSectorRepository.deleteByUserIdAndSectorIdIn(userId, subSectorIds);
        }

        List<CustomSector> subSectors = subSectorIds.stream()
                .map(sectorById::get)
                .sorted(Comparator.comparingInt(CustomSector::getDepth).reversed())
                .toList();
        customSectorRepository.deleteAll(subSectors);
    }

    /** 보호 업종(신규 상장·거래 정지·미분류)은 삭제·이름 변경·이동을 하지 못하고, 그 아래에 하위 업종을 만들 수도 없다. */
    private void requireNotProtected(CustomSector sector) {
        if (sector.isProtected()) {
            throw new ConflictException(ErrorCode.SECTOR_PROTECTED, sector.getId());
        }
    }

    /** 보호 업종 이름은 다른 업종이 쓸 수 없다. 보호 업종 자체는 서버가 ProtectedSectorService로 만든다. */
    private void requireNotReservedName(String name) {
        if (ProtectedSector.isReservedName(name)) {
            throw new ConflictException(ErrorCode.SECTOR_NAME_RESERVED, name);
        }
    }

    /** 섹터 전체 스냅샷을 id 조회용/parentId 그룹핑용 두 가지 형태로 함께 준비해둔다.
     * reparent/deletePreview/delete처럼 둘 다 필요한 경우에만 사용. */
    private record SectorMaps(Map<Long, CustomSector> sectorById, Map<Long, List<CustomSector>> sectorByParentId) {}

    private SectorMaps getSectorMaps() {
        List<CustomSector> sectors = findAllSectors(CurrentUser.requireId());
        Map<Long, CustomSector> sectorById = new HashMap<>();
        Map<Long, List<CustomSector>> sectorByParentId = new HashMap<>();
        for (CustomSector sector : sectors) {
            sectorById.put(sector.getId(), sector);
            sectorByParentId
                    .computeIfAbsent(sector.getParentId(), key -> new ArrayList<>())
                    .add(sector);
        }
        return new SectorMaps(sectorById, sectorByParentId);
    }

    private List<CustomSector> findAllSectors(Long userId) {
        return customSectorRepository.findAllByUserId(userId);
    }

    private CustomSector findOwnedSector(Long sectorId, Long userId) {
        return customSectorRepository
                .findByIdAndUserId(sectorId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SECTOR_NOT_FOUND, sectorId));
    }

    private CustomSector findSector(Long sectorId, Map<Long, CustomSector> sectorById) {
        CustomSector sector = sectorById.get(sectorId);
        if (sector == null) {
            throw new NotFoundException(ErrorCode.SECTOR_NOT_FOUND, sectorId);
        }
        return sector;
    }

    private List<String> toDeletableSectorNames(
            Long sectorId, List<Long> subSectorIds, Map<Long, CustomSector> sectorById) {
        return subSectorIds.stream()
                .filter(id -> !id.equals(sectorId))
                .map(id -> sectorById.get(id).getName())
                .toList();
    }

    /** "이 섹터들을 막는" 배정만 남긴다 — 그 행이 가리키는 종목이 stock_info 캐시에서 활성 주권인
     * 것만(캐시에 아예 없는 종목은 화면에도 안 보이므로 "막지 않음"으로 친다, 5-3). deletePreview·delete가
     * 이 필터를 공유해서 화면(종목 관리 페이지)과 같은 기준으로 판정한다. */
    private List<CustomStockSector> findBlockingStockSectors(List<CustomStockSector> stockSectors) {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return stockSectors.stream()
                .filter(stockSector -> isActiveOrdinaryStock(stockSector, stockInfoCache))
                .toList();
    }

    private boolean isActiveOrdinaryStock(CustomStockSector stockSector, Map<String, StockInfo> stockInfoCache) {
        StockInfo stockInfo = stockInfoCache.get(stockSector.getStockCode());
        return stockInfo != null && stockInfo.isActiveAndOrdinary();
    }

    private List<StockSectorItem> toBlockingStockSectorItems(
            List<CustomStockSector> stockSectors, Map<Long, CustomSector> sectorById) {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return stockSectors.stream()
                .map(stockSector -> new StockSectorItem(
                        stockSector.getStockCode(),
                        resolveStockName(stockSector.getStockCode(), stockInfoCache),
                        sectorById.get(stockSector.getSectorId()).getName()))
                .toList();
    }

    /** 캐시에 없는 종목이면 종목코드를 그대로 이름 자리에 넣는다 — findBlockingStockSectors가 활성
     * 주권만 통과시키므로 이 자리는 지금 도달 불가능하지만, "캐시가 낡았을 때"의 대가(5-3)를 실제 예외로
     * 만들지 않기 위한 방어다. */
    private String resolveStockName(String stockCode, Map<String, StockInfo> stockInfoCache) {
        StockInfo stockInfo = stockInfoCache.get(stockCode);
        return stockInfo != null ? stockInfo.getStockName() : stockCode;
    }

    private List<Long> collectSubSectorIds(Long sectorId, Map<Long, List<CustomSector>> sectorByParentId) {
        List<Long> collectedIds = new ArrayList<>();
        collectedIds.add(sectorId);
        collectSubSectors(sectorId, sectorByParentId, collectedIds);
        return collectedIds;
    }

    private void collectSubSectors(
            Long parentId, Map<Long, List<CustomSector>> sectorByParentId, List<Long> collectedIds) {
        for (CustomSector child : sectorByParentId.getOrDefault(parentId, List.of())) {
            collectedIds.add(child.getId());
            collectSubSectors(child.getId(), sectorByParentId, collectedIds);
        }
    }

    private SectorItem toItem(CustomSector sector) {
        return new SectorItem(sector.getId(), sector.getName(), sector.getParentId(), sector.getDepth());
    }
}
