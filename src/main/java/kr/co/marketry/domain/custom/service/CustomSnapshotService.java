package kr.co.marketry.domain.custom.service;

import java.util.List;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.NotFoundException;
import kr.co.marketry.domain.auth.service.CurrentUser;
import kr.co.marketry.domain.custom.dto.SnapshotItem;
import kr.co.marketry.domain.custom.entity.CustomSector;
import kr.co.marketry.domain.custom.entity.CustomSnapshot;
import kr.co.marketry.domain.custom.repository.CustomSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 사용자의 전체 커스텀 데이터 스냅샷을 관리한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class CustomSnapshotService {

    private final CustomSnapshotRepository customSnapshotRepository;
    private final CustomSectorRepository customSectorRepository;
    private final CustomSectorTreeService customSectorTreeService;

    @Transactional(readOnly = true)
    public List<SnapshotItem> getVersions() {
        Long userId = CurrentUser.requireId();
        return customSnapshotRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(snapshot -> customSectorTreeService.isCurrentSnapshotFormat(snapshot.getSnapshotJson()))
                .map(this::toItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public SnapshotItem currentVersion() {
        Long userId = CurrentUser.requireId();
        Long snapshotId = customSectorRepository
                .findFirstByUserIdOrderByIdAsc(userId)
                .map(CustomSector::getSnapshotId)
                .orElse(null);
        if (snapshotId == null) {
            return null;
        }
        return customSnapshotRepository
                .findByIdAndUserId(snapshotId, userId)
                .filter(snapshot -> customSectorTreeService.isCurrentSnapshotFormat(snapshot.getSnapshotJson()))
                .map(this::toItem)
                .orElse(null);
    }

    public SnapshotItem save(String label) {
        Long userId = CurrentUser.requireId();
        String snapshotJson = customSectorTreeService.serializeCurrentSnapshot(userId);
        CustomSnapshot saved = customSnapshotRepository.save(CustomSnapshot.create(userId, label, snapshotJson));
        tagLiveSectors(userId, saved.getId());
        return toItem(saved);
    }

    public SnapshotItem overwrite(Long snapshotId, String label) {
        Long userId = CurrentUser.requireId();
        CustomSnapshot snapshot = findOwnedSnapshot(snapshotId, userId);
        if (!customSectorTreeService.isCurrentSnapshotFormat(snapshot.getSnapshotJson())) {
            throw new BadRequestException(ErrorCode.SNAPSHOT_FORMAT_UNSUPPORTED);
        }
        String snapshotJson = customSectorTreeService.serializeCurrentSnapshot(userId);
        snapshot.overwrite(label, snapshotJson);
        tagLiveSectors(userId, snapshotId);
        return toItem(snapshot);
    }

    public void restore(Long snapshotId) {
        Long userId = CurrentUser.requireId();
        CustomSnapshot snapshot = findOwnedSnapshot(snapshotId, userId);
        if (!customSectorTreeService.isCurrentSnapshotFormat(snapshot.getSnapshotJson())) {
            throw new BadRequestException(ErrorCode.SNAPSHOT_FORMAT_UNSUPPORTED);
        }
        customSectorTreeService.restore(snapshot.getSnapshotJson(), userId, snapshotId);
    }

    public void delete(Long snapshotId) {
        Long userId = CurrentUser.requireId();
        findOwnedSnapshot(snapshotId, userId);
        customSnapshotRepository.deleteByIdAndUserId(snapshotId, userId);
    }

    private CustomSnapshot findOwnedSnapshot(Long snapshotId, Long userId) {
        return customSnapshotRepository
                .findByIdAndUserId(snapshotId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SNAPSHOT_NOT_FOUND, snapshotId));
    }

    private void tagLiveSectors(Long userId, Long snapshotId) {
        customSectorRepository.findAllByUserId(userId).forEach(sector -> sector.tagSnapshot(snapshotId));
    }

    private SnapshotItem toItem(CustomSnapshot snapshot) {
        return new SnapshotItem(
                snapshot.getId(), snapshot.getLabel(), snapshot.getCreatedAt(), snapshot.getUpdatedAt());
    }
}
