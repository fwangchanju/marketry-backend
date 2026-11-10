package kr.co.marketry.domain.notification.controller;

import kr.co.marketry.domain.notification.dto.TelegramSendSettingRequest;
import kr.co.marketry.domain.notification.dto.TelegramSendSettingResponse;
import kr.co.marketry.domain.notification.service.TelegramReportDispatcher;
import kr.co.marketry.domain.notification.service.TelegramSendSettingService;
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
        // 국내 테스트는 국내 지도 한 장만 보낸다.
        boolean sent = telegramReportDispatcher.sendLatestKorea();
        return sent
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @PostMapping("/send-japan-once")
    public ResponseEntity<Void> sendJapanOnce() {
        boolean sent = telegramReportDispatcher.sendLatestJapan();
        return sent
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
