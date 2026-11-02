package dev.eolmae.marketry.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.UserPreferenceRepository;
import dev.eolmae.marketry.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 발송값이 없을 때 렌더러가 사용하던 소유자 설정과 MARKETRY 제외 업종을 읽는다. */
@Service
@RequiredArgsConstructor
public class TelegramImageLegacySettingService {
    private static final Set<String> IMAGE_KEYS = Set.of(
            "heatmap.tierRangeMinIndex",
            "heatmap.tierRangeMaxIndex",
            "heatmap.tierRangeMinValue",
            "heatmap.tierRangeMaxValue",
            "heatmap.sectorFilterEnabled",
            "heatmap.sectorLevelEnabled",
            "heatmap.selectedMaxDepth",
            "heatmap.activeDepthMetric",
            "heatmap.depthMetricEnabled",
            "heatmap.depthMetricMinIndex",
            "heatmap.depthMetricMaxIndex",
            "heatmap.boxSizeMarketCapRatio",
            "heatmap.stockLabelModeIndex",
            "heatmap.stockLabelEnabled",
            "heatmap.decimalPlacesIndex",
            "heatmap.strongIndustryColor",
            "heatmap.topPickDepth",
            "heatmap.topPickCount",
            "heatmap.topPickEnabled");
    private final UserPreferenceRepository preferences;
    private final CustomSectorRepository sectors;
    private final CustomValueTierThresholdService tiers;
    private final MarketryProperties properties;
    private final ObjectMapper objectMapper;

    public record Response(
            Map<String, Object> preferences, List<List<Object>> excludedSectors, List<CustomValueTierItem> tiers) {}

    @Transactional(readOnly = true)
    public Response find() {
        Long ownerId = properties.ownerUserId();
        Long publishedId = properties.publishedUserId();
        Map<String, Object> stored = ownerId == null
                ? Map.of()
                : preferences
                        .findById(ownerId)
                        .map(preference -> parse(preference.getPayload()))
                        .orElseGet(Map::of);
        List<CustomSector> all = publishedId == null ? List.of() : sectors.findAllByUserId(publishedId);
        Map<Long, CustomSector> byId = new HashMap<>();
        all.forEach(sector -> byId.put(sector.getId(), sector));
        List<List<Object>> excluded = all.stream()
                .filter(CustomSector::isExcluded)
                .map(sector -> {
                    List<String> names = new ArrayList<>();
                    CustomSector current = sector;
                    while (current != null && names.size() <= all.size()) {
                        names.add(current.getName());
                        current = byId.get(current.getParentId());
                    }
                    Collections.reverse(names);
                    return List.<Object>of(sector.getId(), String.join(" > ", names));
                })
                .toList();
        List<CustomValueTierItem> ownerTiers =
                ownerId == null ? tiers.getDefaultValueTiers() : tiers.getValueTiers(ownerId);
        Map<String, Object> imagePreferences = new HashMap<>();
        IMAGE_KEYS.forEach(key -> {
            if (stored.containsKey(key)) imagePreferences.put(key, stored.get(key));
        });
        return new Response(imagePreferences, excluded, ownerTiers);
    }

    private Map<String, Object> parse(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }
}
