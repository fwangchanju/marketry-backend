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

    // 캡처·전송 실패는 502가 아니라 424로 돌려준다 — 앞단(Cloudflare)이 502는 응답 내용을 버리고 자기 오류 페이지로 바꿔서
    // errorCode가 화면에 닿지 않는다.
    @PostMapping("/send-once")
    public ResponseEntity<?> sendOnce() {
        // 국내 테스트는 국내 지도 한 장만 보낸다. 실패하면 어느 단계에서 막혔는지 errorCode로 알린다.
        return switch (telegramReportDispatcher.sendLatestKoreaOnce()) {
            case SENT -> ResponseEntity.noContent().build();
            case NO_COMMON_SNAPSHOT ->
                sendFailure(HttpStatus.CONFLICT, "KOREA_NO_COMMON_SNAPSHOT", "발송할 국내 공통 스냅샷이 없습니다.");
            case MAP_CAPTURE_FAILED ->
                sendFailure(HttpStatus.FAILED_DEPENDENCY, "KOREA_MAP_CAPTURE_FAILED", "국내 지도 캡처에 실패했습니다.");
            case TELEGRAM_SEND_FAILED ->
                sendFailure(HttpStatus.FAILED_DEPENDENCY, "KOREA_TELEGRAM_SEND_FAILED", "국내 지도 텔레그램 전송에 실패했습니다.");
        };
    }

    @PostMapping("/send-japan-once")
    public ResponseEntity<?> sendJapanOnce() {
        return switch (telegramReportDispatcher.sendLatestJapanOnce()) {
            case SENT -> ResponseEntity.noContent().build();
            case NO_QUOTES -> sendFailure(HttpStatus.CONFLICT, "NO_JAPAN_QUOTES", "발송할 일본 시세가 없습니다.");
            case QUOTE_LOOKUP_FAILED ->
                sendFailure(HttpStatus.INTERNAL_SERVER_ERROR, "JAPAN_QUOTE_LOOKUP_FAILED", "일본 시세 조회에 실패했습니다.");
            case MAP_CAPTURE_FAILED ->
                sendFailure(HttpStatus.FAILED_DEPENDENCY, "JAPAN_MAP_CAPTURE_FAILED", "일본 지도 캡처에 실패했습니다.");
            case TELEGRAM_SEND_FAILED ->
                sendFailure(HttpStatus.FAILED_DEPENDENCY, "JAPAN_TELEGRAM_SEND_FAILED", "일본 지도 텔레그램 전송에 실패했습니다.");
        };
    }

    private ResponseEntity<ProblemDetail> sendFailure(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("errorCode", errorCode);
        return ResponseEntity.status(status).body(problemDetail);
    }
}
