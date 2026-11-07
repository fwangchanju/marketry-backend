package kr.co.marketry.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringsTest {

    @Test
    void trimToEmpty_null이면_빈문자열을_반환한다() {
        assertThat(Strings.trimToEmpty(null)).isEmpty();
    }

    @Test
    void trimToEmpty_앞뒤_공백을_제거한다() {
        assertThat(Strings.trimToEmpty("  abc  ")).isEqualTo("abc");
    }

    @Test
    void substringBefore_null이면_빈문자열을_반환한다() {
        assertThat(Strings.substringBefore(null, "_")).isEmpty();
    }

    @Test
    void substringBefore_구분자가_없으면_전체문자열을_반환한다() {
        assertThat(Strings.substringBefore("005930", "_")).isEqualTo("005930");
    }

    @Test
    void substringBefore_구분자_앞부분만_반환한다() {
        assertThat(Strings.substringBefore("005930_NX", "_")).isEqualTo("005930");
    }
}
