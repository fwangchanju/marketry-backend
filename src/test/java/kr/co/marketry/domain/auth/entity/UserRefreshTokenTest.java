package kr.co.marketry.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Zone;
import org.junit.jupiter.api.Test;

class UserRefreshTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 12, 0);

    // replace()/revoke()는 교체·폐기 시각을 실제 현재 시각으로 기록한다. 만료 시각을 고정 날짜로 두면 그
    // 날짜가 지난 뒤부터 "교체 직후인데 이미 만료"가 되어 테스트가 깨진다.
    private static LocalDateTime tomorrowFromNow() {
        return LocalDateTime.now(Zone.KST.zoneId()).plusDays(1);
    }

    @Test
    void 교체_직후에는_사용_가능하다() {
        UserRefreshToken token = createToken(tomorrowFromNow());

        token.replace();

        assertThat(token.isUsableAt(token.getReplacedAt())).isTrue();
    }

    @Test
    void 교체_30초가_지나면_사용_불가능하다() {
        UserRefreshToken token = createToken(tomorrowFromNow());

        token.replace();

        LocalDateTime after30Seconds = token.getReplacedAt().plusSeconds(30);
        assertThat(token.isUsableAt(after30Seconds)).isFalse();
    }

    @Test
    void replace를_두_번_호출해도_첫_교체_시각을_유지한다() {
        UserRefreshToken token = createToken(tomorrowFromNow());

        token.replace();
        LocalDateTime firstReplacedAt = token.getReplacedAt();
        token.replace();

        assertThat(token.getReplacedAt()).isEqualTo(firstReplacedAt);
    }

    @Test
    void revoke된_토큰은_교체_여부와_무관하게_사용_불가능하다() {
        UserRefreshToken token = createToken(tomorrowFromNow());

        token.revoke();

        assertThat(token.isUsableAt(token.getRevokedAt())).isFalse();
    }

    @Test
    void 만료된_토큰은_사용_불가능하다() {
        UserRefreshToken token = createToken(NOW.minusSeconds(1));

        assertThat(token.isUsableAt(NOW)).isFalse();
    }

    private UserRefreshToken createToken(LocalDateTime expiresAt) {
        return UserRefreshToken.create(mock(UserAccount.class), "hash", expiresAt);
    }
}
