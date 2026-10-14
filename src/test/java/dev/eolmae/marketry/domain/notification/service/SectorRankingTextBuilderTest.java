package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.view.dto.SectorRankingSummary;
import dev.eolmae.marketry.domain.view.dto.TopSectorItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

// TOP3 선정, 대분류 필터, 기본 제외 구간 제외는 TreemapQueryService가 랭킹을 확정할 때 끝낸다.
// 여기서는 확정된 SectorRankingSummary를 텍스트로 조립하는 포맷팅만 검증한다.
class SectorRankingTextBuilderTest {

    private final SectorRankingTextBuilder builder = new SectorRankingTextBuilder();

    @Test
    void buildRankingText_섹터들을_받은_순서_그대로_이어붙인다() {
        SectorRankingSummary summary = new SectorRankingSummary(
                Market.KOSPI,
                null,
                List.of(
                        new TopSectorItem("반도체", BigDecimal.valueOf(10)),
                        new TopSectorItem("화학", BigDecimal.valueOf(5)),
                        new TopSectorItem("자동차", BigDecimal.valueOf(2))));

        String text = builder.buildRankingText(List.of(summary));

        assertThat(text).isEqualTo("#코스피\n반도체 +10.00%p\n화학 +5.00%p\n자동차 +2.00%p");
    }

    @Test
    void buildRankingText_지수_등락률이_있으면_헤더_옆에_붙는다() {
        SectorRankingSummary summary = new SectorRankingSummary(
                Market.KOSPI, BigDecimal.valueOf(-1.23), List.of(new TopSectorItem("반도체", BigDecimal.valueOf(5))));

        String text = builder.buildRankingText(List.of(summary));

        assertThat(text).isEqualTo("#코스피 -1.23%\n반도체 +5.00%p");
    }

    @Test
    void buildRankingText_지수_등락률이_없으면_헤더에_퍼센트를_붙이지_않는다() {
        SectorRankingSummary summary =
                new SectorRankingSummary(Market.KOSPI, null, List.of(new TopSectorItem("반도체", BigDecimal.valueOf(5))));

        String text = builder.buildRankingText(List.of(summary));

        assertThat(text).isEqualTo("#코스피\n반도체 +5.00%p");
    }

    @Test
    void buildRankingText_음수_등락률은_부호없이_마이너스로만_붙는다() {
        SectorRankingSummary summary = new SectorRankingSummary(
                Market.KOSPI, null, List.of(new TopSectorItem("반도체", BigDecimal.valueOf(-12.34))));

        String text = builder.buildRankingText(List.of(summary));

        assertThat(text).isEqualTo("#코스피\n반도체 -12.34%p");
    }

    @Test
    void buildRankingText_여러_마켓은_빈줄로_구분되어_이어붙는다() {
        SectorRankingSummary kospi =
                new SectorRankingSummary(Market.KOSPI, null, List.of(new TopSectorItem("반도체", BigDecimal.valueOf(5))));
        SectorRankingSummary kosdaq =
                new SectorRankingSummary(Market.KOSDAQ, null, List.of(new TopSectorItem("제약", BigDecimal.valueOf(3))));

        String text = builder.buildRankingText(List.of(kospi, kosdaq));

        assertThat(text).isEqualTo("#코스피\n반도체 +5.00%p\n\n#코스닥\n제약 +3.00%p");
    }

    @Test
    void buildSectorCaption_헤더에_대괄호와_N분_전_대비_라벨을_붙인다() {
        SectorRankingSummary summary = new SectorRankingSummary(
                Market.KOSPI,
                BigDecimal.valueOf(-1.23),
                List.of(
                        new TopSectorItem("제약", BigDecimal.valueOf(2.1)),
                        new TopSectorItem("운송", BigDecimal.valueOf(1.75))));

        String text = builder.buildSectorCaption(summary, 15);

        assertThat(text).isEqualTo("[#코스피 15분 전 대비]\n제약 +2.10%p\n운송 +1.75%p");
    }

    @Test
    void buildSectorCaption_지수_등락률이_있어도_헤더에는_안_붙인다() {
        SectorRankingSummary summary = new SectorRankingSummary(
                Market.KOSDAQ, BigDecimal.valueOf(3.0), List.of(new TopSectorItem("반도체", BigDecimal.valueOf(1))));

        String text = builder.buildSectorCaption(summary, 30);

        assertThat(text).isEqualTo("[#코스닥 30분 전 대비]\n반도체 +1.00%p");
    }

    @Test
    void buildSectorFallbackCaption_헤더가_등락률_라벨이고_단위는_퍼센트다() {
        SectorRankingSummary summary = new SectorRankingSummary(
                Market.KOSPI,
                null,
                List.of(
                        new TopSectorItem("반도체", BigDecimal.valueOf(2.1)),
                        new TopSectorItem("화학", BigDecimal.valueOf(-1.5))));

        String text = builder.buildSectorFallbackCaption(summary);

        assertThat(text).isEqualTo("[#코스피 섹터 등락률]\n반도체 +2.10%\n화학 -1.50%");
    }

    @Test
    void buildMapCaption_마켓_구분_없이_두_줄로_이어붙인다() {
        List<TopSectorItem> topSectors = List.of(
                new TopSectorItem("반도체", BigDecimal.valueOf(1.35)), new TopSectorItem("제약", BigDecimal.valueOf(1.20)));

        String text = builder.buildMapCaption(topSectors);

        assertThat(text).isEqualTo("[#코스피 / #코스닥 섹터 등락률]\n반도체 +1.35%\n제약 +1.20%");
    }
}
