package kr.co.marketry.domain.notification.controller;

import kr.co.marketry.domain.notification.dto.TelegramSendSettingRequest;
import kr.co.marketry.domain.notification.dto.TelegramSendSettingResponse;
import kr.co.marketry.domain.notification.service.TelegramReportDispatcher;
import kr.co.marketry.domain.notification.service.TelegramSendSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
    public ResponseEntity<?> sendJapanOnce() {
        return switch (telegramReportDispatcher.sendLatestJapanOnce()) {
            case SENT -> ResponseEntity.noContent().build();
            case NO_QUOTES -> japanSendFailure(HttpStatus.CONFLICT, "NO_JAPAN_QUOTES", "발송할 일본 시세가 없습니다.");
            case QUOTE_LOOKUP_FAILED ->
                japanSendFailure(HttpStatus.INTERNAL_SERVER_ERROR, "JAPAN_QUOTE_LOOKUP_FAILED", "일본 시세 조회에 실패했습니다.");
            case MAP_CAPTURE_FAILED ->
                japanSendFailure(HttpStatus.BAD_GATEWAY, "JAPAN_MAP_CAPTURE_FAILED", "일본 지도 캡처에 실패했습니다.");
            case TELEGRAM_SEND_FAILED ->
                japanSendFailure(HttpStatus.BAD_GATEWAY, "JAPAN_TELEGRAM_SEND_FAILED", "일본 지도 텔레그램 전송에 실패했습니다.");
        };
    }

    private ResponseEntity<ProblemDetail> japanSendFailure(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("errorCode", errorCode);
        return ResponseEntity.status(status).body(problemDetail);
    }
}
