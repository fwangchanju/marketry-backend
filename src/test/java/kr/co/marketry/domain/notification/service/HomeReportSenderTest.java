package kr.co.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.enums.Market;
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
import kr.co.marketry.domain.view.enums.AverageMode;
import kr.co.marketry.domain.view.enums.MarketQuery;
import kr.co.marketry.domain.view.service.MarketTradingScheduleService;
import kr.co.marketry.domain.view.service.TreemapQueryService;
import org.junit.jupiter.api.Test;

class HomeReportSenderTest {

    private static final LocalDateTime DATA_TIME = LocalDateTime.of(2026, 10, 22, 8, 5);
    private static final byte[] MAP_IMAGE = {4, 5, 6};
    private static final byte[] JAPAN_MAP_IMAGE = {7, 8, 9};
    private static final String ROW_SELECTOR = "[data-captureid='map-mobile-capture'] [data-capture-row]";
    private static final String JAPAN_ROW_SELECTOR = "[data-captureid='map-mobile-capture'] [data-capture-row]";
    private static final List<String> ROWS = List.of("{\"name\":\"헬스케어\",\"value\":\"+1.91\"}");
    private static final List<String> JAPAN_ROWS =
            List.of("{\"name\":\"기술\",\"value\":\"+4.5\"}", "{\"name\":\"금융\",\"value\":\"+4.2\"}");

    private final ScreenshotClient screenshotClient = mock(ScreenshotClient.class);
    private final TelegramClient telegramClient = mock(TelegramClient.class);
    private final SectorRankingTextBuilder sectorRankingTextBuilder = mock(SectorRankingTextBuilder.class);
    private final TreemapQueryService treemapQueryService = mock(TreemapQueryService.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService = mock(SectorPriceSnapshotService.class);
    private final OverseasStockQuoteService overseasStockQuoteService = mock(OverseasStockQuoteService.class);
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
            renderBasisResolver,
            overseasStockQuoteService);

