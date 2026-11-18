package kr.co.marketry.domain.stock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 미국 S&P 500 종목의 GICS 4단계 분류(섹터·산업그룹·산업·하위 산업)를 읽기 전용으로 돌려준다.
 * 값은 저장소의 파일(usa-gics-classifications.json)에서 읽으며, 운영 화면에서 고친 분류와 따로 둔다.
 */
@Service
public class UsaGicsClassificationService {
    private static final String RESOURCE = "usa-gics-classifications.json";

    public record Gics(String sector, String industryGroup, String industry, String subIndustry, String gicsCode) {}

    private final Map<String, Gics> classifications;

    public UsaGicsClassificationService(ObjectMapper objectMapper) {
        this.classifications = load(objectMapper);
    }

    public Map<String, Gics> findAll() {
        return classifications;
    }

    private static Map<String, Gics> load(ObjectMapper objectMapper) {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, Gics> raw = objectMapper.readValue(stream, new TypeReference<>() {});
            return Map.copyOf(raw);
        } catch (IOException e) {
            throw new IllegalStateException("GICS 분류 파일을 읽지 못했습니다: " + RESOURCE, e);
        }
    }
}
