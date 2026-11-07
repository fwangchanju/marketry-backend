package kr.co.marketry.domain.stock.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CollectionCheckerTest {

    private static final int START_HOUR = 8;
    private static final int END_HOUR = 20;
    private static final int INTERVAL_MINUTES = 5;

    @Test
    void isTradingTime_시작시각_정각은_거래시간이다() {
        LocalDateTime mon0800 = LocalDateTime.of(2025, 6, 2, 8, 0); // 월요일

        assertThat(CollectionChecker.isTradingTime(mon0800, START_HOUR, END_HOUR))
                .isTrue();
    }

    @Test
    void isTradingTime_종료시각_정각도_거래시간이다() {
        LocalDateTime mon2000 = LocalDateTime.of(2025, 6, 2, 20, 0); // 월요일

        assertThat(CollectionChecker.isTradingTime(mon2000, START_HOUR, END_HOUR))
                .isTrue();
    }

    @Test
    void isTradingTime_시작시각_직전은_거래시간이_아니다() {
        LocalDateTime mon0759 = LocalDateTime.of(2025, 6, 2, 7, 59); // 월요일

        assertThat(CollectionChecker.isTradingTime(mon0759, START_HOUR, END_HOUR))
                .isFalse();
    }

    @Test
    void isTradingTime_종료시각_직후는_거래시간이_아니다() {
        LocalDateTime mon2001 = LocalDateTime.of(2025, 6, 2, 20, 1); // 월요일

        assertThat(CollectionChecker.isTradingTime(mon2001, START_HOUR, END_HOUR))
                .isFalse();
    }

    @Test
    void isTradingTime_주말은_시간대_안이어도_거래시간이_아니다() {
        LocalDateTime sat1200 = LocalDateTime.of(2025, 6, 7, 12, 0); // 토요일

        assertThat(CollectionChecker.isTradingTime(sat1200, START_HOUR, END_HOUR))
                .isFalse();
    }

    @Test
    void expectedSnapshotTime_거래시간중이면_수집주기_단위로_절삭된다() {
        LocalDateTime mon1007 = LocalDateTime.of(2025, 6, 2, 10, 7, 30); // 월요일

        LocalDateTime result = CollectionChecker.expectedSnapshotTime(mon1007, START_HOUR, END_HOUR, INTERVAL_MINUTES);

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 2, 10, 5));
    }

    @Test
    void expectedSnapshotTime_평일_거래시작전이면_직전_거래일_종료시각이다() {
        LocalDateTime mon0700 = LocalDateTime.of(2025, 6, 2, 7, 0); // 월요일

        LocalDateTime result = CollectionChecker.expectedSnapshotTime(mon0700, START_HOUR, END_HOUR, INTERVAL_MINUTES);

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 5, 30, 20, 0)); // 직전 금요일 20:00
    }

    @Test
    void expectedSnapshotTime_평일_거래종료후면_당일_종료시각이다() {
        LocalDateTime mon2100 = LocalDateTime.of(2025, 6, 2, 21, 0); // 월요일

        LocalDateTime result = CollectionChecker.expectedSnapshotTime(mon2100, START_HOUR, END_HOUR, INTERVAL_MINUTES);

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 2, 20, 0));
    }

    @Test
    void expectedSnapshotTime_주말이면_직전_거래일_종료시각이다() {
        LocalDateTime sat1200 = LocalDateTime.of(2025, 6, 7, 12, 0); // 토요일

        LocalDateTime result = CollectionChecker.expectedSnapshotTime(sat1200, START_HOUR, END_HOUR, INTERVAL_MINUTES);

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 6, 20, 0)); // 직전 금요일 20:00
    }

    @Test
    void previousTradingDay_월요일이면_직전_금요일이다() {
        LocalDate monday = LocalDate.of(2025, 6, 2);

        assertThat(CollectionChecker.previousTradingDay(monday)).isEqualTo(LocalDate.of(2025, 5, 30));
    }
}
