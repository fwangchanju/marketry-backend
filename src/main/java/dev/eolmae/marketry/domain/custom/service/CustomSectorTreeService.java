package dev.eolmae.marketry.domain.custom.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.custom.dto.CustomSnapshotPayload;
import dev.eolmae.marketry.domain.custom.entity.CustomScaleThreshold;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.CustomStockAlias;
import dev.eolmae.marketry.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketry.domain.custom.entity.UserPreference;
import dev.eolmae.marketry.domain.custom.repository.CustomScaleThresholdRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockAliasRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketry.domain.custom.repository.UserPreferenceRepository;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.repository.StockInfoRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 커스텀 데이터 전체를 저장하고 복원한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class CustomSectorTreeService {

    private static final int SNAPSHOT_VERSION = 2;

    private final CustomSectorRepository customSectorRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final CustomStockAliasRepository customStockAliasRepository;
    private final CustomScaleThresholdRepository customScaleThresholdRepository;
    private final CustomValueTierThresholdRepository customValueTierThresholdRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final ProtectedSectorService protectedSectorService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String serializeCurrentSnapshot(Long userId) {
        List<CustomSnapshotPayload.Sector> sectors = customSectorRepository.findAllByUserId(userId).stream()
                .sorted(java.util.Comparator.comparing(CustomSector::getId))
                .map(sector -> new CustomSnapshotPayload.Sector(
                        sector.getId(), sector.getParentId(), sector.getName(), sector.getDepth(), sector.isExcluded()))
                .toList();
        List<CustomSnapshotPayload.StockAssignment> assignments =
                customStockSectorRepository.findAllByIdUserId(userId).stream()
                        .sorted(java.util.Comparator.comparing(CustomStockSector::getStockCode))
                        .map(assignment -> new CustomSnapshotPayload.StockAssignment(
                                assignment.getStockCode(), assignment.getSectorId()))
                        .toList();
        List<CustomSnapshotPayload.StockAlias> aliases = customStockAliasRepository.findAllByIdUserId(userId).stream()
                .sorted(java.util.Comparator.comparing(CustomStockAlias::getStockCode))
                .map(alias -> new CustomSnapshotPayload.StockAlias(alias.getStockCode(), alias.getAlias()))
                .toList();
        List<CustomSnapshotPayload.ScaleThreshold> scales =
                customScaleThresholdRepository.findAllByUserId(userId).stream()
                        .sorted(java.util.Comparator.comparing(CustomScaleThreshold::getThresholdPercent))
                        .map(threshold -> new CustomSnapshotPayload.ScaleThreshold(
                                threshold.getThresholdPercent(), threshold.getColor(), threshold.getColorLabel()))
                        .toList();
        List<CustomSnapshotPayload.ValueTierThreshold> tiers =
                customValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(userId).stream()
                        .map(threshold -> new CustomSnapshotPayload.ValueTierThreshold(
                                threshold.getLabel(), threshold.getThresholdValue(), threshold.isExcludedByDefault()))
                        .toList();
        Map<String, Object> preferences = userPreferenceRepository
                .findById(userId)
                .map(preference -> parsePreferences(preference.getPayload()))
                .orElseGet(Map::of);
        return toJson(
                new CustomSnapshotPayload(SNAPSHOT_VERSION, sectors, assignments, aliases, scales, tiers, preferences));
    }

    public CustomSnapshotPayload parseSnapshot(String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            if (root == null
                    || !root.isObject()
                    || !root.has("snapshotVersion")
                    || root.path("snapshotVersion").asInt() != SNAPSHOT_VERSION) {
                throw new BadRequestException(ErrorCode.SNAPSHOT_FORMAT_UNSUPPORTED);
            }
            return objectMapper.readValue(snapshotJson, new TypeReference<>() {});
        } catch (BadRequestException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.SECTOR_TREE_PARSE_FAILED, e);
        }
    }

    public boolean isCurrentSnapshotFormat(String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            return root != null
                    && root.isObject()
                    && root.path("snapshotVersion").asInt() == SNAPSHOT_VERSION;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    public void restore(String snapshotJson, Long userId, Long snapshotId) {
        CustomSnapshotPayload snapshot = parseSnapshot(snapshotJson);
        validateSnapshot(snapshot);

        customStockSectorRepository.deleteAllByIdUserId(userId);
        customStockAliasRepository.deleteAll(customStockAliasRepository.findAllByIdUserId(userId));
        customSectorRepository.deleteAll(customSectorRepository.findAllByUserId(userId));
        customScaleThresholdRepository.deleteAllByUserId(userId);
        customValueTierThresholdRepository.deleteAllByUserId(userId);
        // IDENTITY 전략은 save() 즉시 INSERT하는데 위 delete들은 flush 전까지 큐에만 쌓여있어,
        // flush 없이 restoreSectors를 부르면 같은 (user_id, name) 등 유니크 제약을 건드려 충돌한다.
        customSectorRepository.flush();

        Map<Long, CustomSector> sectorBySnapshotId = restoreSectors(snapshot.sectors(), userId, snapshotId);
        // 옛 스냅샷에는 보호 업종이 없을 수 있어서, 복원한 트리에 없는 보호 업종을 채운다.
        protectedSectorService.ensureForUser(userId);
        List<CustomStockSector> assignments = snapshot.assignments().stream()
                .map(assignment -> CustomStockSector.create(
                        userId,
                        assignment.stockCode(),
                        sectorBySnapshotId.get(assignment.sectorId()).getId()))
                .toList();
        customStockSectorRepository.saveAll(assignments);
        customStockAliasRepository.saveAll(snapshot.aliases().stream()
                .map(alias -> CustomStockAlias.create(userId, alias.stockCode(), alias.alias()))
                .toList());
        customScaleThresholdRepository.saveAll(snapshot.scaleThresholds().stream()
                .map(threshold -> CustomScaleThreshold.create(
                        userId, threshold.thresholdPercent(), threshold.color(), threshold.colorLabel()))
                .toList());
        customValueTierThresholdRepository.saveAll(snapshot.valueTierThresholds().stream()
                .map(threshold -> CustomValueTierThreshold.create(
                        userId, threshold.label(), threshold.thresholdValue(), threshold.excludedByDefault()))
                .toList());
        updatePreferences(userId, snapshot.preferences());
    }

    private Map<Long, CustomSector> restoreSectors(
            List<CustomSnapshotPayload.Sector> sectors, Long userId, Long snapshotId) {
        Map<Long, CustomSector> restored = new HashMap<>();
        Set<Long> pending =
                sectors.stream().map(CustomSnapshotPayload.Sector::id).collect(Collectors.toSet());
        while (!pending.isEmpty()) {
            List<CustomSnapshotPayload.Sector> ready = sectors.stream()
                    .filter(sector -> pending.contains(sector.id()))
                    .filter(sector -> sector.parentId() == null || restored.containsKey(sector.parentId()))
                    .toList();
            if (ready.isEmpty()) {
                throw new BadRequestException(ErrorCode.SECTOR_TREE_PARSE_FAILED);
            }
            for (CustomSnapshotPayload.Sector sector : ready) {
                CustomSector entity = sector.parentId() == null
                        ? CustomSector.createParent(userId, sector.name())
                        : CustomSector.createChild(userId, sector.name(), restored.get(sector.parentId()));
                if (sector.excluded()) {
                    entity.exclude();
                }
                entity.tagSnapshot(snapshotId);
                restored.put(sector.id(), customSectorRepository.save(entity));
                pending.remove(sector.id());
            }
        }
        return restored;
    }

    private void validateSnapshot(CustomSnapshotPayload snapshot) {
        Set<Long> sectorIds = snapshot.sectors().stream()
                .map(CustomSnapshotPayload.Sector::id)
                .collect(Collectors.toSet());
        boolean hasInvalidParent = snapshot.sectors().stream()
                .anyMatch(sector -> sector.parentId() != null && !sectorIds.contains(sector.parentId()));
        boolean hasInvalidAssignment =
                snapshot.assignments().stream().anyMatch(assignment -> !sectorIds.contains(assignment.sectorId()));
        Set<String> stockCodes = snapshot.assignments().stream()
                .map(CustomSnapshotPayload.StockAssignment::stockCode)
                .collect(Collectors.toSet());
        stockCodes.addAll(snapshot.aliases().stream()
                .map(CustomSnapshotPayload.StockAlias::stockCode)
                .toList());
        Set<String> existingStockCodes = stockInfoRepository.findAllById(stockCodes).stream()
                .map(StockInfo::getStockCode)
                .collect(Collectors.toSet());
        if (hasInvalidParent || hasInvalidAssignment || !existingStockCodes.containsAll(stockCodes)) {
            throw new BadRequestException(ErrorCode.SECTOR_TREE_PARSE_FAILED);
        }
    }

    private Map<String, Object> parsePreferences(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.SECTOR_TREE_PARSE_FAILED, e);
        }
    }

    private void updatePreferences(Long userId, Map<String, Object> preferences) {
        String payload = toJson(preferences == null ? Map.of() : preferences);
        UserPreference preference =
                userPreferenceRepository.findById(userId).orElseGet(() -> UserPreference.createEmpty(userId));
        preference.overwrite(payload);
        userPreferenceRepository.save(preference);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.SECTOR_TREE_SERIALIZE_FAILED, e);
        }
    }
}
