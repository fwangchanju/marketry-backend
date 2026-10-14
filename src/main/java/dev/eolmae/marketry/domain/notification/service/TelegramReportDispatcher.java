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
