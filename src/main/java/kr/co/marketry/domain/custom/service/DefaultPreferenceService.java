package kr.co.marketry.domain.custom.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.custom.entity.DefaultPreference;
import kr.co.marketry.domain.custom.repository.DefaultPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultPreferenceService {
    private static final Set<String> PROFILES = Set.of("marketry", "exchange");
    private static final Set<String> PROFILE_KEYS = Set.of(
            "avgChangeRateUseSimple",
            "boxSizeMarketCapRatio",
            "strongIndustryColor",
            "sectorFilterEnabled",
            "sectorLevelEnabled",
            "selectedMaxDepth",
            "activeDepthMetric",
            "depthMetricEnabled",
            "depthMetricMinIndex",
            "depthMetricMaxIndex",
            "topPickDepth",
            "topPickCount",
            "topPickEnabled",
            "stockLabelModeIndex",
            "stockLabelEnabled",
            "decimalPlacesIndex",
            "colorCustomOn",
            "tierRangeMinIndex",
            "tierRangeMaxIndex",
            "tierRangeMinValue",
            "tierRangeMaxValue",
            "excludedSectorKeys",
            "excludedSectorNames");
    private static final Set<String> BOOLEAN_KEYS = Set.of(
            "avgChangeRateUseSimple",
            "sectorFilterEnabled",
            "sectorLevelEnabled",
            "depthMetricEnabled",
            "topPickEnabled",
            "stockLabelEnabled",
            "colorCustomOn");
    private static final Set<String> LIST_KEYS = Set.of("excludedSectorKeys", "excludedSectorNames");
    private static final Set<String> METRICS =
            Set.of("weightedAvgChangeRate", "simpleAvgChangeRate", "upDownCount", "marketValue", "avgChangeRate");
    private final DefaultPreferenceRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> find() {
        return repository.findById(1L).map(value -> parse(value.getPayload())).orElseGet(Map::of);
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> payload) {
        if (payload == null || payload.keySet().stream().anyMatch(key -> !PROFILES.contains(key))) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
        for (String profile : payload.keySet()) validateProfile(profile, payload.get(profile));
        String json = write(payload);
        if (json.length() > 100_000) throw new BadRequestException(ErrorCode.INVALID_INPUT);
        DefaultPreference preference = repository.findById(1L).orElseGet(DefaultPreference::create);
        preference.update(json);
        repository.save(preference);
        return payload;
    }

    private void validateProfile(String profile, Object value) {
        if (!(value instanceof Map<?, ?> settings)) throw new BadRequestException(ErrorCode.INVALID_INPUT);
        for (Object key : settings.keySet()) {
            if (!(key instanceof String name) || !PROFILE_KEYS.contains(name)) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            if ((profile.equals("marketry") && name.equals("excludedSectorNames"))
                    || (profile.equals("exchange") && name.equals("excludedSectorKeys"))) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            validateValue(name, settings.get(name));
        }
    }

    private void validateValue(String key, Object value) {
        if (BOOLEAN_KEYS.contains(key)) {
            if (!(value instanceof Boolean)) throw new BadRequestException(ErrorCode.INVALID_INPUT);
            return;
        }
        if (LIST_KEYS.contains(key)) {
            if (!(value instanceof java.util.List<?> list)
                    || list.stream().anyMatch(item -> !(item instanceof String text) || text.isBlank())) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            return;
        }
        if (key.equals("strongIndustryColor")) {
            if (!(value instanceof String color) || !color.matches("#[0-9a-fA-F]{6}")) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            return;
        }
        if (key.equals("activeDepthMetric")) {
            if (value != null && (!(value instanceof String metric) || !METRICS.contains(metric))) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            return;
        }
        if (key.equals("tierRangeMaxValue")) {
            if (value != null && !isNumberInRange(value, 0, 1_000_000_000_000_000d, false)) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            return;
        }
        if (key.equals("selectedMaxDepth")) {
            if (value != null && !isNumberInRange(value, 1, 3, true)) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            return;
        }
        double min =
                switch (key) {
                    case "tierRangeMinIndex", "tierRangeMaxIndex" -> -1;
                    case "tierRangeMinValue",
                            "boxSizeMarketCapRatio",
                            "depthMetricMinIndex",
                            "depthMetricMaxIndex",
                            "topPickDepth",
                            "topPickCount",
                            "stockLabelModeIndex",
                            "decimalPlacesIndex" -> 0;
                    default -> Double.NaN;
                };
        double max =
                switch (key) {
                    case "tierRangeMinIndex", "tierRangeMaxIndex" -> 100;
                    case "tierRangeMinValue" -> 1_000_000_000_000_000d;
                    case "boxSizeMarketCapRatio" -> 100;
                    case "depthMetricMinIndex", "depthMetricMaxIndex", "topPickDepth" -> 2;
                    case "topPickCount" -> 3;
                    case "stockLabelModeIndex" -> 3;
                    case "decimalPlacesIndex" -> 2;
                    default -> Double.NaN;
                };
        if (Double.isNaN(min) || !isNumberInRange(value, min, max, true)) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isNumberInRange(Object value, double min, double max, boolean integer) {
        if (!(value instanceof Number number)) return false;
        double numericValue = number.doubleValue();
        return Double.isFinite(numericValue)
                && numericValue >= min
                && numericValue <= max
                && (!integer || numericValue == Math.rint(numericValue));
    }

    private Map<String, Object> parse(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    private String write(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
