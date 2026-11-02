package dev.eolmae.marketry.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.notification.service.TelegramImageSettingService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptureTelegramImageSettingControllerTest {
    private final TelegramImageSettingService service = mock(TelegramImageSettingService.class);
    private final CaptureTelegramImageSettingController controller = new CaptureTelegramImageSettingController(service);

    @Test
    void 일반_화면에는_발송_설정이_노출되지_않는다() {
        assertThat(controller.find(null).getBody().values()).isNull();
        verify(service, never()).find();
    }

    @Test
    void 검증된_캡처_토큰_요청은_저장값을_받는다() {
        TelegramImageSettingService.Response setting =
                new TelegramImageSettingService.Response(Map.of("preferences", Map.of()), null);
        when(service.find()).thenReturn(setting);
        assertThat(controller.find("capture-token").getBody()).isEqualTo(setting);
    }
}
