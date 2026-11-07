package kr.co.marketry.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import kr.co.marketry.common.enums.Market;
import org.junit.jupiter.api.Test;

class MarketLabelsTest {

    @Test
    void toKorean_KOSPI는_코스피를_반환한다() {
        assertThat(MarketLabels.toKorean(Market.KOSPI)).isEqualTo("코스피");
    }

    @Test
    void toKorean_KOSDAQ는_코스닥을_반환한다() {
        assertThat(MarketLabels.toKorean(Market.KOSDAQ)).isEqualTo("코스닥");
    }
}
