package dev.eolmae.marketry.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.DefaultPreferenceRepository;
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
            "heatmap.profile.marketry.tierRangeMinIndex",
            "heatmap.profile.marketry.tierRangeMaxIndex",
            "heatmap.profile.marketry.tierRangeMinValue",
            "heatmap.profile.marketry.tierRangeMaxValue",
            "heatmap.profile.marketry.sectorFilterEnabled",
            "heatmap.profile.marketry.sectorLevelEnabled",
            "heatmap.profile.marketry.selectedMaxDepth",
            "heatmap.profile.marketry.activeDepthMetric",
            "heatmap.profile.marketry.depthMetricEnabled",
            "heatmap.profile.marketry.depthMetricMinIndex",
            "heatmap.profile.marketry.depthMetricMaxIndex",
            "heatmap.boxSizeMarketCapRatio",
            "heatmap.profile.marketry.boxSizeMarketCapRatio",
            "heatmap.stockLabelModeIndex",
            "heatmap.profile.marketry.stockLabelModeIndex",
            "heatmap.stockLabelEnabled",
            "heatmap.profile.marketry.stockLabelEnabled",
            "heatmap.decimalPlacesIndex",
            "heatmap.profile.marketry.decimalPlacesIndex",
            "heatmap.strongIndustryColor",
            "heatmap.profile.marketry.strongIndustryColor",
            "heatmap.profile.marketry.topPickDepth",
            "heatmap.profile.marketry.topPickCount",
            "heatmap.profile.marketry.topPickEnabled",
            "heatmap.profile.marketry.excludedSectorKeys");
    private final UserPreferenceRepository preferences;
    private final DefaultPreferenceRepository defaultPreferences;
    private final CustomSectorRepository sectors;
    private final CustomValueTierThresholdService tiers;
    private final MarketryProperties properties;
    private final ObjectMapper objectMapper;

    public record Response(
            Map<String, Object> preferences, List<List<Object>> excludedSectors, List<CustomValueTierItem> tiers) {}

    @Transactional(readOnly = true)
    public Response find() {
        return find(false);
    }

    /** 텔레그램 렌더러에는 소유자 개인값을 섞지 않고 MARKETRY 기본값만 제공한다. */
    @Transactional(readOnly = true)
    public Response findForCapture() {
        return find(true);
    }

    private Response find(boolean useDefaults) {
        Long ownerId = properties.ownerUserId();
        Long publishedId = properties.publishedUserId();
        Map<String, Object> stored = useDefaults || ownerId == null
                ? Map.of()
                : preferences
                        .findById(ownerId)
                        .map(preference -> parse(preference.getPayload()))
                        .orElseGet(Map::of);
        Map<String, Object> defaultPayload = useDefaults
                ? defaultPreferences
                        .findById(1L)
                        .map(preference -> parse(preference.getPayload()))
                        .orElseGet(Map::of)
                : Map.of();
        Map<String, Object> marketryDefaults = defaultPayload.get("marketry") instanceof Map<?, ?> map
                ? objectMapper.convertValue(map, new TypeReference<>() {})
                : Map.of();
        List<CustomSector> all = publishedId == null ? List.of() : sectors.findAllByUserId(publishedId);
        Map<Long, CustomSector> byId = new HashMap<>();
        all.forEach(sector -> byId.put(sector.getId(), sector));
        Object configuredKeys = useDefaults
                ? marketryDefaults.get("excludedSectorKeys")
                : stored.get("heatmap.profile.marketry.excludedSectorKeys");
        Set<String> excludedKeys = configuredKeys instanceof List<?> list
                ? list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .collect(java.util.stream.Collectors.toSet())
                : Set.of();
        List<List<Object>> excluded = all.stream()
                .filter(sector -> excludedKeys.contains(sector.getStableKey().toString())
                        || (!(configuredKeys instanceof List<?>) && sector.isExcluded()))
                .map(sector -> {
                    List<String> names = new ArrayList<>();
                    CustomSector current = sector;
                    while (current != null && names.size() <= all.size()) {
                        names.add(current.getName());
                        current = byId.get(current.getParentId());
                    }
                    Collections.reverse(names);
                    return List.<Object>of(sector.getStableKey().toString(), String.join(" > ", names));
                })
                .toList();
        List<CustomValueTierItem> ownerTiers =
                ownerId == null ? tiers.getDefaultValueTiers() : tiers.getValueTiers(ownerId);
        Map<String, Object> imagePreferences = new HashMap<>();
        if (useDefaults) {
            marketryDefaults.forEach((key, value) -> {
                String profileKey = "heatmap.profile.marketry." + key;
                if (!IMAGE_KEYS.contains(profileKey) || "excludedSectorKeys".equals(key)) return;
                imagePreferences.put("heatmap." + key, value);
            });
        } else {
            IMAGE_KEYS.forEach(key -> {
                if (stored.containsKey(key))
                    imagePreferences.put(
                            key.startsWith("heatmap.profile.marketry.")
                                    ? "heatmap." + key.substring("heatmap.profile.marketry.".length())
                                    : key,
                            stored.get(key));
            });
        }
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
