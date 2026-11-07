package kr.co.marketry.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserAccountTest {
    private static final LocalDateTime FIRST_ACCESS = LocalDateTime.of(2026, 10, 21, 17, 0, 0);

    private final UserAccount user = UserAccount.create("issuer", "sub", "member@example.com");

    @Test
    void 접속_기록이_없던_회원은_처음_접속에서_바로_기록한다() {
        assertThat(user.getLastAccessAt()).isNull();

        user.recordAccess(FIRST_ACCESS);

        assertThat(user.getLastAccessAt()).isEqualTo(FIRST_ACCESS);
    }

    @Test
    void 마지막_기록_뒤_5분이_지나기_전에는_다시_기록하지_않는다() {
        user.recordAccess(FIRST_ACCESS);

        user.recordAccess(FIRST_ACCESS.plusMinutes(4).plusSeconds(59));

        assertThat(user.getLastAccessAt()).isEqualTo(FIRST_ACCESS);
    }

    @Test
    void 마지막_기록_뒤_5분이_되면_새로_기록한다() {
        user.recordAccess(FIRST_ACCESS);
        LocalDateTime fiveMinutesLater = FIRST_ACCESS.plus(UserAccount.ACCESS_RECORD_INTERVAL);

        user.recordAccess(fiveMinutesLater);

        assertThat(user.getLastAccessAt()).isEqualTo(fiveMinutesLater);
    }
}
