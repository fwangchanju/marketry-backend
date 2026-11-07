package kr.co.marketry.domain.notification.controller;

import kr.co.marketry.domain.notification.service.TelegramImageLegacySettingService;
import kr.co.marketry.domain.notification.service.TelegramImageSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/map/telegram-image-setting")
@RequiredArgsConstructor
public class CaptureTelegramImageSettingController {
    private final TelegramImageLegacySettingService service;

    // X-Capture-Token은 인증 필터가 검증하며 읽기 요청에만 허용한다.
    @GetMapping
    public ResponseEntity<TelegramImageSettingService.Response> find(
            @RequestHeader(value = "X-Capture-Token", required = false) String token) {
        var legacy = token == null ? null : service.findForCapture();
        TelegramImageSettingService.Response response = token == null || legacy == null
                ? new TelegramImageSettingService.Response(null, null)
                : new TelegramImageSettingService.Response(
                        java.util.Map.of(
                                "preferences", legacy.preferences(), "excludedSectors", legacy.excludedSectors()),
                        null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
