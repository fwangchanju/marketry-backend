package kr.co.marketry.domain.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.domain.notification.client.TelegramClient;
import kr.co.marketry.domain.notification.enums.RenderBasis;
import kr.co.marketry.domain.notification.enums.RenderTarget;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import kr.co.marketry.domain.renderer.client.ScreenshotClient;
import kr.co.marketry.domain.renderer.client.ScreenshotClient.CaptureResult;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService;
import kr.co.marketry.domain.stock.service.SectorPriceSnapshotService;
import kr.co.marketry.domain.view.dto.TopSectorItem;
import kr.co.marketry.domain.view.enums.MarketQuery;
import kr.co.marketry.domain.view.service.MarketTradingScheduleService;
import kr.co.marketry.domain.view.service.TreemapQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 시장 지도를 모바일 캡처 한 장으로 보낸다. 캡션은 시장 요약 화면의 그룹 등락률을 읽어 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeReportSender {

    private static final MarketQuery HOME_MARKETS = MarketQuery.ALL_STOCK;
    private static final int CAPTION_SECTOR_COUNT = 2;
    private static final String KOREA_HEADER = "#KOSPI #KOSDAQ";
    private static final String KOREA_CLOSE_HEADER = "#KOSPI #KOSDAQ #마감";
    private static final String JAPAN_HEADER = "#니케이225 (20분 지연)";
    private static final String JAPAN_CLOSE_HEADER = "#니케이225 (20분 지연) #마감";
    private static final String MAP_CAPTURE_ERROR_SOURCE = "홈리포트 모바일 지도 캡처";
    // 시장 요약 판 안의 그룹 등락률 업종 막대 — 코스피·코스닥 지수 줄은 data-capture-row가 없다. 일반 홈 화면 쪽 같은 막대는 제외한다.
    private static final String GROUP_ROW_SELECTOR = "[data-captureid='summary-capture'] [data-capture-row]";
    private static final String JAPAN_GROUP_ROW_SELECTOR =
            "[data-captureid='map-mobile-capture'] [data-japan-report-row]";

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final SectorRankingTextBuilder sectorRankingTextBuilder;
    private final TreemapQueryService treemapQueryService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final MarketTradingScheduleService marketTradingScheduleService;
    private final EscalationPublisher escalationPublisher;
    private final RenderBasisResolver renderBasisResolver;
    private final OverseasStockQuoteService overseasStockQuoteService;

    @Value("${collect.interval-minutes}")
    private int collectionIntervalMinutes;

    public boolean sendLatest() {
        return sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(HOME_MARKETS.toMarkets())
                .map(dataTime -> sendReport(dataTime, false))
                .orElse(false);
    }

    public boolean sendLatestKorea() {
        return sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(HOME_MARKETS.toMarkets())
                .map(this::sendKoreaTestReport)
                .orElse(false);
    }

    public boolean sendLatestJapan() {
        return sendLatestJapanOnce() == JapanSendResult.SENT;
    }

    public JapanSendResult sendLatestJapanOnce() {
        OverseasStockQuoteService.QuoteSnapshot snapshot;
        try {
            snapshot = overseasStockQuoteService.findQuotes("JAPAN", null);
        } catch (Exception e) {
            log.warn("[홈리포트] 일본 최신 시세 조회 실패 | context : {}", e.getClass().getSimpleName());
            return JapanSendResult.QUOTE_LOOKUP_FAILED;
        }
        if (snapshot.snapshotTime() == null) return JapanSendResult.NO_QUOTES;

        Optional<CapturedJapanMap> captured = captureJapanMap();
        if (captured.isEmpty()) return JapanSendResult.MAP_CAPTURE_FAILED;
        CapturedJapanMap testCapture = captured.get();
        CapturedJapanMap markedCapture =
                new CapturedJapanMap(testCapture.images(), withTestMarker(testCapture.caption()));
        return sendJapanMap(markedCapture) ? JapanSendResult.SENT : JapanSendResult.TELEGRAM_SEND_FAILED;
    }

    public enum JapanSendResult {
        SENT,
        NO_QUOTES,
        QUOTE_LOOKUP_FAILED,
        MAP_CAPTURE_FAILED,
        TELEGRAM_SEND_FAILED
    }

    public boolean send(LocalDateTime dataTime) {
        return send(dataTime, false);
    }

    public boolean send(LocalDateTime dataTime, boolean finalDailySend) {
        if (sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(HOME_MARKETS.toMarkets())
                .filter(dataTime::equals)
                .isEmpty()) {
            log.warn("[홈리포트] 기준 시각의 최신 공통 스냅샷이 없어 발송 생략 | context : {}", dataTime);
            return false;
        }
        return sendReport(dataTime, finalDailySend);
    }

    private boolean sendReport(LocalDateTime dataTime, boolean finalDailySend) {
        // 캡션과 지도에 같은 등락률 기준(종가·별도)을 적용한다.
        Optional<RenderBasis> basis = renderBasisResolver.resolve(dataTime);
        String caption = null;
        try {
            CaptureResult summary = screenshotClient.captureWithRows(
                    RenderTarget.HOME_MOBILE.path(basis), RenderTarget.HOME_MOBILE.selector(), GROUP_ROW_SELECTOR);
            if (!summary.images().isEmpty()) {
                caption = sectorRankingTextBuilder.buildCaptionFromRenderedRows(summary.rows(), CAPTION_SECTOR_COUNT);
            }
        } catch (Exception e) {
            log.warn(
                    "[홈리포트] 요약 캡처 실패로 서버 계산 캡션 사용 | context : {}|{}",
                    dataTime,
                    e.getClass().getSimpleName());
        }
        if (caption == null) {
            log.warn("[홈리포트] 이미지에서 그룹 등락률 값을 읽지 못해 서버 계산 캡션으로 발송 | context : {}", dataTime);
            try {
                caption = buildCaption(dataTime);
            } catch (Exception e) {
                log.warn("[홈리포트] 캡션 계산 실패로 캡션 없이 발송 | context : {}", dataTime, e);
            }
        }
        caption = withHeader(caption, koreaHeader(basis, dataTime));

        List<byte[]> mapImages = captureMapImages(basis);
        if (mapImages.isEmpty()) {
            log.warn("[홈리포트] 시장 지도 캡처 실패로 발송 생략");
            return false;
        }
        telegramClient.sendPhoto(telegramProperties.chatId(), mapImages.get(0), caption);
        log.info("홈 리포트 발송 완료: 국내 시장 지도 1장");
        if (finalDailySend) {
            captureJapanMap(dataTime, true).ifPresent(this::sendJapanMap);
        }
        return true;
    }

    private boolean sendKoreaTestReport(LocalDateTime dataTime) {
        Optional<RenderBasis> basis = renderBasisResolver.resolve(dataTime);
        String caption = null;
        try {
            CaptureResult summary = screenshotClient.captureWithRows(
                    RenderTarget.HOME_MOBILE.path(basis), RenderTarget.HOME_MOBILE.selector(), GROUP_ROW_SELECTOR);
            if (!summary.images().isEmpty()) {
                caption = sectorRankingTextBuilder.buildCaptionFromRenderedRows(summary.rows(), CAPTION_SECTOR_COUNT);
            }
        } catch (Exception e) {
            log.warn(
                    "[홈리포트] 요약 캡처 실패로 서버 계산 캡션 사용 | context : {}|{}",
                    dataTime,
                    e.getClass().getSimpleName());
        }
        if (caption == null) {
            try {
                caption = buildCaption(dataTime);
            } catch (Exception e) {
                log.warn("[홈리포트] 캡션 계산 실패로 캡션 없이 발송 | context : {}", dataTime, e);
            }
        }
        caption = withHeader(caption, koreaHeader(basis, dataTime));
        caption = withTestMarker(caption);
        List<byte[]> images = captureMapImages(basis);
        if (images.isEmpty()) return false;
        telegramClient.sendPhoto(telegramProperties.chatId(), images.get(0), caption);
        return true;
    }

    private boolean sendJapanMap(CapturedJapanMap captured) {
        try {
            telegramClient.sendPhoto(
                    telegramProperties.chatId(), captured.images().get(0), captured.caption());
            log.info("홈 리포트 발송 완료: 일본 시장 지도 1장");
            return true;
        } catch (Exception e) {
            log.warn("[홈리포트] 일본 지도 텔레그램 발송 실패 | context : {}", e.getClass().getSimpleName());
            return false;
        }
    }

    // 당일 일본 시세가 저장된 경우만 함께 보낸다. 아직 첫 시세가 없으면 전일 지도를 오늘 지도처럼 보내지 않는다.
    private Optional<CapturedJapanMap> captureJapanMap(LocalDateTime dataTime, boolean closingTransmission) {
        try {
            LocalDateTime japanSnapshotTime =
                    overseasStockQuoteService.findQuotes("JAPAN", null).snapshotTime();
            if (japanSnapshotTime == null || !japanSnapshotTime.toLocalDate().equals(dataTime.toLocalDate())) {
                return Optional.empty();
            }
            return captureJapanMap(closingTransmission);
        } catch (Exception e) {
            log.warn(
                    "[홈리포트] 일본 지도 캡처 실패로 국내 지도만 발송 | context : {}|{}",
                    dataTime,
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<CapturedJapanMap> captureJapanMap() {
        return captureJapanMap(false);
    }

    private Optional<CapturedJapanMap> captureJapanMap(boolean closingTransmission) {
        try {
            CaptureResult captured = screenshotClient.captureWithRows(
                    RenderTarget.JAPAN_MAP_MOBILE.path(),
                    RenderTarget.JAPAN_MAP_MOBILE.selector(),
                    JAPAN_GROUP_ROW_SELECTOR);
            if (captured.images().isEmpty()) {
                log.warn("[홈리포트] 일본 지도 캡처 결과가 비어 있어 일본 발송 생략");
                return Optional.empty();
            }
            String topSectors =
                    sectorRankingTextBuilder.buildCaptionFromRenderedRows(captured.rows(), CAPTION_SECTOR_COUNT);
            if (topSectors == null || topSectors.isBlank()) {
                log.warn("[홈리포트] 일본 대분류 TOP2를 읽지 못해 일본 지도 발송 생략");
                return Optional.empty();
            }
            String header = closingTransmission ? JAPAN_CLOSE_HEADER : JAPAN_HEADER;
            return Optional.of(new CapturedJapanMap(captured.images(), header + "\n" + topSectors));
        } catch (Exception e) {
            log.warn("[홈리포트] 일본 지도 캡처 실패로 일본 발송 생략 | context : {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String withHeader(String caption, String header) {
        return caption == null || caption.isBlank() ? header : header + "\n" + caption;
    }

    private String withTestMarker(String caption) {
        return caption + "\n\n테스트 발송";
    }

    private record CapturedJapanMap(List<byte[]> images, String caption) {}

    // 마지막 종가 구간의 스냅샷에만 헤더에 마감 태그를 더한다.
    private String koreaHeader(Optional<RenderBasis> basis, LocalDateTime dataTime) {
        return basis.orElse(null) == RenderBasis.CLOSE
                        && renderBasisResolver.isFinalClosingSnapshot(dataTime, collectionIntervalMinutes)
                ? KOREA_CLOSE_HEADER
                : KOREA_HEADER;
    }

    // 시장 지도 캡처 실패는 개발자 채널에 알린다. 한 번 실패는 곧바로 다시 찍어 보고, 또 실패할 때만 알린다.
    private List<byte[]> captureMapImages(Optional<RenderBasis> basis) {
        try {
            List<byte[]> images = captureMapWithRetry(basis);
            escalationPublisher.resolve(MAP_CAPTURE_ERROR_SOURCE);
            return images;
        } catch (EscalateException e) {
            escalationPublisher.report(MAP_CAPTURE_ERROR_SOURCE, e);
            return List.of();
        }
    }

    private List<byte[]> captureMapWithRetry(Optional<RenderBasis> basis) {
        try {
            return captureMap(basis);
        } catch (EscalateException e) {
            log.warn("[홈리포트] 시장 지도 캡처 실패, 1회 다시 시도 | cause={}", e.getCauseMessage());
            return captureMap(basis);
        }
    }

    private List<byte[]> captureMap(Optional<RenderBasis> basis) {
        List<byte[]> images =
                screenshotClient.capture(RenderTarget.MAP_MOBILE.path(basis), RenderTarget.MAP_MOBILE.selector());
        if (images.isEmpty()) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        }
        return images;
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
