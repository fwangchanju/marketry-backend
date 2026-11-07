package kr.co.marketry.domain.stock.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class KiwoomValueParserTest {

    @Test
    void parseBigDecimal_콤마가_제거되고_파싱된다() {
        assertThat(KiwoomValueParser.parseBigDecimal("1,234.56")).isEqualByComparingTo("1234.56");
    }

    @Test
    void parseBigDecimal_무데이터_마커는_0을_반환한다() {
        assertThat(KiwoomValueParser.parseBigDecimal("-")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseBigDecimal_이중_음수부호는_하나로_정규화된다() {
        assertThat(KiwoomValueParser.parseBigDecimal("--1234.56")).isEqualByComparingTo("-1234.56");
    }

    @Test
    void parseLong_콤마가_제거되고_파싱된다() {
        assertThat(KiwoomValueParser.parseLong("1,234,567")).isEqualTo(1234567L);
    }

    @Test
    void parseLong_무데이터_마커는_0을_반환한다() {
        assertThat(KiwoomValueParser.parseLong("-")).isZero();
    }

    @Test
    void parseInt_콤마가_제거되고_파싱된다() {
        assertThat(KiwoomValueParser.parseInt("12,345")).isEqualTo(12345);
    }

    @Test
    void parseInt_무데이터_마커는_0을_반환한다() {
        assertThat(KiwoomValueParser.parseInt("-")).isZero();
    }
}
