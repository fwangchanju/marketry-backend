package kr.co.marketry.domain.notification.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarketryPropertiesTest {

    private final MarketryProperties properties = new MarketryProperties("http://localhost:8081", 1L, 900000L);

    @Test
    void userIdOrPublished_로그인_사용자가_없으면_발행_사용자를_쓴다() {
        assertThat(properties.userIdOrPublished(null)).isEqualTo(900000L);
    }

    @Test
    void userIdOrPublished_로그인_사용자가_있으면_그_사용자를_쓴다() {
        assertThat(properties.userIdOrPublished(7L)).isEqualTo(7L);
    }
}
