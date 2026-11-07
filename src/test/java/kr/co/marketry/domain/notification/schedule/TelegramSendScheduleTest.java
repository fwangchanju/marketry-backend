package kr.co.marketry.domain.notification.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TelegramSendScheduleTest {

    private static final int START_HOUR = 8;
    private static final int END_HOUR = 20;
    private static final int SEND_MINUTE = 10;
    private static final int SEND_INTERVAL_MINUTES = 15;
    private static final int COLLECT_INTERVAL_MINUTES = 5;
    private static final LocalDateTime DATE = LocalDateTime.of(2025, 6, 2, 0, 0);

    @Test
    void due_기준점_이전이라_발송하지_않는다() {
        LocalDateTime now = DATE.withHour(8).withMinute(0);

        assertThat(due(now, true)).isFalse();
    }

    @Test
    void due_기준_시각에는_발송한다() {
        LocalDateTime now = DATE.withHour(8).withMinute(10);

        assertThat(due(now, true)).isTrue();
    }

    @Test
    void due_발송_격자에_안_맞는_시각은_발송하지_않는다() {
        LocalDateTime now = DATE.withHour(8).withMinute(20);

        // 경과분 10 — 15의 배수가 아니다. 모듈로 판정이 깨지면 여기서 잡힌다.
        assertThat(due(now, true)).isFalse();
    }

    @Test
    void due_08시40분도_발송한다() {
        LocalDateTime now = DATE.withHour(8).withMinute(40);

        // 08:40 발송 복구 — startHour를 경계로 묶어 스킵하던 옛 버그가 여기서 사라졌는지 확인한다.
        assertThat(due(now, true)).isTrue();
    }

    @Test
    void due_두_시간_뒤에도_동일한_격자로_발송한다() {
        LocalDateTime now = DATE.withHour(10).withMinute(10);

        assertThat(due(now, true)).isTrue();
    }

    @Test
    void due_마감_시각에는_shouldCollect가_꺼져도_발송한다() {
        LocalDateTime now = DATE.withHour(20).withMinute(10);

        assertThat(due(now, false)).isTrue();
    }

    @Test
    void due_마감_이후_추가_사이클은_발송하지_않는다() {
        LocalDateTime now = DATE.withHour(20).withMinute(40);

        assertThat(due(now, false)).isFalse();
    }

    @Test
    void due_경과분이_음수인데_배수_조건만으로는_걸리는_조합에서_가드가_실제로_막는다() {
        // sendMinute 10, send-interval 5인 조합에서 08:00의 경과분은 -10이고 -10 % 5 == 0이라,
        // "경과분 >= 0" 가드가 없으면 발송으로 잘못 판정된다. 5단계 테스트의 상수 조합(간격 15)에서는
        // -10 % 15 == -10이라 이 케이스가 우연히 통과해버려서 가드 누락이 드러나지 않았다.
        LocalDateTime now = DATE.withHour(8).withMinute(0);

        boolean result = TelegramSendSchedule.due(now, true, START_HOUR, END_HOUR, SEND_MINUTE, 5);

        assertThat(result).isFalse();
    }

    @Test
    void validate_sendMinute이_0이하이면_기동을_막는다() {
        assertThatThrownBy(() -> validate(0, SEND_INTERVAL_MINUTES, 15)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_sendIntervalMinutes가_0이하이면_기동을_막는다() {
        assertThatThrownBy(() -> validate(SEND_MINUTE, 0, 15)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_beforeMinutes가_0이하이면_기동을_막는다() {
        assertThatThrownBy(() -> validate(SEND_MINUTE, SEND_INTERVAL_MINUTES, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_sendMinute이_collect_interval의_배수가_아니면_기동을_막는다() {
        assertThatThrownBy(() -> validate(7, SEND_INTERVAL_MINUTES, 15)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_sendIntervalMinutes가_collect_interval의_배수가_아니면_기동을_막는다() {
        assertThatThrownBy(() -> validate(SEND_MINUTE, 7, 15)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_beforeMinutes가_collect_interval의_배수가_아니면_기동을_막는다() {
        assertThatThrownBy(() -> validate(SEND_MINUTE, SEND_INTERVAL_MINUTES, 7))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_조건을_모두_만족하면_통과한다() {
        assertThatCode(() -> validate(SEND_MINUTE, SEND_INTERVAL_MINUTES, 15)).doesNotThrowAnyException();
    }

    private boolean due(LocalDateTime now, boolean shouldCollect) {
        return TelegramSendSchedule.due(now, shouldCollect, START_HOUR, END_HOUR, SEND_MINUTE, SEND_INTERVAL_MINUTES);
    }

    private void validate(int sendMinute, int sendIntervalMinutes, int beforeMinutes) {
        TelegramSendSchedule.validate(sendMinute, sendIntervalMinutes, beforeMinutes, COLLECT_INTERVAL_MINUTES);
    }
}
