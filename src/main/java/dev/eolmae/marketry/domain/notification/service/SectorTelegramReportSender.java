package dev.eolmae.marketry.domain.notification.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.domain.notification.client.TelegramClient;
import dev.eolmae.marketry.domain.notification.enums.RenderTarget;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketry.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketry.domain.view.dto.SectorRankingSummary;
import dev.eolmae.marketry.domain.view.enums.AverageMode;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.TreemapQueryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 두 마켓(KOSPI/KOSDAQ)의 섹터 등락률 이미지를 마켓별로 각각 별도 메시지로 보낸다 — 맵을
 * 포함하지 않는 유일한 발송 경로다. {@link TelegramReportSender}를 상속하지 않는 독립 컴포넌트다.
 * 그 부모의 send()는 캡처 URL을 "?market=" 값만 붙여 만들고 "&beforeMinutes="를 붙이지 않으므로,
 * 상속하면 부모 send()를 통째로 오버라이드해야 해서 상속으로 얻는 것이 없다. 더 나쁜 것은 오버라이드를
 * 빠뜨렸을 때 컴파일도 테스트도 안 깨진 채 이미지가 프론트 기본값(30분) 기준으로 나간다는 점이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SectorTelegramReportSender {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final SectorRankingTextBuilder sectorRankingTextBuilder;
    private final TreemapQueryService treemapQueryService;

    public void send(LocalDateTime dataTime) {
        int beforeMinutes = telegramProperties.beforeMinutes();
        AverageMode averageMode = telegramProperties.averageMode();
        boolean sectorFilter = telegramProperties.sectorFilter();
        List<SectorRankingSummary> deltaRankings = treemapQueryService.getTopSectorRankings(
                MarketQuery.ALL_STOCK, dataTime, beforeMinutes, averageMode, sectorFilter);
        if (deltaRankings.isEmpty()) {
            log.warn("{} 시각 섹터 등락률 랭킹 조회가 비어 있어 섹터 발송을 건너뜀", dataTime);
            return;
        }

        // 결과에 있는 마켓만 보낸다 — getTopSectorRankings가 그 시각 데이터 없는 마켓을 이미 결과에서
        // 뺀다. 목록을 상수로 박아두면 데이터 없는 마켓의 빈 화면을 보내려다 실패한다.
        // changeRateRankings는 매일 첫 발송(08:10, before 없음)처럼 어느 마켓이든 변화율을 못 구할 때만
        // 필요하므로, 실제로 필요해질 때 한 번만 조회한다(하루 한 번 일어나는 추가 조회).
        List<SectorRankingSummary> changeRateRankings = null;
        int sentCount = 0;
        for (SectorRankingSummary delta : deltaRankings) {
            SectorRankingSummary summaryToSend = delta;
            boolean isFallback = false;
            if (delta.topSectors().isEmpty()) {
                if (changeRateRankings == null) {
                    changeRateRankings = treemapQueryService.getTopSectorRankingsByChangeRate(
                            MarketQuery.ALL_STOCK, dataTime, beforeMinutes, averageMode, sectorFilter);
                }
                SectorRankingSummary fallback = findByMarket(changeRateRankings, delta.market());
                if (fallback == null || fallback.topSectors().isEmpty()) {
                    log.warn("{} 시각 {} 마켓은 변화율·등락률 랭킹이 모두 비어 있어 발송을 건너뜀", dataTime, delta.market());
                    continue;
                }
                summaryToSend = fallback;
                isFallback = true;
            }
            sentCount += sendOneMarket(summaryToSend, beforeMinutes, averageMode, sectorFilter, isFallback);
        }

        // 한 장도 못 보냈으면 캡처가 통째로 빈 것이다. 마켓별로 나눠 보내기 전에는 빈 목록이
        // sendMediaGroup으로 넘어가 텔레그램 400으로 터지고 에스컬레이션까지 갔는데, 지금 구조에서는
        // 루프가 0회 돌 뿐이라 아무 흔적이 남지 않는다. 그 자리를 이 예외가 메운다.
        if (sentCount == 0) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        }
        // rankings.size()가 아니라 실제로 보낸 건수를 찍는다 — 한쪽 마켓 캡처만 비면 둘이 달라진다.
        log.info("섹터 리포트 발송 완료: 메시지={}건", sentCount);
    }

    private SectorRankingSummary findByMarket(List<SectorRankingSummary> rankings, Market market) {
        return rankings.stream()
                .filter(ranking -> ranking.market() == market)
                .findFirst()
                .orElse(null);
    }

    /** 보낸 메시지 건수를 돌려준다 — 호출부가 "하나도 못 보냈는가"를 판정하는 근거다. */
    private int sendOneMarket(
            SectorRankingSummary summary,
            int beforeMinutes,
            AverageMode averageMode,
            boolean sectorFilter,
            boolean isFallback) {
        List<byte[]> images = screenshotClient.capture(
                sectorPath(summary.market(), beforeMinutes, averageMode, sectorFilter), RenderTarget.SECTOR.selector());

        // ScreenshotClient.capture는 응답의 images가 null일 때만 던지고 빈 배열은 그대로 돌려준다.
        // 셀렉터가 아무것도 못 찾는 경우(예: 프론트가 에러 화면을 그려 capture 대상 요소가 없음)가
        // 여기 걸린다. 다른 마켓은 성공했을 수 있으므로 여기서 던지지 않고 호출부가 합계로 판정한다.
        if (images.isEmpty()) {
            log.warn("{} 섹터 캡처 결과가 비어 있어 발송을 건너뜀", summary.market());
            return 0;
        }

        String caption = isFallback
                ? sectorRankingTextBuilder.buildSectorFallbackCaption(summary)
                : sectorRankingTextBuilder.buildSectorCaption(summary, beforeMinutes);
        for (byte[] image : images) {
            telegramClient.sendPhoto(telegramProperties.chatId(), image, caption);
        }
        return images.size();
    }

    private String sectorPath(Market market, int beforeMinutes, AverageMode averageMode, boolean sectorFilter) {
        return RenderTarget.SECTOR.path() + "/" + RenderTarget.marketSegment(market)
                + "?beforeMinutes=" + beforeMinutes
                + "&avgMode=" + averageMode.queryValue()
                + "&sectorFilter=" + sectorFilter;
    }
}
