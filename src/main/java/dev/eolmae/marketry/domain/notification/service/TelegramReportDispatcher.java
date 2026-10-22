package dev.eolmae.marketry.domain.notification.service;

import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 텔레그램 발송 진입점을 하나로 묶는다. CollectionScheduler와 수동 테스트가 같은 메서드를 불러서,
// 발송기 시그니처나 호출 순서가 바뀌어도 이 안에서만 바뀌고 양쪽 호출부는 그대로다. 판정 로직은 갖지
// 않는 얇은 층 — 발송 여부(시각 게이팅 등)는 여전히 호출부 책임이다.
@Component
@RequiredArgsConstructor
public class TelegramReportDispatcher {

    private final SectorTelegramReportSender sectorTelegramReportSender;
    private final TreemapAlbumReportSender treemapAlbumReportSender;
    private final HomeReportSender homeReportSender;

    // 정기 발송은 홈 화면 한 장이다(관리자 메시지 페이지). 아래 sendSector·sendMap은 정기 발송에서 빠졌고 수동 테스트만 쓴다.
    // TODO 홈 발송이 운영에서 확인되면 sendSector·sendMap·sendAll과 SectorTelegramReportSender·TreemapAlbumReportSender·dueForMap을
    // 정리한다.
    public void sendHome(LocalDateTime dataTime) {
        homeReportSender.send(dataTime);
    }

    public void sendSector(LocalDateTime dataTime) {
        sectorTelegramReportSender.send(dataTime);
    }

    public void sendMap(LocalDateTime dataTime) {
        treemapAlbumReportSender.send(dataTime);
    }

    // 섹터 → 맵 순. 스케줄러가 둘 다 due일 때와 같은 순서다.
    public void sendAll(LocalDateTime dataTime) {
        sendSector(dataTime);
        sendMap(dataTime);
    }

    public void sendMapSinglePage(LocalDateTime dataTime, MarketQuery query) {
        treemapAlbumReportSender.sendMapSinglePage(dataTime, query);
    }
}
