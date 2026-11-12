package kr.co.marketry.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.custom.entity.DefaultPreference;
import kr.co.marketry.domain.custom.repository.DefaultPreferenceRepository;
import org.junit.jupiter.api.Test;

class DefaultPreferenceServiceTest {
    private final DefaultPreferenceRepository repository = mock(DefaultPreferenceRepository.class);
    private final DefaultPreferenceService service = new DefaultPreferenceService(repository, new ObjectMapper());

    @Test
    void MARKETRY와_거래소_기본값을_각각_저장한다() {
        Map<String, Object> payload = Map.of(
                "marketry", Map.of("boxSizeMarketCapRatio", 60, "excludedSectorKeys", java.util.List.of("key-1")),
                "exchange", Map.of("boxSizeMarketCapRatio", 35, "excludedSectorNames", java.util.List.of("금융 > 은행")));
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.save(any(DefaultPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> saved = service.update(payload);

        assertThat(saved).isEqualTo(payload);
    }

    @Test
    void 거래소_기본값에_MARKETRY_제외_키를_넣을_수_없다() {
        Map<String, Object> payload = Map.of("exchange", Map.of("excludedSectorKeys", java.util.List.of("key-1")));

        assertThatThrownBy(() -> service.update(payload)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 박스_크기_비율은_0에서_100_사이의_정수만_저장한다() {
        Map<String, Object> payload = Map.of("marketry", Map.of("boxSizeMarketCapRatio", 101));

        assertThatThrownBy(() -> service.update(payload)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 일본_기본값은_일본_지도_설정_이름으로_저장한다() {
        Map<String, Object> japan =
                Map.of("topPickEnabled", true, "topPickDepth", 1, "topPickCount", 2, "maxDepth", 2, "decimalPlaces", 1);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.save(any(DefaultPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> saved = service.update(Map.of("japan", japan));

        assertThat(saved).isEqualTo(Map.of("japan", japan));
    }

    @Test
    void 일본_기본값에_한국_전용_키나_모르는_키를_넣을_수_없다() {
        assertThatThrownBy(() -> service.update(Map.of("japan", Map.of("excludedSectorKeys", java.util.List.of("k")))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(Map.of("japan", Map.of("unknown", 1))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(Map.of("japan", Map.of("topPickCount", 4))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 한국_기본값에는_일본_전용_키를_넣을_수_없다() {
        assertThatThrownBy(() -> service.update(Map.of("marketry", Map.of("maxDepth", 2))))
                .isInstanceOf(BadRequestException.class);
    }
}
