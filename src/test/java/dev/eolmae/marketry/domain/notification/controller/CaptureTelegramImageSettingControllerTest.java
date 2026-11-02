package dev.eolmae.marketry.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.notification.service.TelegramImageLegacySettingService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptureTelegramImageSettingControllerTest {
    private final TelegramImageLegacySettingService service = mock(TelegramImageLegacySettingService.class);
    private final CaptureTelegramImageSettingController controller = new CaptureTelegramImageSettingController(service);

    @Test
    void 일반_화면에는_발송_설정이_노출되지_않는다() {
        assertThat(controller.find(null).getBody().values()).isNull();
        verify(service, never()).find();
    }

    @Test
    void 검증된_캡처_토큰_요청은_저장값을_받는다() {
        TelegramImageLegacySettingService.Response setting = new TelegramImageLegacySettingService.Response(
                Map.of("heatmap.boxSizeMarketCapRatio", 70), java.util.List.of(), java.util.List.of());
        when(service.find()).thenReturn(setting);
        assertThat(controller.find("capture-token").getBody().values())
                .isEqualTo(Map.of("preferences", setting.preferences(), "excludedSectors", setting.excludedSectors()));
    }
}
