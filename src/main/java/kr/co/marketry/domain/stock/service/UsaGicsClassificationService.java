package kr.co.marketry.domain.stock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService.Classification;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 미국 S&P 500 종목의 GICS 분류를 읽기 전용으로 돌려준다. 값은 저장소의 파일(usa-gics-classifications.json)에서 읽으며,
 * 운영 화면에서 고친 분류(overseas_stock_classification)와 따로 둔다.
 */
@Service
public class UsaGicsClassificationService {
    private static final String RESOURCE = "usa-gics-classifications.json";

    private final Map<String, Classification> classifications;

    public UsaGicsClassificationService(ObjectMapper objectMapper) {
        this.classifications = load(objectMapper);
    }

    public Map<String, Classification> findAll() {
        return classifications;
    }

    private static Map<String, Classification> load(ObjectMapper objectMapper) {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, Map<String, String>> raw = objectMapper.readValue(stream, new TypeReference<>() {});
            return raw.entrySet().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            Map.Entry::getKey,
                            entry -> new Classification(
                                    entry.getValue().get("sector"),
                                    entry.getValue().get("industry"),
                                    entry.getValue().get("subIndustry"))));
        } catch (IOException e) {
            throw new IllegalStateException("GICS 분류 파일을 읽지 못했습니다: " + RESOURCE, e);
        }
    }
}