    @org.junit.jupiter.api.BeforeEach
    void givenNoBasis() {
        when(renderBasisResolver.resolve(any())).thenReturn(Optional.empty());
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(null, List.of()));
    }

    private void givenTodaySnapshot() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.of(DATA_TIME));
    }

    // 국내 지도 한 번으로 이미지와 업종 등락률 행을 함께 받는다.
    private void givenKoreaMapCapture(List<String> rows) {
        when(screenshotClient.captureWithRows(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()),
                        RenderTarget.MAP_MOBILE.selector(),
                        ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(MAP_IMAGE), rows));
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
    void 같은_날짜라도_요청시각보다_오래된_공통_스냅샷이면_보내지_않는다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList()))
                .thenReturn(Optional.of(DATA_TIME.minusMinutes(5)));

        assertThat(sender.send(DATA_TIME)).isFalse();

        verify(screenshotClient, never()).captureWithRows(any(), any(), any());
    }

    @Test
    void 일회성_국내_발송은_최근_공통_스냅샷으로_국내지도만_보낸다() {
        LocalDateTime latestSnapshot = DATA_TIME.minusDays(1);
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList()))
                .thenReturn(Optional.of(latestSnapshot));
        when(renderBasisResolver.resolve(latestSnapshot)).thenReturn(Optional.of(RenderBasis.AFTER_HOURS));
        when(screenshotClient.captureWithRows(
                        "/map/korea?capture=mobile&basis=afterHours", RenderTarget.MAP_MOBILE.selector(), ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(MAP_IMAGE), ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        assertThat(sender.sendLatest()).isTrue();

        verify(screenshotClient)
                .captureWithRows(
                        "/map/korea?capture=mobile&basis=afterHours", RenderTarget.MAP_MOBILE.selector(), ROW_SELECTOR);
        verify(screenshotClient, never()).captureWithRows(any(), eq(RenderTarget.HOME_MOBILE.selector()), any());
        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥 #애프터마켓\n캡션");
        verify(telegramClient, never()).sendPhoto(eq("chat"), eq(JAPAN_MAP_IMAGE), any());
    }

    @Test
    void 일회성_발송은_공통_스냅샷이_없으면_보내지_않는다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.empty());

        assertThat(sender.sendLatest()).isFalse();

        verify(screenshotClient, never()).captureWithRows(any(), any(), any());
        verify(telegramClient, never()).sendMediaGroup(any(), any(), any());
    }

    @Test
    void 일본_일회성_발송은_일본_지도와_대분류_TOP2만_보낸다() {
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(DATA_TIME, List.of()));
        when(screenshotClient.captureWithRows(
                        RenderTarget.JAPAN_MAP_MOBILE.path(),
                        RenderTarget.JAPAN_MAP_MOBILE.selector(),
                        JAPAN_ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(JAPAN_MAP_IMAGE), JAPAN_ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(JAPAN_ROWS, 2))
                .thenReturn("기술 +4.5%\n금융 +4.2%");

        assertThat(sender.sendLatestJapan()).isTrue();

        verify(telegramClient).sendPhoto("chat", JAPAN_MAP_IMAGE, "#니케이225 (20분 지연)\n기술 +4.5%\n금융 +4.2%\n\n테스트 발송");
        verify(screenshotClient, never())
                .captureWithRows(eq(RenderTarget.MAP_MOBILE.path(Optional.empty())), any(), any());
        verify(sectorPriceSnapshotService, never()).findLatestCommonSnapshotTime(anyList());
    }

    @Test
    void 일본_테스트_캡처가_실패하면_일본_지도로_에스컬레이션한다() {
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(DATA_TIME, List.of()));
        when(screenshotClient.captureWithRows(
                        RenderTarget.JAPAN_MAP_MOBILE.path(),
                        RenderTarget.JAPAN_MAP_MOBILE.selector(),
                        JAPAN_ROW_SELECTOR))
                .thenThrow(new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED));

        assertThat(sender.sendLatestJapanOnce()).isEqualTo(HomeReportSender.JapanSendResult.MAP_CAPTURE_FAILED);

        verify(escalationPublisher).report(eq("일본 지도"), any(EscalateException.class));
        verify(telegramClient, never()).sendPhoto(any(), any(), any());
    }

    @Test
    void 일본_테스트_텔레그램_전송이_실패하면_일본_지도로_에스컬레이션한다() {
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(DATA_TIME, List.of()));
        when(screenshotClient.captureWithRows(
                        RenderTarget.JAPAN_MAP_MOBILE.path(),
                        RenderTarget.JAPAN_MAP_MOBILE.selector(),
                        JAPAN_ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(JAPAN_MAP_IMAGE), JAPAN_ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(JAPAN_ROWS, 2))
                .thenReturn("기술 +4.5%\n금융 +4.2%");
        org.mockito.Mockito.doThrow(new RuntimeException("전송 실패"))
                .when(telegramClient)
                .sendPhoto("chat", JAPAN_MAP_IMAGE, "#니케이225 (20분 지연)\n기술 +4.5%\n금융 +4.2%\n\n테스트 발송");

        assertThat(sender.sendLatestJapanOnce()).isEqualTo(HomeReportSender.JapanSendResult.TELEGRAM_SEND_FAILED);

        verify(escalationPublisher).report(eq("일본 지도"), any(EscalateException.class));
    }

    @Test
    void 일본_일회성_발송은_시세가_없으면_발송하지_않는다() {
        assertThat(sender.sendLatestJapan()).isFalse();

        verify(screenshotClient, never()).captureWithRows(any(), any(), any());
        verify(telegramClient, never()).sendPhoto(any(), any(), any());
    }

    // 이미지에 그려진 그룹 등락률 값으로 캡션을 만들어야 이미지와 숫자가 어긋나지 않는다.
    @Test
    void 시장_지도_한_장을_그룹_등락률_캡션과_함께_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("헬스케어 +1.91% / S7 +1.71%");

        assertThat(sender.send(DATA_TIME)).isTrue();

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n헬스케어 +1.91% / S7 +1.71%");
        verify(treemapQueryService, never()).getMergedSectorRanking(any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void 정기_발송은_당일_일본_시세가_있으면_국내_지도_뒤에_일본_지도를_별도_사진으로_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(DATA_TIME, List.of()));
        when(screenshotClient.captureWithRows(
                        RenderTarget.JAPAN_MAP_MOBILE.path(),
                        RenderTarget.JAPAN_MAP_MOBILE.selector(),
                        JAPAN_ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(JAPAN_MAP_IMAGE), JAPAN_ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(JAPAN_ROWS, 2))
                .thenReturn("기술 +4.5%\n금융 +4.2%");

        assertThat(sender.send(DATA_TIME)).isTrue();

        var order = inOrder(telegramClient);
        order.verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n캡션");
        order.verify(telegramClient).sendPhoto("chat", JAPAN_MAP_IMAGE, "#니케이225 (20분 지연)\n기술 +4.5%\n금융 +4.2%");
        verify(telegramClient, never()).sendMediaGroup(any(), any(), any());
    }

    @Test
    void 같은_일본_시세로는_일본_지도를_다시_보내지_않는다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        givenJapanSnapshot(DATA_TIME);
        givenJapanCapture();

        sender.send(DATA_TIME);
        sender.send(DATA_TIME);

        verify(telegramClient, times(2)).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n캡션");
        verify(telegramClient, times(1)).sendPhoto(eq("chat"), eq(JAPAN_MAP_IMAGE), anyString());
    }

    @Test
    void 새_일본_시세가_저장되면_다음_발송에서_일본_지도를_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        givenJapanSnapshot(DATA_TIME);
        givenJapanCapture();

        sender.send(DATA_TIME);
        givenJapanSnapshot(DATA_TIME.plusMinutes(5));
        sender.send(DATA_TIME);

        verify(telegramClient, times(2)).sendPhoto("chat", JAPAN_MAP_IMAGE, "#니케이225 (20분 지연)\n기술 +4.5%\n금융 +4.2%");
    }

    @Test
    void 일본_15시30분_시세는_마감을_붙여_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        givenJapanSnapshot(DATA_TIME.toLocalDate().atTime(15, 30));
        givenJapanCapture();

        sender.send(DATA_TIME);

        verify(telegramClient).sendPhoto("chat", JAPAN_MAP_IMAGE, "#니케이225 (20분 지연) #마감\n기술 +4.5%\n금융 +4.2%");
    }

    @Test
    void 일본_지도_전송이_실패하면_같은_시세로_다음_발송에서_다시_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        givenJapanSnapshot(DATA_TIME);
        givenJapanCapture();
        org.mockito.Mockito.doThrow(new RuntimeException("텔레그램 발송 실패"))
                .doNothing()
                .when(telegramClient)
                .sendPhoto(eq("chat"), eq(JAPAN_MAP_IMAGE), anyString());

        sender.send(DATA_TIME);
        sender.send(DATA_TIME);

        verify(telegramClient, times(2)).sendPhoto(eq("chat"), eq(JAPAN_MAP_IMAGE), anyString());
    }

    private void givenJapanSnapshot(LocalDateTime snapshotTime) {
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(snapshotTime, List.of()));
    }

    private void givenJapanCapture() {
        when(screenshotClient.captureWithRows(
                        RenderTarget.JAPAN_MAP_MOBILE.path(),
                        RenderTarget.JAPAN_MAP_MOBILE.selector(),
                        JAPAN_ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(JAPAN_MAP_IMAGE), JAPAN_ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(JAPAN_ROWS, 2))
                .thenReturn("기술 +4.5%\n금융 +4.2%");
    }

    @Test
    void 일본_당일_시세가_없으면_국내_지도만_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(DATA_TIME.minusDays(1), List.of()));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(screenshotClient, never())
                .capture(RenderTarget.JAPAN_MAP_MOBILE.path(), RenderTarget.JAPAN_MAP_MOBILE.selector());
        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n캡션");
    }

    @Test
    void 일본_지도_캡처가_실패해도_국내_지도는_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(DATA_TIME, List.of()));
        when(screenshotClient.captureWithRows(
                        RenderTarget.JAPAN_MAP_MOBILE.path(),
                        RenderTarget.JAPAN_MAP_MOBILE.selector(),
                        JAPAN_ROW_SELECTOR))
                .thenThrow(new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        assertThat(sender.send(DATA_TIME)).isTrue();

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n캡션");
        verify(escalationPublisher).report(eq("일본 지도"), any(EscalateException.class));
        verify(telegramClient, never()).sendMediaGroup(any(), any(), any());
    }

    @Test
    void 일본_시세_조회가_실패해도_국내_지도는_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        when(overseasStockQuoteService.findQuotes("JAPAN", null)).thenThrow(new RuntimeException("조회 실패"));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        assertThat(sender.send(DATA_TIME)).isTrue();

        verify(screenshotClient, never())
                .capture(RenderTarget.JAPAN_MAP_MOBILE.path(), RenderTarget.JAPAN_MAP_MOBILE.selector());
        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n캡션");
        var failure = org.mockito.ArgumentCaptor.forClass(EscalateException.class);
        verify(escalationPublisher).report(eq("일본 지도"), failure.capture());
        assertThat(failure.getValue().getErrorCode()).isEqualTo(ErrorCode.MAP_QUOTE_LOOKUP_FAILED);
    }

    @Test
    void 일본_테스트_발송에서_시세_조회가_실패하면_일본_지도로_알린다() {
        when(overseasStockQuoteService.findQuotes("JAPAN", null)).thenThrow(new RuntimeException("조회 실패"));

        assertThat(sender.sendLatestJapanOnce()).isEqualTo(HomeReportSender.JapanSendResult.QUOTE_LOOKUP_FAILED);

        var failure = org.mockito.ArgumentCaptor.forClass(EscalateException.class);
        verify(escalationPublisher).report(eq("일본 지도"), failure.capture());
        assertThat(failure.getValue().getErrorCode()).isEqualTo(ErrorCode.MAP_QUOTE_LOOKUP_FAILED);
        verify(telegramClient, never()).sendPhoto(any(), any(), any());
    }

    @Test
    void 일본_지도에서_TOP2를_읽지_못하면_일본_지도_캡션_실패를_알린다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(DATA_TIME, List.of()));
        when(screenshotClient.captureWithRows(
                        RenderTarget.JAPAN_MAP_MOBILE.path(),
                        RenderTarget.JAPAN_MAP_MOBILE.selector(),
                        JAPAN_ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(JAPAN_MAP_IMAGE), List.of()));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(List.of(), 2))
                .thenReturn(null);

        assertThat(sender.send(DATA_TIME)).isTrue();

        var failure = org.mockito.ArgumentCaptor.forClass(EscalateException.class);
        verify(escalationPublisher).report(eq("일본 지도"), failure.capture());
        assertThat(failure.getValue().getErrorCode()).isEqualTo(ErrorCode.MAP_CAPTION_BUILD_FAILED);
        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n캡션");
        verify(telegramClient, never()).sendPhoto(eq("chat"), eq(JAPAN_MAP_IMAGE), anyString());
    }

    @Test
    void 일본_사진_전송이_실패해도_국내_사진_전송은_성공으로_유지한다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        when(overseasStockQuoteService.findQuotes("JAPAN", null))
                .thenReturn(new OverseasStockQuoteService.QuoteSnapshot(DATA_TIME, List.of()));
        when(screenshotClient.captureWithRows(
                        RenderTarget.JAPAN_MAP_MOBILE.path(),
                        RenderTarget.JAPAN_MAP_MOBILE.selector(),
                        JAPAN_ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(JAPAN_MAP_IMAGE), JAPAN_ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(JAPAN_ROWS, 2))
                .thenReturn("기술 +4.5%\n금융 +4.2%");
        org.mockito.Mockito.doThrow(new RuntimeException("텔레그램 발송 실패"))
                .when(telegramClient)
                .sendPhoto("chat", JAPAN_MAP_IMAGE, "#니케이225 (20분 지연)\n기술 +4.5%\n금융 +4.2%");

        assertThat(sender.send(DATA_TIME)).isTrue();

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n캡션");
        verify(telegramClient).sendPhoto("chat", JAPAN_MAP_IMAGE, "#니케이225 (20분 지연)\n기술 +4.5%\n금융 +4.2%");
    }

    @Test
    void 이미지에서_값을_못_읽으면_서버_계산_순위로_캡션을_만든다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(List.of());
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

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n계산 캡션");
    }

    // 프리·애프터 마켓에는 화면이 NXT 종목만 보여 주므로, 서버 계산 캡션도 같은 종목으로 순위를 만든다.
    // 15:30~15:40은 종가, 15:40부터는 애프터 마켓 별도 화면으로 지도를 찍는다.
    @Test
    void 발송_시각의_등락률_기준을_지도_주소에_붙인다() {
        givenTodaySnapshot();
        when(renderBasisResolver.resolve(DATA_TIME)).thenReturn(Optional.of(RenderBasis.AFTER_HOURS));
        when(screenshotClient.captureWithRows(
                        "/map/korea?capture=mobile&basis=afterHours", RenderTarget.MAP_MOBILE.selector(), ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(MAP_IMAGE), ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥 #애프터마켓\n캡션");
    }

    @Test
    void 종가로_보내는_메시지는_첫줄에_마감_태그를_붙인다() {
        givenTodaySnapshot();
        when(renderBasisResolver.resolve(DATA_TIME)).thenReturn(Optional.of(RenderBasis.CLOSE));
        when(renderBasisResolver.isFinalClosingSnapshot(DATA_TIME, 0)).thenReturn(true);
        when(screenshotClient.captureWithRows(
                        "/map/korea?capture=mobile&basis=close", RenderTarget.MAP_MOBILE.selector(), ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(MAP_IMAGE), ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("헬스케어 +1.91% / S7 +1.71%");

        sender.send(DATA_TIME);

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥 #마감\n헬스케어 +1.91% / S7 +1.71%");
    }

    @Test
    void 애프터_마켓_별도_등락률_메시지는_첫줄에_애프터마켓_태그를_붙인다() {
        givenTodaySnapshot();
        when(renderBasisResolver.resolve(DATA_TIME)).thenReturn(Optional.of(RenderBasis.AFTER_HOURS));
        when(screenshotClient.captureWithRows(
                        "/map/korea?capture=mobile&basis=afterHours", RenderTarget.MAP_MOBILE.selector(), ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(MAP_IMAGE), ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥 #애프터마켓\n캡션");
    }

    @Test
    void 서버_계산_캡션은_NXT_단독_시간대면_NXT_종목만으로_순위를_만든다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(List.of());
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

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥\n캡션");
    }

    @Test
    void 읽은_값도_계산_순위도_2개_미만이면_캡션_없이_이미지만_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(List.of());
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(any(), eq(2)))
                .thenReturn(null);
        when(treemapQueryService.getMergedSectorRanking(any(), any(), any(), eq(true), anyBoolean()))
                .thenReturn(List.of(new TopSectorItem("금융", BigDecimal.ONE)));

        sender.send(DATA_TIME);

        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥");
    }

    // 시장 지도 캡처가 실패하면 발송하지 않고 개발자 채널에 알린다.
    @Test
    void 시장_지도_캡처가_실패하면_발송하지_않고_에스컬레이션한다() {
        givenTodaySnapshot();
        EscalateException failure = new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        when(screenshotClient.captureWithRows(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()),
                        RenderTarget.MAP_MOBILE.selector(),
                        ROW_SELECTOR))
                .thenThrow(failure);
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(screenshotClient, times(2))
                .captureWithRows(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()),
                        RenderTarget.MAP_MOBILE.selector(),
                        ROW_SELECTOR);
        verify(escalationPublisher).report("국내 지도", failure);
        verify(telegramClient, never()).sendPhoto(any(), any(), any());
        verify(telegramClient, never()).sendMediaGroup(any(), any(), any());
    }

    @Test
    void 시장_지도_캡처가_한_번_실패해도_다시_찍어_성공하면_알리지_않고_보낸다() {
        givenTodaySnapshot();
        when(screenshotClient.captureWithRows(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()),
                        RenderTarget.MAP_MOBILE.selector(),
                        ROW_SELECTOR))
                .thenThrow(new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED))
                .thenReturn(new CaptureResult(List.of(MAP_IMAGE), ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(screenshotClient, times(2))
                .captureWithRows(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()),
                        RenderTarget.MAP_MOBILE.selector(),
                        ROW_SELECTOR);
        verify(escalationPublisher, never()).report(any(), any());
        verify(telegramClient).sendPhoto(any(), eq(MAP_IMAGE), any());
    }

    @Test
    void 시장_지도_캡처가_비어_있어도_한_번_다시_찍는다() {
        givenTodaySnapshot();
        when(screenshotClient.captureWithRows(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()),
                        RenderTarget.MAP_MOBILE.selector(),
                        ROW_SELECTOR))
                .thenReturn(new CaptureResult(List.of(), ROWS))
                .thenReturn(new CaptureResult(List.of(MAP_IMAGE), ROWS));
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(escalationPublisher, never()).report(any(), any());
        verify(telegramClient).sendPhoto(any(), eq(MAP_IMAGE), any());
    }

    @Test
    void 지도에서_등락률을_못_읽고_캡션_계산도_실패해도_지도는_보낸다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(List.of());
        when(treemapQueryService.getMergedSectorRanking(any(), any(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new IllegalStateException("캡션 계산 실패"));

        assertThat(sender.send(DATA_TIME)).isTrue();
        verify(telegramClient).sendPhoto("chat", MAP_IMAGE, "#코스피 #코스닥");
    }

    @Test
    void 국내_발송은_홈_요약_화면을_열지_않고_지도만_한_번_찍는다() {
        givenTodaySnapshot();
        givenKoreaMapCapture(ROWS);
        when(sectorRankingTextBuilder.buildCaptionFromRenderedRows(ROWS, 2)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(screenshotClient, never()).captureWithRows(any(), eq(RenderTarget.HOME_MOBILE.selector()), any());
        verify(screenshotClient, never()).capture(any(), any());
        verify(screenshotClient, times(1))
                .captureWithRows(
                        RenderTarget.MAP_MOBILE.path(Optional.empty()),
                        RenderTarget.MAP_MOBILE.selector(),
                        ROW_SELECTOR);
    }

    @Test
    void 캡처_주소와_캡처_이름은_프론트의_3대4_판과_맞다() {
        assertThat(RenderTarget.HOME_MOBILE.path()).isEqualTo("/home?capture=mobile");
        assertThat(RenderTarget.HOME_MOBILE.selector()).isEqualTo("[data-captureid='summary-capture']");
        assertThat(RenderTarget.MAP_MOBILE.path()).isEqualTo("/map/korea?capture=mobile");
        assertThat(RenderTarget.MAP_MOBILE.selector()).isEqualTo("[data-captureid='map-mobile-capture']");
        assertThat(RenderTarget.JAPAN_MAP_MOBILE.path()).isEqualTo("/map/japan?capture=mobile");
        assertThat(RenderTarget.JAPAN_MAP_MOBILE.selector()).isEqualTo("[data-captureid='map-mobile-capture']");
    }

    @Test
    void 마켓_세그먼트는_프론트_지도_주소와_맞다() {
        // 프론트에 없는 주소(옛 /map/allstock)를 열면 캡처 요소가 없어 스크린샷이 타임아웃으로 실패한다.
        assertThat(RenderTarget.marketSegment(MarketQuery.ALL_STOCK)).isEqualTo("korea");
        assertThat(RenderTarget.marketSegment(MarketQuery.KOSPI)).isEqualTo("kospi");
        assertThat(RenderTarget.marketSegment(MarketQuery.KOSDAQ)).isEqualTo("kosdaq");
        assertThat(RenderTarget.marketSegment(Market.KOSPI)).isEqualTo("kospi");
    }

    @Test
    void 등락률_기준이_없으면_주소는_그대로이고_있으면_기준을_붙인다() {
        assertThat(RenderTarget.HOME_MOBILE.path(Optional.empty())).isEqualTo("/home?capture=mobile");
        assertThat(RenderTarget.HOME_MOBILE.path(Optional.of(RenderBasis.CLOSE)))
                .isEqualTo("/home?capture=mobile&basis=close");
        assertThat(RenderTarget.MAP_MOBILE.path(Optional.of(RenderBasis.AFTER_HOURS)))
                .isEqualTo("/map/korea?capture=mobile&basis=afterHours");
    }
}
