package kr.co.marketry.domain.notification.controller;

import java.util.Map;
import kr.co.marketry.domain.notification.service.TelegramImageLegacySettingService;
import kr.co.marketry.domain.notification.service.TelegramImageSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/telegram-image-setting")
@RequiredArgsConstructor
public class AdminTelegramImageSettingController {
    private final TelegramImageSettingService service;
    private final TelegramImageLegacySettingService legacyService;

    @GetMapping("/legacy")
    public ResponseEntity<TelegramImageLegacySettingService.Response> legacy() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(legacyService.find());
    }

    @GetMapping
    public ResponseEntity<TelegramImageSettingService.Response> find() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.find());
    }

    @PutMapping
    public ResponseEntity<TelegramImageSettingService.Response> update(@RequestBody Map<String, Object> values) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(values));
    }
}
