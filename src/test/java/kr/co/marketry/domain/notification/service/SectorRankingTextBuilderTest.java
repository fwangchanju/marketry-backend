package kr.co.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.view.dto.SectorRankingSummary;
import kr.co.marketry.domain.view.dto.TopSectorItem;
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
    void buildMapCaption_헤더와_마켓_구분_없이_상위_업종을_각_줄에_보인다() {
        List<TopSectorItem> topSectors = List.of(
                new TopSectorItem("반도체", BigDecimal.valueOf(1.35)), new TopSectorItem("제약", BigDecimal.valueOf(1.20)));

        String text = builder.buildMapCaption(topSectors);

        assertThat(text).isEqualTo("반도체 +1.4%\n제약 +1.2%");
    }

    @Test
    void buildMapCaption_반올림해도_작은_등락의_방향을_표시한다() {
        List<TopSectorItem> topSectors = List.of(
                new TopSectorItem("상승", new BigDecimal("0.04")),
                new TopSectorItem("보합", BigDecimal.ZERO),
                new TopSectorItem("하락", new BigDecimal("-0.04")));

        assertThat(builder.buildMapCaption(topSectors)).isEqualTo("상승 +0.0%\n보합 0.0%\n하락 -0.0%");
    }

    @Test
    void buildCaptionFromRenderedRows_이미지의_값으로_상위_업종을_등락률_높은_순으로_각_줄에_보인다() {
        List<String> rows = List.of(
                "{\"name\":\"소재\",\"value\":\"-3.35\"}",
                "{\"name\":\"헬스케어\",\"value\":\"+1.91\"}",
                "{\"name\":\"S7\",\"value\":\"+1.71\"}",
                "{\"name\":\"에너지화학\",\"value\":\"-3.49\"}");

        String text = builder.buildCaptionFromRenderedRows(rows, 2);

        // 이미지에 적힌 숫자 그대로(+1.91)이고 등락률이 가장 높은 두 업종이다.
        assertThat(text).isEqualTo("헬스케어 +1.91%\nS7 +1.71%");
    }

    @Test
    void buildCaptionFromRenderedRows_음수만_있어도_덜_떨어진_업종이_위다() {
        List<String> rows = List.of(
                "{\"name\":\"금융\",\"value\":\"-2.07\"}",
                "{\"name\":\"필수소비재\",\"value\":\"-0.38\"}",
                "{\"name\":\"헬스케어\",\"value\":\"-2.91\"}");

        assertThat(builder.buildCaptionFromRenderedRows(rows, 2)).isEqualTo("필수소비재 -0.38%\n금융 -2.07%");
    }

    @Test
    void buildCaptionFromRenderedRows_행이_모자라거나_읽을_수_없으면_null이다() {
        assertThat(builder.buildCaptionFromRenderedRows(List.of(), 2)).isNull();
        assertThat(builder.buildCaptionFromRenderedRows(List.of("{\"name\":\"금융\",\"value\":\"-2.07\"}"), 2))
                .isNull();
        assertThat(builder.buildCaptionFromRenderedRows(List.of("{\"name\":\"금융\",\"value\":\"-2.07\"}", "깨진 값"), 2))
                .isNull();
    }
}
