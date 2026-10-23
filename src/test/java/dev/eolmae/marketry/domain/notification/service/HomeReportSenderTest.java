package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.domain.notification.client.TelegramClient;
import dev.eolmae.marketry.domain.notification.enums.RenderTarget;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketry.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.view.dto.TopSectorItem;
import dev.eolmae.marketry.domain.view.enums.AverageMode;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.MarketTradingScheduleService;
import dev.eolmae.marketry.domain.view.service.TreemapQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HomeReportSenderTest {

    private static final LocalDateTime DATA_TIME = LocalDateTime.of(2026, 10, 22, 8, 5);
    private static final byte[] IMAGE = {1, 2, 3};

    private final ScreenshotClient screenshotClient = mock(ScreenshotClient.class);
    private final TelegramClient telegramClient = mock(TelegramClient.class);
    private final SectorRankingTextBuilder sectorRankingTextBuilder = mock(SectorRankingTextBuilder.class);
    private final TreemapQueryService treemapQueryService = mock(TreemapQueryService.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService = mock(SectorPriceSnapshotService.class);
    private final MarketTradingScheduleService marketTradingScheduleService = mock(MarketTradingScheduleService.class);
    private final TelegramProperties properties = new TelegramProperties(
            "token", "chat", "developer", 5, 30, 15, AverageMode.SIMPLE, true, List.of(LocalTime.of(15, 30)));
    private final HomeReportSender sender = new HomeReportSender(
            screenshotClient,
            telegramClient,
            properties,
            sectorRankingTextBuilder,
            treemapQueryService,
            sectorPriceSnapshotService,
            marketTradingScheduleService);

    @Test
    void 오늘의_공통_스냅샷이_없으면_찍지도_보내지도_않는다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.empty());

        sender.send(DATA_TIME);

        verify(screenshotClient, never()).capture(any(), any());
        verify(telegramClient, never()).sendPhoto(any(), any(), any());
    }

    @Test
    void 이전_날짜의_스냅샷만_있으면_보내지_않는다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList()))
                .thenReturn(Optional.of(DATA_TIME.minusDays(1)));

        sender.send(DATA_TIME);

        verify(screenshotClient, never()).capture(any(), any());
    }

    @Test
    void 홈_3대4_판을_찍고_상위_하위_그룹_캡션과_함께_한_장으로_보낸다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.of(DATA_TIME));
        when(screenshotClient.capture(RenderTarget.HOME_MOBILE.path(), RenderTarget.HOME_MOBILE.selector()))
                .thenReturn(List.of(IMAGE));
        TopSectorItem top = new TopSectorItem("필수소비재", new BigDecimal("-0.38"));
        TopSectorItem middle = new TopSectorItem("금융", new BigDecimal("-2.07"));
        TopSectorItem bottom = new TopSectorItem("신규 상장", new BigDecimal("-31.93"));
        when(treemapQueryService.getMergedSectorRanking(
                        eq(MarketQuery.ALL_STOCK), eq(DATA_TIME), eq(AverageMode.SIMPLE), eq(true), eq(false)))
                .thenReturn(List.of(top, middle, bottom));
        when(sectorRankingTextBuilder.buildHomeCaption(top, bottom)).thenReturn("캡션");

        sender.send(DATA_TIME);

        // 랭킹의 맨 위가 상위, 맨 아래가 하위다.
        verify(telegramClient).sendPhoto("chat", IMAGE, "캡션");
    }

    // 프리·애프터 마켓에는 화면이 NXT 종목만 보여 주므로, 캡션도 같은 종목으로 순위를 만들어야 이미지와 숫자가 맞는다.
    @Test
    void NXT_단독_시간대면_NXT_종목만으로_캡션_순위를_만든다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.of(DATA_TIME));
        when(screenshotClient.capture(any(), any())).thenReturn(List.of(IMAGE));
        when(marketTradingScheduleService.isNxtOnly(DATA_TIME)).thenReturn(true);
        TopSectorItem top = new TopSectorItem("헬스케어", new BigDecimal("1.91"));
        TopSectorItem bottom = new TopSectorItem("에너지화학", new BigDecimal("-3.49"));
        when(treemapQueryService.getMergedSectorRanking(
                        eq(MarketQuery.ALL_STOCK), eq(DATA_TIME), eq(AverageMode.SIMPLE), eq(true), eq(true)))
                .thenReturn(List.of(top, bottom));
        when(sectorRankingTextBuilder.buildHomeCaption(top, bottom)).thenReturn("캡션");

        sender.send(DATA_TIME);

        verify(telegramClient).sendPhoto("chat", IMAGE, "캡션");
    }

    @Test
    void 순위가_2개_미만이면_캡션만_버리고_이미지는_보낸다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.of(DATA_TIME));
        when(screenshotClient.capture(any(), any())).thenReturn(List.of(IMAGE));
        when(treemapQueryService.getMergedSectorRanking(any(), any(), any(), eq(true), anyBoolean()))
                .thenReturn(List.of(new TopSectorItem("금융", BigDecimal.ONE)));

        sender.send(DATA_TIME);

        verify(telegramClient).sendPhoto("chat", IMAGE, null);
    }

    @Test
    void 캡처가_비어_있으면_에스컬레이션한다() {
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(anyList())).thenReturn(Optional.of(DATA_TIME));
        when(screenshotClient.capture(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> sender.send(DATA_TIME)).isInstanceOf(EscalateException.class);
        verify(telegramClient, never()).sendPhoto(any(), any(), any());
    }

    @Test
    void 홈_캡처는_프론트의_3대4_판_주소와_캡처_이름을_쓴다() {
        org.assertj.core.api.Assertions.assertThat(RenderTarget.HOME_MOBILE.path())
                .isEqualTo("/home?capture=mobile");
        org.assertj.core.api.Assertions.assertThat(RenderTarget.HOME_MOBILE.selector())
                .isEqualTo("[data-captureid='summary-capture']");
    }
}
