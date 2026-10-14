package dev.eolmae.marketry.domain.view.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TreemapResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void 분류_갱신_시각은_taxonomyUpdatedAt_이름으로만_내려_보낸다() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 8, 15, 55, 0);
        TreemapResponse response = new TreemapResponse(null, List.of(), null, updatedAt, null);

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(json.has("taxonomyUpdatedAt")).isTrue();
        assertThat(json.has("classificationUpdatedAt")).isFalse();
    }

    @Test
    void 갱신_시각이_없으면_taxonomyUpdatedAt이_null이다() {
        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(TreemapResponse.empty()));

        assertThat(json.get("taxonomyUpdatedAt").isNull()).isTrue();
    }
}
