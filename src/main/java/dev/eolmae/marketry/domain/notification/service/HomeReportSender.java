package dev.eolmae.marketry.domain.notification.service;

import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.domain.notification.client.TelegramClient;
import dev.eolmae.marketry.domain.notification.enums.RenderTarget;
import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketry.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketry.domain.renderer.client.ScreenshotClient.CaptureResult;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.view.dto.TopSectorItem;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.MarketTradingScheduleService;
import dev.eolmae.marketry.domain.view.service.TreemapQueryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 시장 요약(홈 화면: 툴바·시장 지도·그룹 등락률)과 시장 지도(지도 페이지 모바일 캡처)를 3:4 세로 이미지 두 장으로 찍어 한 메시지(앨범)로 보낸다.
 * 캡션은 첫 이미지(시장 요약)에 그려진 그룹 등락률 막대 값을 그대로 읽어 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeReportSender {

    private static final MarketQuery HOME_MARKETS = MarketQuery.ALL_STOCK;
    private static final int CAPTION_SECTOR_COUNT = 2;
    // 시장 요약 판 안의 그룹 등락률 업종 막대 — 코스피·코스닥 지수 줄은 data-capture-row가 없다. 일반 홈 화면 쪽 같은 막대는 제외한다.
    private static final String GROUP_ROW_SELECTOR = "[data-captureid='summary-capture'] [data-capture-row]";

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final SectorRankingTextBuilder sectorRankingTextBuilder;
    private final TreemapQueryService treemapQueryService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final MarketTradingScheduleService marketTradingScheduleService;
    private final EscalationPublisher escalationPublisher;

    public void send(LocalDateTime dataTime) {
        if (sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(HOME_MARKETS.toMarkets())
                .filter(time -> time.toLocalDate().equals(dataTime.toLocalDate()))
                .isEmpty()) {
            log.warn("[홈리포트] 오늘의 공통 스냅샷이 없어 발송 생략 | context : {}", dataTime);
            return;
        }
        CaptureResult summary = screenshotClient.captureWithRows(
                RenderTarget.HOME_MOBILE.path(), RenderTarget.HOME_MOBILE.selector(), GROUP_ROW_SELECTOR);
        if (summary.images().isEmpty()) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        }
        String caption = sectorRankingTextBuilder.buildCaptionFromRenderedRows(summary.rows(), CAPTION_SECTOR_COUNT);
        if (caption == null) {
            log.warn("[홈리포트] 이미지에서 그룹 등락률 값을 읽지 못해 서버 계산 캡션으로 발송 | context : {}", dataTime);
            caption = buildCaption(dataTime);
        }

        List<byte[]> mapImages = captureMapImages();
        if (mapImages.isEmpty()) {
            telegramClient.sendPhoto(
                    telegramProperties.chatId(), summary.images().get(0), caption);
            log.info("홈 리포트 발송 완료: 시장 요약 1장(시장 지도 캡처 실패)");
            return;
        }
        telegramClient.sendMediaGroup(
                telegramProperties.chatId(), List.of(summary.images().get(0), mapImages.get(0)), caption);
        log.info("홈 리포트 발송 완료: 시장 요약·시장 지도 2장");
    }

    // 시장 지도 캡처가 실패해도 시장 요약은 보낸다 — 실패는 개발자 채널로 알리고 이미지 한 장으로 발송한다.
    private List<byte[]> captureMapImages() {
        try {
            return screenshotClient.capture(RenderTarget.MAP_MOBILE.path(), RenderTarget.MAP_MOBILE.selector());
        } catch (EscalateException e) {
            escalationPublisher.report(e);
            return List.of();
        }
    }

    /**
     * 캡션을 못 만들면 null — TelegramClient가 null/공백 캡션을 붙이지 않는다. 이미지는 이미 찍었으므로 캡션만 버리고 보낸다.
     * 등락률이 가장 높은 상위 업종 {@value #CAPTION_SECTOR_COUNT}개를 "업종 등락률" 한 줄씩 보낸다.
     */
    private String buildCaption(LocalDateTime dataTime) {
        // NXT 단독 시간대(프리·애프터 마켓)에는 화면이 NXT 종목만 보여 주므로 캡션도 같은 종목으로 계산해야 이미지와 숫자가 맞는다.
        List<TopSectorItem> ranking = treemapQueryService.getMergedSectorRanking(
                HOME_MARKETS,
                dataTime,
                telegramProperties.averageMode(),
                telegramProperties.sectorFilter(),
                marketTradingScheduleService.isNxtOnly(dataTime));
        if (ranking.size() < CAPTION_SECTOR_COUNT) {
            log.warn("{} 시각 병합 섹터 랭킹이 {}개 미만이라 홈 캡션 없이 발송", dataTime, CAPTION_SECTOR_COUNT);
            return null;
        }
        return sectorRankingTextBuilder.buildMapCaption(ranking.subList(0, CAPTION_SECTOR_COUNT));
    }
}
