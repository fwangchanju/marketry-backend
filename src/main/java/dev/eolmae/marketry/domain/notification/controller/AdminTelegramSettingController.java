package dev.eolmae.marketry.domain.notification.controller;

import dev.eolmae.marketry.domain.notification.dto.TelegramSendSettingRequest;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendSettingResponse;
import dev.eolmae.marketry.domain.notification.service.TelegramSendSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/telegram-settings")
@RequiredArgsConstructor
public class AdminTelegramSettingController {
    private final TelegramSendSettingService telegramSendSettingService;

    @GetMapping
    public ResponseEntity<TelegramSendSettingResponse> find() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(telegramSendSettingService.find());
    }

    @PutMapping
    public ResponseEntity<TelegramSendSettingResponse> update(@RequestBody TelegramSendSettingRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(telegramSendSettingService.update(request));
    }
}
