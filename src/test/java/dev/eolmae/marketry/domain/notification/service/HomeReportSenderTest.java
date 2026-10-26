package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.domain.notification.client.TelegramClient;
import dev.eolmae.marketry.domain.notification.enums.RenderBasis;
import dev.eolmae.marketry.domain.notification.enums.RenderTarget;
import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketry.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketry.domain.renderer.client.ScreenshotClient.CaptureResult;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.view.dto.TopSectorItem;
import dev.eolmae.marketry.domain.view.enums.AverageMode;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.MarketTradingScheduleService;
import dev.eolmae.marketry.domain.view.service.TreemapQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HomeReportSenderTest {

    private static final LocalDateTime DATA_TIME = LocalDateTime.of(2026, 10, 22, 8, 5);
    private static final byte[] SUMMARY_IMAGE = {1, 2, 3};
    private static final byte[] MAP_IMAGE = {4, 5, 6};
    private static final String ROW_SELECTOR = "[data-captureid='summary-capture'] [data-capture-row]";
    private static final List<String> ROWS = List.of("{\"name\":\"헬스케어\",\"value\":\"+1.91\"}");

    private final ScreenshotClient screenshotClient = mock(ScreenshotClient.class);
    private final TelegramClient telegramClient = mock(TelegramClient.class);
    private final SectorRankingTextBuilder sectorRankingTextBuilder = mock(SectorRankingTextBuilder.class);
    private final TreemapQueryService treemapQueryService = mock(TreemapQueryService.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService = mock(SectorPriceSnapshotService.class);
    private final MarketTradingScheduleService marketTradingScheduleService = mock(MarketTradingScheduleService.class);
    private final EscalationPublisher escalationPublisher = mock(EscalationPublisher.class);
    private final RenderBasisResolver renderBasisResolver = mock(RenderBasisResolver.class);
    private final TelegramProperties properties =
            new TelegramProperties("token", "chat", "developer", 5, 30, 15, AverageMode.SIMPLE, true);
    private final HomeReportSender sender = new HomeReportSender(
            screenshotClient,
            telegramClient,
            properties,
            sectorRankingTextBuilder,
            treemapQueryService,
            sectorPriceSnapshotService,
            marketTradingScheduleService,
            escalationPublisher,
            renderBasisResolver);

    @org.junit.jupiter.api.BeforeEach
    void givenNoBasis() {
        when(renderBasisResolver.resolve(any())).thenReturn(Optional.empty());
    }

    private void givenTodaySnapshot() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.of(DATA_TIME));
    }

    private void givenSummaryCapture(List<String> rows) {
        when(screenshotClient.captureWithRows(
                        RenderTarget.HOME_MOBILE.path(Optional.empty()),
                        RenderTarget.HOME_MOBILE.selector(),
                        ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(SUMMARY_IMAGE), rows));
    }

    private void givenMapCapture() {
        when(screenshotClient.capture(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()), RenderTarget.MAP_MOBILE.selector()))
                .thenReturn(List.of(MAP_IMAGE));
    }

    @Test
    void 오늘의_공통_스냅샷이_없으면_찍지도_보내지도_않는다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.empty());

        assertThat(sender.send(DATA_TIME)).isFalse();

        verify(screenshotClient, never()).captureWithRows(any(), any(), any());
        verify(telegramClient, never()).sendMediaGroup(any(), any(), any());
    }

    @Test
    void 이전_날짜의_스냅샷만_있으면_보내지_않는다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList()))
                .thenReturn(Optional.of(DATA_TIME.minusDays(1)));

        assertThat(sender.send(DATA_TIME)).isFalse();

        verify(screenshotClient, never()).captureWithRows(any(), any(), any());
    }

    @Test
    void 일회성_발송은_최근_공통_스냅샷이_오늘이_아니어도_홈리포트를_보낸다() {
        LocalDateTime latestSnapshot = DATA_TIME.minusDays(1);
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList()))
                .thenReturn(Optional.of(latestSnapshot));
        when(renderBasisResolver.resolve(latestSnapshot)).thenReturn(Optional.of(RenderBasis.AFTER_HOURS));
        when(screenshotClient.captureWithRows(
                        "/home?capture=mobile&basis=afterHours", RenderTarget.HOME_MOBILE.selector(), ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(SUMMARY_IMAGE), ROWS));
        when(screenshotClient.capture(
                        "/map/allstock?capture=mobile&basis=afterHours", RenderTarget.MAP_MOBILE.selector()))
                .thenReturn(List.of(MAP_IMAGE));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        assertThat(sender.sendLatest()).isTrue();

        verify(screenshotClient)
                .captureWithRows(
                        "/home?capture=mobile&basis=afterHours", RenderTarget.HOME_MOBILE.selector(), ROW_SELECTOR);
        verify(telegramClient).sendMediaGroup("chat", List.of(SUMMARY_IMAGE, MAP_IMAGE), "캡션\n#애프터");
    }

    @Test
    void 일회성_발송은_공통_스냅샷이_없으면_보내지_않는다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.empty());

        assertThat(sender.sendLatest()).isFalse();

        verify(screenshotClient, never()).captureWithRows(any(), any(), any());
        verify(telegramClient, never()).sendMediaGroup(any(), any(), any());
    }

    // 이미지에 그려진 그룹 등락률 값으로 캡션을 만들어야 이미지와 숫자가 어긋나지 않는다.
    @Test
    void 시장_요약과_시장_지도_두_장을_이미지에서_읽은_값의_캡션과_함께_앨범으로_보낸다() {
        givenTodaySnapshot();
        givenSummaryCapture(ROWS);
        givenMapCapture();
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("헬스케어 +1.91% / S7 +1.71%");

        assertThat(sender.send(DATA_TIME)).isTrue();

        verify(telegramClient).sendMediaGroup("chat", List.of(SUMMARY_IMAGE, MAP_IMAGE), "헬스케어 +1.91% / S7 +1.71%");
        verify(treemapQueryService, never()).getMergedSectorRanking(any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void 이미지에서_값을_못_읽으면_서버_계산_순위로_캡션을_만든다() {
        givenTodaySnapshot();
        givenSummaryCapture(List.of());
        givenMapCapture();
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(List.of(), 2))
                .thenReturn(null);
        TopSectorItem top = new TopSectorItem("필수소비재", new BigDecimal("-0.38"));
        TopSectorItem second = new TopSectorItem("금융", new BigDecimal("-2.07"));
        TopSectorItem third = new TopSectorItem("신규 상장", new BigDecimal("-31.93"));
        when(treemapQueryService.getMergedSectorRanking(
                        eq(MarketQuery.ALL_STOCK), eq(DATA_TIME), eq(AverageMode.SIMPLE), eq(true), eq(false)))
                .thenReturn(List.of(top, second, third));
        when(sectorRankingTextBuilder.buildMapCaption(List.of(top, second))).thenReturn("계산 캡션");

        sender.send(DATA_TIME);

        verify(telegramClient).sendMediaGroup("chat", List.of(SUMMARY_IMAGE, MAP_IMAGE), "계산 캡션");
    }

    // 프리·애프터 마켓에는 화면이 NXT 종목만 보여 주므로, 서버 계산 캡션도 같은 종목으로 순위를 만든다.
    // 15:30~15:40은 종가, 15:40부터는 애프터 마켓 별도 화면으로 두 이미지를 모두 찍는다.
    @Test
    void 발송_시각의_등락률_기준을_두_이미지_주소에_붙인다() {
        givenTodaySnapshot();
        when(renderBasisResolver.resolve(DATA_TIME)).thenReturn(Optional.of(RenderBasis.AFTER_HOURS));
        when(screenshotClient.captureWithRows(
                        "/home?capture=mobile&basis=afterHours", RenderTarget.HOME_MOBILE.selector(), ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(SUMMARY_IMAGE), ROWS));
        when(screenshotClient.capture(
                        "/map/allstock?capture=mobile&basis=afterHours", RenderTarget.MAP_MOBILE.selector()))
                .thenReturn(List.of(MAP_IMAGE));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(telegramClient).sendMediaGroup("chat", List.of(SUMMARY_IMAGE, MAP_IMAGE), "캡션\n#애프터");
    }

    @Test
    void 종가로_보내는_메시지는_캡션_아래_줄에_종가_태그를_붙인다() {
        givenTodaySnapshot();
        when(renderBasisResolver.resolve(DATA_TIME)).thenReturn(Optional.of(RenderBasis.CLOSE));
        when(screenshotClient.captureWithRows(
                        "/home?capture=mobile&basis=close", RenderTarget.HOME_MOBILE.selector(), ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(SUMMARY_IMAGE), ROWS));
        when(screenshotClient.capture("/map/allstock?capture=mobile&basis=close", RenderTarget.MAP_MOBILE.selector()))
                .thenReturn(List.of(MAP_IMAGE));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("헬스케어 +1.91% / S7 +1.71%");

        sender.send(DATA_TIME);

        verify(telegramClient)
                .sendMediaGroup("chat", List.of(SUMMARY_IMAGE, MAP_IMAGE), "헬스케어 +1.91% / S7 +1.71%\n#종가");
    }

    @Test
    void 별도로_보내는_메시지에는_애프터_태그를_붙인다() {
        givenTodaySnapshot();
        when(renderBasisResolver.resolve(DATA_TIME)).thenReturn(Optional.of(RenderBasis.AFTER_HOURS));
        when(screenshotClient.captureWithRows(
                        "/home?capture=mobile&basis=afterHours", RenderTarget.HOME_MOBILE.selector(), ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(SUMMARY_IMAGE), ROWS));
        when(screenshotClient.capture(
                        "/map/allstock?capture=mobile&basis=afterHours", RenderTarget.MAP_MOBILE.selector()))
                .thenReturn(List.of(MAP_IMAGE));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(telegramClient).sendMediaGroup("chat", List.of(SUMMARY_IMAGE, MAP_IMAGE), "캡션\n#애프터");
    }

    @Test
    void 서버_계산_캡션은_NXT_단독_시간대면_NXT_종목만으로_순위를_만든다() {
        givenTodaySnapshot();
        givenSummaryCapture(List.of());
        givenMapCapture();
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(any(), eq(2)))
                .thenReturn(null);
        when(marketTradingScheduleService.isNxtOnly(DATA_TIME)).thenReturn(true);
        TopSectorItem top = new TopSectorItem("헬스케어", new BigDecimal("1.91"));
        TopSectorItem second = new TopSectorItem("S7", new BigDecimal("1.71"));
        when(treemapQueryService.getMergedSectorRanking(
                        eq(MarketQuery.ALL_STOCK), eq(DATA_TIME), eq(AverageMode.SIMPLE), eq(true), eq(true)))
                .thenReturn(List.of(top, second));
        when(sectorRankingTextBuilder.buildMapCaption(List.of(top, second))).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(telegramClient).sendMediaGroup("chat", List.of(SUMMARY_IMAGE, MAP_IMAGE), "캡션");
    }

    @Test
    void 읽은_값도_계산_순위도_2개_미만이면_캡션_없이_이미지만_보낸다() {
        givenTodaySnapshot();
        givenSummaryCapture(List.of());
        givenMapCapture();
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(any(), eq(2)))
                .thenReturn(null);
        when(treemapQueryService.getMergedSectorRanking(any(), any(), any(), eq(true), anyBoolean()))
                .thenReturn(List.of(new TopSectorItem("금융", BigDecimal.ONE)));

        sender.send(DATA_TIME);

        verify(telegramClient).sendMediaGroup("chat", List.of(SUMMARY_IMAGE, MAP_IMAGE), null);
    }

    // 새로 붙인 시장 지도 캡처가 실패해도 시장 요약은 나가야 한다 — 실패는 개발자 채널로 알린다.
    @Test
    void 시장_지도_캡처가_실패하면_시장_요약_한_장만_보내고_에스컬레이션한다() {
        givenTodaySnapshot();
        givenSummaryCapture(ROWS);
        EscalateException failure = new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        when(screenshotClient.capture(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()), RenderTarget.MAP_MOBILE.selector()))
                .thenThrow(failure);
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(escalationPublisher).report(failure);
        verify(telegramClient).sendPhoto("chat", SUMMARY_IMAGE, "캡션");
        verify(telegramClient, never()).sendMediaGroup(any(), any(), any());
    }

    @Test
    void 시장_요약_캡처가_비어_있으면_에스컬레이션한다() {
        givenTodaySnapshot();
        when(screenshotClient.captureWithRows(anyString(), anyString(), anyString()))
                .thenReturn(new CaptureResult(List.of(), List.of()));

        assertThatThrownBy(() -> sender.send(DATA_TIME)).isInstanceOf(EscalateException.class);
        verify(telegramClient, never()).sendMediaGroup(any(), any(), any());
        verify(telegramClient, never()).sendPhoto(any(), any(), isNull());
    }

    @Test
    void 캡처_주소와_캡처_이름은_프론트의_3대4_판과_맞다() {
        assertThat(RenderTarget.HOME_MOBILE.path()).isEqualTo("/home?capture=mobile");
        assertThat(RenderTarget.HOME_MOBILE.selector()).isEqualTo("[data-captureid='summary-capture']");
        assertThat(RenderTarget.MAP_MOBILE.path()).isEqualTo("/map/allstock?capture=mobile");
        assertThat(RenderTarget.MAP_MOBILE.selector()).isEqualTo("[data-captureid='map-mobile-capture']");
    }

    @Test
    void 등락률_기준이_없으면_주소는_그대로이고_있으면_기준을_붙인다() {
        assertThat(RenderTarget.HOME_MOBILE.path(Optional.empty())).isEqualTo("/home?capture=mobile");
        assertThat(RenderTarget.HOME_MOBILE.path(Optional.of(RenderBasis.CLOSE)))
                .isEqualTo("/home?capture=mobile&basis=close");
        assertThat(RenderTarget.MAP_MOBILE.path(Optional.of(RenderBasis.AFTER_HOURS)))
                .isEqualTo("/map/allstock?capture=mobile&basis=afterHours");
    }
}
