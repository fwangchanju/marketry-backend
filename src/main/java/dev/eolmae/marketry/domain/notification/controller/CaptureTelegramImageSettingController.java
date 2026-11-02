package dev.eolmae.marketry.domain.notification.controller;

import dev.eolmae.marketry.domain.notification.service.TelegramImageSettingService;
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
    private final TelegramImageSettingService service;

    // X-Capture-Token은 인증 필터가 검증하며 읽기 요청에만 허용한다.
    @GetMapping
    public ResponseEntity<TelegramImageSettingService.Response> find(
            @RequestHeader(value = "X-Capture-Token", required = false) String token) {
        TelegramImageSettingService.Response response =
                token == null ? new TelegramImageSettingService.Response(null, null) : service.find();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
