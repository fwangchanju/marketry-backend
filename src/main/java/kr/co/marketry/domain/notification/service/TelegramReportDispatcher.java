package kr.co.marketry.domain.notification.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 텔레그램 발송 진입점. CollectionScheduler가 이 메서드를 불러서, 발송기의 시그니처가 바뀌어도 이 안에서만 바뀌고 호출부는 그대로다.
// 판정 로직은 갖지 않는 얇은 층 — 발송 여부(시각 게이팅 등)는 여전히 호출부 책임이다.
// 정기 발송과 관리자 1회 발송은 시장 지도 이미지를 같은 경로로 보낸다.
@Component
@RequiredArgsConstructor
public class TelegramReportDispatcher {

    private final HomeReportSender homeReportSender;

    public boolean sendHome(LocalDateTime dataTime) {
        return sendHome(dataTime, false);
    }

    public boolean sendHome(LocalDateTime dataTime, boolean finalDailySend) {
        return homeReportSender.send(dataTime, finalDailySend);
    }

    public boolean sendLatestKorea() {
        return homeReportSender.sendLatestKorea();
    }

    public boolean sendLatestJapan() {
        return homeReportSender.sendLatestJapan();
    }
}
