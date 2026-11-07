package kr.co.marketry.domain.auth.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VisitorSourceTest {

    @ParameterizedTest
    @CsvSource({
        "https://search.naver.com/search.naver?query=a, NAVER",
        "https://m.blog.naver.com/abc, NAVER",
        "https://www.google.com/, GOOGLE",
        "https://www.google.co.kr/search?q=a, GOOGLE",
        "https://t.me/MARKETRY2026, OTHER",
        "https://marketry.co.kr/map, DIRECT",
        "https://www.marketry.co.kr/, DIRECT",
        "not a url, OTHER",
    })
    void 이전_페이지_주소의_도메인으로_출처를_나눈다(String referrer, VisitorSource expected) {
        assertThat(VisitorSource.from(referrer, null)).isEqualTo(expected);
    }

    @Test
    void 이전_페이지가_없으면_직접_들어온_것이다() {
        assertThat(VisitorSource.from(null, null)).isEqualTo(VisitorSource.DIRECT);
        assertThat(VisitorSource.from("  ", " ")).isEqualTo(VisitorSource.DIRECT);
    }

    @Test
    void utm_source가_있으면_이전_페이지보다_먼저_본다() {
        assertThat(VisitorSource.from("https://t.me/x", "naver")).isEqualTo(VisitorSource.NAVER);
        assertThat(VisitorSource.from(null, "instagram")).isEqualTo(VisitorSource.OTHER);
    }
}
