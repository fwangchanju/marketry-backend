package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.domain.notification.client.TelegramClient;
import dev.eolmae.marketry.domain.notification.enums.RenderTarget;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketry.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketry.domain.view.dto.SectorRankingSummary;
import dev.eolmae.marketry.domain.view.dto.TopSectorItem;
import dev.eolmae.marketry.domain.view.enums.AverageMode;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.TreemapQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SectorTelegramReportSenderTest {

    private static final int BEFORE_MINUTES = 15;
    private static final List<LocalTime> MAP_SEND_TIMES = List.of(LocalTime.of(8, 15));

    private final ScreenshotClient screenshotClient = Mockito.mock(ScreenshotClient.class);
    private final TelegramClient telegramClient = Mockito.mock(TelegramClient.class);
    private final TelegramProperties telegramProperties = new TelegramProperties(
            "token",
            "chat-id",
            "dev-chat",
            10,
            BEFORE_MINUTES,
            BEFORE_MINUTES,
            AverageMode.SIMPLE,
            true,
            MAP_SEND_TIMES);
    private final SectorRankingTextBuilder sectorRankingTextBuilder = Mockito.mock(SectorRankingTextBuilder.class);
    private final TreemapQueryService treemapQueryService = Mockito.mock(TreemapQueryService.class);
    private final SectorTelegramReportSender sender = new SectorTelegramReportSender(
            screenshotClient, telegramClient, telegramProperties, sectorRankingTextBuilder, treemapQueryService);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 8, 25);
    private final byte[] kospiImage = {1};
    private final byte[] kosdaqImage = {2};
    private final BigDecimal indexChangeRate = BigDecimal.valueOf(1.23);

    @Test
    void send_랭킹_결과가_통째로_비면_캡처도_발송도_하지_않는다() {
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of());

        sender.send(dataTime);

        verifyNoInteractions(screenshotClient);
        verifyNoInteractions(telegramClient);
    }

    @Test
    void send_두_마켓_다_있으면_마켓별로_각각_sendPhoto_한다() {
        SectorRankingSummary kospiSummary =
                new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of(topSectorItem()));
        SectorRankingSummary kosdaqSummary =
                new SectorRankingSummary(Market.KOSDAQ, indexChangeRate, List.of(topSectorItem()));
        List<SectorRankingSummary> rankings = List.of(kospiSummary, kosdaqSummary);
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(rankings);
        when(screenshotClient.capture(
                        "/sector/kospi?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of(kospiImage));
        when(screenshotClient.capture(
                        "/sector/kosdaq?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of(kosdaqImage));
        when(sectorRankingTextBuilder.buildSectorCaption(kospiSummary, BEFORE_MINUTES))
                .thenReturn("[#코스피 15분 전 대비]\n...");
        when(sectorRankingTextBuilder.buildSectorCaption(kosdaqSummary, BEFORE_MINUTES))
                .thenReturn("[#코스닥 15분 전 대비]\n...");

        sender.send(dataTime);

        verify(telegramClient, never()).sendMediaGroup(Mockito.any(), Mockito.any(), Mockito.any());
        ArgumentCaptor<byte[]> imageCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> captionCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, Mockito.times(2))
                .sendPhoto(Mockito.eq("chat-id"), imageCaptor.capture(), captionCaptor.capture());
        assertThat(imageCaptor.getAllValues()).containsExactly(kospiImage, kosdaqImage);
        assertThat(captionCaptor.getAllValues()).containsExactly("[#코스피 15분 전 대비]\n...", "[#코스닥 15분 전 대비]\n...");
    }

    @Test
    void send_한_마켓만_있으면_그_마켓만_캡처해서_보낸다() {
        SectorRankingSummary kospiSummary =
                new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of(topSectorItem()));
        List<SectorRankingSummary> rankings = List.of(kospiSummary);
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(rankings);
        when(screenshotClient.capture(
                        "/sector/kospi?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of(kospiImage));
        when(sectorRankingTextBuilder.buildSectorCaption(kospiSummary, BEFORE_MINUTES))
                .thenReturn("[#코스피 15분 전 대비]\n...");

        sender.send(dataTime);

        verify(screenshotClient, never()).capture(Mockito.contains("KOSDAQ"), Mockito.any());
        verify(telegramClient)
                .sendPhoto(Mockito.eq("chat-id"), Mockito.eq(kospiImage), Mockito.eq("[#코스피 15분 전 대비]\n..."));
        verify(telegramClient, Mockito.times(1)).sendPhoto(Mockito.any(), Mockito.any(), Mockito.any());
    }

    // 캡처가 통째로 비는 것은 마켓별 분리 이전에는 텔레그램 400으로 드러나던 상황이다. 지금은 발송
    // 루프가 0회 돌 뿐이라, 이 예외가 없으면 아무 흔적 없이 그 tick이 사라진다.
    @Test
    void send_캡처가_통째로_비면_에스컬레이션한다() {
        SectorRankingSummary kospiSummary =
                new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of(topSectorItem()));
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of(kospiSummary));
        when(screenshotClient.capture(
                        "/sector/kospi?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> sender.send(dataTime)).isInstanceOf(EscalateException.class);

        verifyNoInteractions(telegramClient);
    }

    @Test
    void send_한_마켓_캡처만_비면_나머지_마켓은_그대로_보낸다() {
        SectorRankingSummary kospiSummary =
                new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of(topSectorItem()));
        SectorRankingSummary kosdaqSummary =
                new SectorRankingSummary(Market.KOSDAQ, indexChangeRate, List.of(topSectorItem()));
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of(kospiSummary, kosdaqSummary));
        when(screenshotClient.capture(
                        "/sector/kospi?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of());
        when(screenshotClient.capture(
                        "/sector/kosdaq?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of(kosdaqImage));
        when(sectorRankingTextBuilder.buildSectorCaption(kosdaqSummary, BEFORE_MINUTES))
                .thenReturn("[#코스닥 15분 전 대비]\n...");

        sender.send(dataTime);

        verify(telegramClient)
                .sendPhoto(Mockito.eq("chat-id"), Mockito.eq(kosdaqImage), Mockito.eq("[#코스닥 15분 전 대비]\n..."));
        verify(telegramClient, Mockito.times(1)).sendPhoto(Mockito.any(), Mockito.any(), Mockito.any());
    }

    // 매일 첫 발송(08:10)처럼 before가 없어 변화율(%p) 랭킹이 비면, 등락률(now, %) 랭킹으로 대체해서
    // buildSectorFallbackCaption으로 보낸다.
    @Test
    void send_델타_랭킹이_비어있으면_등락률_랭킹으로_폴백한다() {
        SectorRankingSummary kospiDeltaEmpty = new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of());
        SectorRankingSummary kosdaqDelta =
                new SectorRankingSummary(Market.KOSDAQ, indexChangeRate, List.of(topSectorItem()));
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of(kospiDeltaEmpty, kosdaqDelta));

        SectorRankingSummary kospiFallback =
                new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of(topSectorItem()));
        when(treemapQueryService.getTopSectorRankingsByChangeRate(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of(kospiFallback));

        when(screenshotClient.capture(
                        "/sector/kospi?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of(kospiImage));
        when(screenshotClient.capture(
                        "/sector/kosdaq?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of(kosdaqImage));
        when(sectorRankingTextBuilder.buildSectorFallbackCaption(kospiFallback)).thenReturn("[#코스피 섹터 등락률]\n...");
        when(sectorRankingTextBuilder.buildSectorCaption(kosdaqDelta, BEFORE_MINUTES))
                .thenReturn("[#코스닥 15분 전 대비]\n...");

        sender.send(dataTime);

        verify(telegramClient)
                .sendPhoto(Mockito.eq("chat-id"), Mockito.eq(kospiImage), Mockito.eq("[#코스피 섹터 등락률]\n..."));
        verify(telegramClient)
                .sendPhoto(Mockito.eq("chat-id"), Mockito.eq(kosdaqImage), Mockito.eq("[#코스닥 15분 전 대비]\n..."));
        verify(sectorRankingTextBuilder, never()).buildSectorCaption(kospiDeltaEmpty, BEFORE_MINUTES);
    }

    // 등락률 랭킹마저 비면 그 마켓만 건너뛰고 나머지는 그대로 보낸다 — rankings.isEmpty() 가드와 같은
    // 자리다.
    @Test
    void send_델타와_등락률_랭킹이_모두_비면_그_마켓만_건너뛴다() {
        SectorRankingSummary kospiDeltaEmpty = new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of());
        SectorRankingSummary kosdaqDelta =
                new SectorRankingSummary(Market.KOSDAQ, indexChangeRate, List.of(topSectorItem()));
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of(kospiDeltaEmpty, kosdaqDelta));

        SectorRankingSummary kospiFallbackAlsoEmpty =
                new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of());
        when(treemapQueryService.getTopSectorRankingsByChangeRate(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of(kospiFallbackAlsoEmpty));

        when(screenshotClient.capture(
                        "/sector/kosdaq?beforeMinutes=15&avgMode=simple&sectorFilter=true",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of(kosdaqImage));
        when(sectorRankingTextBuilder.buildSectorCaption(kosdaqDelta, BEFORE_MINUTES))
                .thenReturn("[#코스닥 15분 전 대비]\n...");

        sender.send(dataTime);

        verify(screenshotClient, never()).capture(Mockito.contains("KOSPI"), Mockito.any());
        verify(telegramClient, Mockito.times(1)).sendPhoto(Mockito.any(), Mockito.any(), Mockito.any());
    }

    // beforeMinutes분 전 데이터가 없는 tick은 보통 두 마켓 모두 한꺼번에 그렇다 — 마켓마다 다시 조회하면
    // 하루 한 번이어야 할 추가 조회가 마켓 수만큼 늘어난다.
    @Test
    void send_변화율_폴백_조회는_한_번만_한다() {
        SectorRankingSummary kospiDeltaEmpty = new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of());
        SectorRankingSummary kosdaqDeltaEmpty = new SectorRankingSummary(Market.KOSDAQ, indexChangeRate, List.of());
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of(kospiDeltaEmpty, kosdaqDeltaEmpty));

        SectorRankingSummary kospiFallback =
                new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of(topSectorItem()));
        SectorRankingSummary kosdaqFallback =
                new SectorRankingSummary(Market.KOSDAQ, indexChangeRate, List.of(topSectorItem()));
        when(treemapQueryService.getTopSectorRankingsByChangeRate(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true))
                .thenReturn(List.of(kospiFallback, kosdaqFallback));

        when(screenshotClient.capture(Mockito.contains("/sector/kospi"), Mockito.any()))
                .thenReturn(List.of(kospiImage));
        when(screenshotClient.capture(Mockito.contains("/sector/kosdaq"), Mockito.any()))
                .thenReturn(List.of(kosdaqImage));

        sender.send(dataTime);

        verify(treemapQueryService, Mockito.times(1))
                .getTopSectorRankingsByChangeRate(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.SIMPLE, true);
    }

    // 프로퍼티 값이 캡처 URL과 캡션 계산 양쪽에 같은 값으로 들어가는지 — 기본값(SIMPLE/true)이 아닌
    // 값(WEIGHTED/false)으로 바꿔도 둘이 같이 바뀌어야 한다.
    @Test
    void send_프로퍼티가_WEIGHTED와_sectorFilter_false면_캡처_URL과_캡션_계산에_그대로_반영된다() {
        TelegramProperties weightedProperties = new TelegramProperties(
                "token",
                "chat-id",
                "dev-chat",
                10,
                BEFORE_MINUTES,
                BEFORE_MINUTES,
                AverageMode.WEIGHTED,
                false,
                MAP_SEND_TIMES);
        SectorTelegramReportSender weightedSender = new SectorTelegramReportSender(
                screenshotClient, telegramClient, weightedProperties, sectorRankingTextBuilder, treemapQueryService);
        SectorRankingSummary kospiSummary =
                new SectorRankingSummary(Market.KOSPI, indexChangeRate, List.of(topSectorItem()));
        when(treemapQueryService.getTopSectorRankings(
                        MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES, AverageMode.WEIGHTED, false))
                .thenReturn(List.of(kospiSummary));
        when(screenshotClient.capture(
                        "/sector/kospi?beforeMinutes=15&avgMode=weighted&sectorFilter=false",
                        RenderTarget.SECTOR.selector()))
                .thenReturn(List.of(kospiImage));
        when(sectorRankingTextBuilder.buildSectorCaption(kospiSummary, BEFORE_MINUTES))
                .thenReturn("[#코스피 15분 전 대비]\n...");

        weightedSender.send(dataTime);

        verify(telegramClient)
                .sendPhoto(Mockito.eq("chat-id"), Mockito.eq(kospiImage), Mockito.eq("[#코스피 15분 전 대비]\n..."));
    }

    private TopSectorItem topSectorItem() {
        return new TopSectorItem("반도체", BigDecimal.valueOf(3.21));
    }
}
