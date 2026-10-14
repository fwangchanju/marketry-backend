package dev.eolmae.marketry.domain.notification.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.domain.notification.client.TelegramClient;
import dev.eolmae.marketry.domain.notification.enums.RenderTarget;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketry.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.view.dto.TopSectorItem;
import dev.eolmae.marketry.domain.view.enums.AverageMode;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.TreemapQueryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 하루 세 번(telegram.map-send-times) 코스피+코스닥 맵을 앨범 하나로 묶어 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TreemapAlbumReportSender {

    private static final MarketQuery MAP_MARKETS = MarketQuery.ALL_STOCK;

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final SectorRankingTextBuilder sectorRankingTextBuilder;
    private final TreemapQueryService treemapQueryService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;

    public void send(LocalDateTime dataTime) {
        if (sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(MAP_MARKETS.toMarkets())
                .filter(time -> time.toLocalDate().equals(dataTime.toLocalDate()))
                .isEmpty()) {
            log.warn("[맵리포트] 오늘의 공통 스냅샷이 없어 발송 생략 | context : {}", dataTime);
            return;
        }
        // 캡처 대상은 항상 두 마켓 고정이다. SectorTelegramReportSender처럼 랭킹 조회 결과로 마켓을
        // 고르면 안 된다 — 맵과 캡션이 같은 가격 행(sector_price_snapshot)을 쓰더라도, 한 마켓만 그
        // 시각 가격 행이 없으면 병합 랭킹(getMergedTopSectorRanking)은 "하나라도 비면 빈 목록"
        // 규칙에 걸려 통째로 빈다. 맵은 나머지 마켓만으로도 그려지므로, 그 조회 결과를 캡처 대상으로
        // 따르면 캡처를 한 장도 안 한 채 "캡처 실패"로 에스컬레이션한다.
        List<byte[]> images =
                capture(MAP_MARKETS.toMarkets(), telegramProperties.averageMode(), telegramProperties.sectorFilter());
        if (images.isEmpty()) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        }

        sendImages(images, buildCaption(MAP_MARKETS, dataTime));
        log.info("맵 리포트 발송 완료: 이미지={}장", images.size());
    }

    /**
     * 수동 테스트 전용 — 스케줄러는 부르지 않는다. {@code query} 하나만 {@code /map/{세그먼트}} 한
     * 페이지로 캡처해서 한 장으로 보낸다({@code ALL_STOCK}이면 {@code /map/allstock}). sendImages는
     * 이미지가 한 장이면 이미 sendPhoto로 내려가므로 발송 쪽은 send()와 그대로 같이 쓴다.
     */
    public void sendMapSinglePage(LocalDateTime dataTime, MarketQuery query) {
        List<byte[]> images = screenshotClient.capture(
                mapPath(
                        RenderTarget.marketSegment(query),
                        telegramProperties.averageMode(),
                        telegramProperties.sectorFilter()),
                RenderTarget.MAP.selector());
        if (images.isEmpty()) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        }

        sendImages(images, buildCaption(query, dataTime));
        log.info("맵 한 페이지 리포트 발송 완료: 이미지={}장", images.size());
    }

    /** 캡션을 못 만들면 null — TelegramClient가 null/공백 캡션을 붙이지 않는다. */
    private String buildCaption(MarketQuery query, LocalDateTime dataTime) {
        List<TopSectorItem> topSectors = treemapQueryService.getMergedTopSectorRanking(
                query, dataTime, telegramProperties.averageMode(), telegramProperties.sectorFilter());
        // 그 시각 데이터가 없거나(수집 실패) 요청 마켓 중 하나라도 합산이 비면 헤더만 덜렁 남는다.
        // 이미지는 이미 찍었으므로 캡션만 버리고 보낸다.
        if (topSectors.isEmpty()) {
            log.warn("{} 시각 병합 섹터 랭킹이 비어 있어 맵 캡션 없이 발송", dataTime);
            return null;
        }
        return sectorRankingTextBuilder.buildMapCaption(topSectors);
    }

    private List<byte[]> capture(List<Market> markets, AverageMode averageMode, boolean sectorFilter) {
        return markets.stream()
                .flatMap(market -> screenshotClient
                        .capture(
                                mapPath(RenderTarget.marketSegment(market), averageMode, sectorFilter),
                                RenderTarget.MAP.selector())
                        .stream())
                .toList();
    }

    // 텔레그램 sendMediaGroup은 media가 2~10개여야 한다 — 한 마켓만 캡처됐으면 sendPhoto로 내려간다.
    private void sendImages(List<byte[]> images, String caption) {
        if (images.size() == 1) {
            telegramClient.sendPhoto(telegramProperties.chatId(), images.get(0), caption);
            return;
        }
        telegramClient.sendMediaGroup(telegramProperties.chatId(), images, caption);
    }

    private String mapPath(String marketSegment, AverageMode averageMode, boolean sectorFilter) {
        return RenderTarget.MAP.path() + "/" + marketSegment
                + "?avgMode=" + averageMode.queryValue()
                + "&sectorFilter=" + sectorFilter;
    }
}
