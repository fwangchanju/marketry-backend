package kr.co.marketry.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NumberParserTest {

    @Test
    void parseBigDecimal_정상값은_그대로_파싱된다() {
        assertThat(NumberParser.parseBigDecimal("1234.56")).isEqualByComparingTo("1234.56");
    }

    @Test
    void parseBigDecimal_null이면_0을_반환한다() {
        assertThat(NumberParser.parseBigDecimal(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseBigDecimal_빈값이면_0을_반환한다() {
        assertThat(NumberParser.parseBigDecimal("  ")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseBigDecimal_숫자가_아니면_0을_반환한다() {
        assertThat(NumberParser.parseBigDecimal("abc")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseLong_정상값은_그대로_파싱된다() {
        assertThat(NumberParser.parseLong("1234")).isEqualTo(1234L);
    }

    @Test
    void parseLong_숫자가_아니면_0을_반환한다() {
        assertThat(NumberParser.parseLong("abc")).isZero();
    }

    @Test
    void parseInt_정상값은_그대로_파싱된다() {
        assertThat(NumberParser.parseInt("1234")).isEqualTo(1234);
    }

    @Test
    void parseInt_숫자가_아니면_0을_반환한다() {
        assertThat(NumberParser.parseInt("abc")).isZero();
    }
}
