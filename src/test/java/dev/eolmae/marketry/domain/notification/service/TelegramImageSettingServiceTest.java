package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.notification.entity.TelegramImageSetting;
import dev.eolmae.marketry.domain.notification.repository.TelegramImageSettingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelegramImageSettingServiceTest {
    private final TelegramImageSettingRepository repository = mock(TelegramImageSettingRepository.class);
    private final TelegramImageSettingService service = new TelegramImageSettingService(repository, new ObjectMapper());

    @Test
    void 저장하지_않았으면_발송값이_없다() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.find().values()).isNull();
    }

    @Test
    void 현재값과_제외_업종을_저장하고_그대로_반환한다() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.save(any(TelegramImageSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> value = Map.of(
                "preferences",
                Map.of("heatmap.boxSizeMarketCapRatio", 45),
                "excludedSectors",
                List.of(List.of(3, "금융")));
        assertThat(service.update(value).values()).isEqualTo(value);
    }

    @Test
    void 설정값이_빠지면_저장하지_않는다() {
        assertThatThrownBy(() -> service.update(Map.of())).isInstanceOf(BadRequestException.class);
    }
}
