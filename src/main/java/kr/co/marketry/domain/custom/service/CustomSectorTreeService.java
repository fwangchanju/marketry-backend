package kr.co.marketry.domain.custom.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.custom.dto.CustomSnapshotPayload;
import kr.co.marketry.domain.custom.entity.CustomScaleThreshold;
import kr.co.marketry.domain.custom.entity.CustomSector;
import kr.co.marketry.domain.custom.entity.CustomStockAlias;
import kr.co.marketry.domain.custom.entity.CustomStockSector;
import kr.co.marketry.domain.custom.entity.CustomValueTierThreshold;
import kr.co.marketry.domain.custom.entity.UserPreference;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.repository.CustomScaleThresholdRepository;
import kr.co.marketry.domain.custom.repository.CustomSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomStockAliasRepository;
import kr.co.marketry.domain.custom.repository.CustomStockSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import kr.co.marketry.domain.custom.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 커스텀 데이터 전체를 저장하고 복원한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class CustomSectorTreeService {

    private static final int SNAPSHOT_VERSION = 3;
    private static final int LEGACY_SNAPSHOT_VERSION = 2;

    private final CustomSectorRepository customSectorRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final CustomStockAliasRepository customStockAliasRepository;
    private final CustomScaleThresholdRepository customScaleThresholdRepository;
    private final CustomValueTierThresholdRepository customValueTierThresholdRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ClassifiableStocks classifiableStocks;
    private final ProtectedSectorService protectedSectorService;
    private final SuspendedStockClassificationService suspendedStockClassificationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String serializeCurrentSnapshot(Long userId, ClassificationMarket market) {
        List<CustomSnapshotPayload.Sector> sectors =
                customSectorRepository.findAllByUserIdAndMarket(userId, market).stream()
                        .sorted(java.util.Comparator.comparing(CustomSector::getId))
                        .map(sector -> new CustomSnapshotPayload.Sector(
                                sector.getId(),
                                sector.getParentId(),
                                sector.getName(),
                                sector.getDepth(),
                                sector.isExcluded(),
                                sector.getStableKey()))
                        .toList();
        List<CustomSnapshotPayload.StockAssignment> assignments =
                customStockSectorRepository.findAllByIdUserIdAndMarket(userId, market).stream()
                        .sorted(java.util.Comparator.comparing(CustomStockSector::getStockCode))
                        .map(assignment -> new CustomSnapshotPayload.StockAssignment(
                                assignment.getStockCode(), assignment.getSectorId()))
                        .toList();
        if (!market.keepsSettingsInSnapshot()) {
            return toJson(new CustomSnapshotPayload(
                    SNAPSHOT_VERSION, sectors, assignments, List.of(), List.of(), List.of(), Map.of()));
        }
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
                    || !isSupportedSnapshotVersion(root.path("snapshotVersion").asInt())) {
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
                    && isSupportedSnapshotVersion(root.path("snapshotVersion").asInt());
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    public void restore(String snapshotJson, Long userId, ClassificationMarket market, Long snapshotId) {
        CustomSnapshotPayload snapshot = parseSnapshot(snapshotJson);
        validateSnapshot(snapshot, market);

        customStockSectorRepository.deleteAllOfUserInMarket(userId, market);
        customSectorRepository.deleteAll(customSectorRepository.findAllByUserIdAndMarket(userId, market));
        if (market.keepsSettingsInSnapshot()) {
            customStockAliasRepository.deleteAll(customStockAliasRepository.findAllByIdUserId(userId));
            customScaleThresholdRepository.deleteAllByUserId(userId);
            customValueTierThresholdRepository.deleteAllByUserId(userId);
        }
        // IDENTITY 전략은 save() 즉시 INSERT하는데 위 delete들은 flush 전까지 큐에만 쌓여있어,
        // flush 없이 restoreSectors를 부르면 같은 (user_id, name) 등 유니크 제약을 건드려 충돌한다.
        customSectorRepository.flush();

        Map<Long, CustomSector> sectorBySnapshotId = restoreSectors(snapshot.sectors(), userId, market, snapshotId);
        // 옛 스냅샷에는 보호 업종이 없을 수 있어서, 복원한 트리에 없는 보호 업종을 채운다.
        protectedSectorService.ensureForUser(userId, market);
        List<CustomStockSector> assignments = snapshot.assignments().stream()
                .map(assignment -> CustomStockSector.create(
                        userId,
                        market,
                        assignment.stockCode(),
                        sectorBySnapshotId.get(assignment.sectorId()).getId()))
                .toList();
        customStockSectorRepository.saveAll(assignments);
        if (market.keepsSettingsInSnapshot()) {
            restoreSettings(snapshot, userId);
        }
        // 복원한 배정 중 지금 거래 정지인 종목은 거래 정지 업종으로 맞춘다(MARKETRY 발행 사용자는 고정본이라 제외된다).
        if (market.locksSuspendedStocks()) {
            suspendedStockClassificationService.applyCurrentExclusionsToUser(userId);
        }
    }

    private void restoreSettings(CustomSnapshotPayload snapshot, Long userId) {
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
            List<CustomSnapshotPayload.Sector> sectors, Long userId, ClassificationMarket market, Long snapshotId) {
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
                UUID stableKey = sector.stableKey() == null ? UUID.randomUUID() : sector.stableKey();
                CustomSector entity = sector.parentId() == null
                        ? CustomSector.createParent(userId, market, sector.name(), stableKey)
                        : CustomSector.createChild(userId, sector.name(), restored.get(sector.parentId()), stableKey);
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

    private static boolean isSupportedSnapshotVersion(int version) {
        return version == SNAPSHOT_VERSION || version == LEGACY_SNAPSHOT_VERSION;
    }

    private void validateSnapshot(CustomSnapshotPayload snapshot, ClassificationMarket market) {
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
        if (market.keepsSettingsInSnapshot()) {
            stockCodes.addAll(snapshot.aliases().stream()
                    .map(CustomSnapshotPayload.StockAlias::stockCode)
                    .toList());
        }
        if (hasInvalidParent || hasInvalidAssignment || !classifiableStocks.existsAll(market, stockCodes)) {
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
