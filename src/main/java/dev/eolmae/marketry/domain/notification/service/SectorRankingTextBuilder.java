package dev.eolmae.marketry.domain.notification.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.util.MarketLabels;
import dev.eolmae.marketry.domain.view.dto.SectorRankingSummary;
import dev.eolmae.marketry.domain.view.dto.TopSectorItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 섹터 등락률 TOP2 랭킹 텍스트 조립 — 랭킹 확정(필터·정렬·TOP2)은 전부 TreemapQueryService가
// 끝낸 뒤 넘겨주므로, 여기는 헤더 조립과 퍼센트 포맷만 한다. 조회 의존성은 없다.
@Component
public class SectorRankingTextBuilder {

    private static final String MAP_CAPTION_HEADER = "[#코스피 / #코스닥 섹터 등락률]";

    /** summaries가 담고 있는 마켓 각각을 "#코스피 +x.xx%\n섹터 +x.xx%\n\n#코스닥 +x.xx%\n.." 형태로
     * 이어붙인다(헤더 옆 퍼센트는 그 마켓 지수의 등락률). 데이터 없는 마켓은 summaries에 이미 없는 상태라
     * 자동으로 텍스트에서도 빠진다. 헤더("Custom Sector" 등)는 안 붙이므로 호출부가 자기 맥락에 맞는
     * 헤더를 붙여 쓴다. */
    public String buildRankingText(List<SectorRankingSummary> summaries) {
        return summaries.stream()
                .map(summary -> buildHeader(summary.market(), summary.indexChangeRate()) + "\n"
                        + summary.topSectors().stream()
                                .map(this::formatTopSectorDelta)
                                .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));
    }

    /** summary 하나(마켓 하나)를 "[#코스피 15분 전 대비]\n섹터 +x.xx%p\n.." 형태로 만든다. 마켓별로
     * 개별 메시지를 보내는 섹터 전용 발송(SectorTelegramReportSender)의 평상시 tick에서만 쓴다 — 지수
     * 등락률 대신 beforeMinutes로 "N분 전 대비" 라벨을 헤더에 붙이고, 본문 값도 그 라벨에 맞는 %p
     * 차이다(buildRankingText의 현재 등락률 %와 단위가 다르다). before가 없어 topSectors가 비는
     * 경우(매일 첫 발송)는 호출부가 이 메서드 대신 buildSectorFallbackCaption을 쓰므로, 여기서는 항상
     * 채워져 있다고 가정한다. */
    public String buildSectorCaption(SectorRankingSummary summary, int beforeMinutes) {
        String header = "[#" + MarketLabels.toKorean(summary.market()) + " " + beforeMinutes + "분 전 대비]";
        String body =
                summary.topSectors().stream().map(this::formatTopSectorDelta).collect(Collectors.joining("\n"));
        return header + "\n" + body;
    }

    /** 매일 첫 발송(08:10)처럼 beforeMinutes분 전 데이터가 없어 변화율(%p)을 못 구할 때의 폴백 캡션 —
     * 등락률(now) 기준 TOP2를 "[#코스피 섹터 등락률]" 헤더로 보여준다. 단위는 %다(%p 아님). 헤더를
     * buildSectorCaption과 다르게 가는 이유는, "15분 전 대비"라고 적어놓고 내용이 등락률이면 방금 고친
     * 것과 같은 라벨-단위 어긋남이 다시 생기기 때문이다. */
    public String buildSectorFallbackCaption(SectorRankingSummary summary) {
        String header = "[#" + MarketLabels.toKorean(summary.market()) + " 섹터 등락률]";
        String body = summary.topSectors().stream()
                .map(this::formatTopSectorChangeRate)
                .collect(Collectors.joining("\n"));
        return header + "\n" + body;
    }

    /** 맵 발송(코스피+코스닥 앨범) 캡션 — 두 마켓을 합친 전체 기준 TOP2를 "[#코스피 / #코스닥 섹터
     * 등락률]" 헤더 아래 두 줄로 보여준다. 마켓 구분 줄은 안 붙인다(헤더에 이미 명시돼 있다). 단위는
     * 등락률(now) 그대로라 %다(%p 아님) — buildSectorCaption의 변화율과 다르다. */
    public String buildMapCaption(List<TopSectorItem> topSectors) {
        String body = topSectors.stream().map(this::formatTopSectorChangeRate).collect(Collectors.joining("\n"));
        return MAP_CAPTION_HEADER + "\n" + body;
    }

    // TopSectorItem.changeRate는 "N분 전 대비 변화"라 단위가 %가 아니라 %p다.
    private String formatTopSectorDelta(TopSectorItem top) {
        return top.sectorName() + " " + formatPercent(top.changeRate()) + "p";
    }

    // 등락률(now) 기준일 때는 단위가 %다 — formatTopSectorDelta와 값의 의미 자체가 다르다.
    private String formatTopSectorChangeRate(TopSectorItem top) {
        return top.sectorName() + " " + formatPercent(top.changeRate());
    }

    private String buildHeader(Market market, BigDecimal indexChangeRate) {
        String header = "#" + MarketLabels.toKorean(market);
        if (indexChangeRate != null) {
            header += " " + formatPercent(indexChangeRate);
        }
        return header;
    }

    private String formatPercent(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        String sign = rounded.signum() > 0 ? "+" : "";
        return sign + rounded.toPlainString() + "%";
    }
}
