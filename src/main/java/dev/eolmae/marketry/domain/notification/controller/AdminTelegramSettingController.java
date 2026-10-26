package dev.eolmae.marketry.domain.notification.controller;

import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendSettingRequest;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendSettingResponse;
import dev.eolmae.marketry.domain.notification.service.TelegramReportDispatcher;
import dev.eolmae.marketry.domain.notification.service.TelegramSendSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/telegram-settings")
@RequiredArgsConstructor
public class AdminTelegramSettingController {
    private final TelegramSendSettingService telegramSendSettingService;
    private final TelegramReportDispatcher telegramReportDispatcher;

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

    @PostMapping("/send-once")
    public ResponseEntity<Void> sendOnce() {
        // 관리자가 요청한 한 번의 발송은 스케줄 시각 판정만 건너뛰고 정기 리포트와 같은 전송 경로를 쓴다.
        boolean sent = telegramReportDispatcher.sendLatestHome(KstClock.now());
        return sent
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
