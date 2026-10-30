package dev.eolmae.marketry.domain.custom.service;

import dev.eolmae.marketry.common.exception.ConflictException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.NotFoundException;
import dev.eolmae.marketry.domain.auth.service.CurrentUser;
import dev.eolmae.marketry.domain.custom.dto.SectorDeletePreview;
import dev.eolmae.marketry.domain.custom.dto.SectorItem;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketry.domain.custom.enums.ProtectedSector;
import dev.eolmae.marketry.domain.custom.enums.SectorDeleteBlockReason;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockSectorRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final ProtectedSectorService protectedSectorService;
    private final JdbcTemplate jdbcTemplate;
    private final SuspendedStockClassificationService suspendedStockClassificationService;

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
        // 초기화로 지워진 배정 중 지금 거래 정지인 종목은 다시 거래 정지 업종에 놓는다.
        suspendedStockClassificationService.applyCurrentExclusionsToUser(userId);
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
            return SectorDeletePreview.blocked(target.getName(), SectorDeleteBlockReason.PROTECTED, List.of());
        }
        List<String> subSectorNames = findSubSectorNames(sectorId, maps.sectorByParentId());
        if (!subSectorNames.isEmpty()) {
            return SectorDeletePreview.blocked(
                    target.getName(), SectorDeleteBlockReason.HAS_SUB_SECTORS, subSectorNames);
        }
        return SectorDeletePreview.deletable(
                target.getName(),
                customStockSectorRepository
                        .findByIdUserIdAndSectorId(userId, sectorId)
                        .size());
    }

    /**
     * 업종을 삭제하고 담긴 종목 전부(비활성·화면에 없는 종목 포함)를 미분류로 옮긴다. 하위 업종이 있으면 삭제하지 않는다.
     * expectedMoveCount가 있으면 미리보기에서 확인한 종목 수와 지금 옮길 종목 수를 비교해, 다르면 삭제하지 않고 다시 확인하게 한다.
     */
    public void delete(Long sectorId, Integer expectedMoveCount) {
        Long userId = CurrentUser.requireId();
        SectorMaps maps = getSectorMaps();
        Map<Long, CustomSector> sectorById = maps.sectorById();
        CustomSector target = findSector(sectorId, sectorById);
        requireNotProtected(target);
        if (!findSubSectorNames(sectorId, maps.sectorByParentId()).isEmpty()) {
            throw new ConflictException(ErrorCode.SECTOR_HAS_SUB_SECTORS, sectorId);
        }

        List<CustomStockSector> assignments = customStockSectorRepository.findByIdUserIdAndSectorId(userId, sectorId);
        if (expectedMoveCount != null && expectedMoveCount != assignments.size()) {
            throw new ConflictException(ErrorCode.SECTOR_MOVE_COUNT_CHANGED, sectorId);
        }
        if (!assignments.isEmpty()) {
            Long unclassifiedId = findUnclassifiedSectorId(userId);
            log.info("[섹터삭제] 종목을 미분류로 이동 | sectorId={}|count={}", sectorId, assignments.size());
            assignments.forEach(assignment -> assignment.reassign(unclassifiedId));
            // 배정 행을 먼저 새 업종으로 내보내야, 이 업종을 가리키는 FK 때문에 삭제가 실패하지 않는다.
            customStockSectorRepository.flush();
            // 삭제로 미분류에 옮긴 것도 사용자가 직접 한 분류라 첫 분류 시각을 기록한다. CustomStockSectorService와 같은 문장이다.
            // 값이 비어 있을 때만 채우는 조건부 UPDATE라 JPQL/QueryDSL보다 한 문장으로 쓰는 편이 안전해 JdbcTemplate을 쓴다.
            jdbcTemplate.update(
                    "UPDATE users SET first_custom_classification_at = CURRENT_TIMESTAMP WHERE id = ? AND first_custom_classification_at IS NULL",
                    userId);
        }
        customSectorRepository.delete(target);
    }

    private Long findUnclassifiedSectorId(Long userId) {
        return findOrCreateUnclassified(userId)
                .orElseThrow(() -> new ConflictException(ErrorCode.SECTOR_UNCLASSIFIED_MISSING));
    }

    /** 최상위 미분류 업종을 찾는다. 없으면(오래된 데이터) 보호 업종을 채운 뒤 한 번 더 찾는다. */
    private java.util.Optional<Long> findOrCreateUnclassified(Long userId) {
        java.util.Optional<Long> found = findUnclassified(userId);
        if (found.isPresent()) {
            return found;
        }
        protectedSectorService.ensureForUser(userId);
        return findUnclassified(userId);
    }

    private java.util.Optional<Long> findUnclassified(Long userId) {
        return findAllSectors(userId).stream()
                .filter(sector -> sector.isProtected()
                        && ProtectedSector.UNCLASSIFIED.getSectorName().equals(sector.getName()))
                .map(CustomSector::getId)
                .findFirst();
    }

    private List<String> findSubSectorNames(Long sectorId, Map<Long, List<CustomSector>> sectorByParentId) {
        return sectorByParentId.getOrDefault(sectorId, List.of()).stream()
                .map(CustomSector::getName)
                .toList();
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
