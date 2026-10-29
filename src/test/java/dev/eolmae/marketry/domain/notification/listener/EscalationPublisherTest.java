package dev.eolmae.marketry.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.eolmae.marketry.common.event.EscalationEvent;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class EscalationPublisherTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EscalationPublisher publisher = new EscalationPublisher(eventPublisher);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @Test
    void 같은_오류는_세_번까지_알리고_세_번째에만_채널_메시지를_만든다() {
        EscalateException failure = new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);

        publisher.report(failure, TODAY);
        publisher.report(failure, TODAY);
        publisher.report(failure, TODAY);
        publisher.report(failure, TODAY);

        ArgumentCaptor<EscalationEvent> eventCaptor = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).message())
                .contains("[SCREENSHOT_CAPTURE_FAILED]", "1회차")
                .doesNotContain("오류 알림 중단");
        assertThat(eventCaptor.getAllValues().get(0).channelMessage()).isNull();
        assertThat(eventCaptor.getAllValues().get(1).message()).contains("2회차");
        assertThat(eventCaptor.getAllValues().get(1).channelMessage()).isNull();
        assertThat(eventCaptor.getAllValues().get(2).message()).contains("3회차", "같은 오류 3회차 발생으로 오류 알림 중단");
        assertThat(eventCaptor.getAllValues().get(2).channelMessage()).isEqualTo("⚠️ 스크린샷 캡처 실패. 3회차.\n봇 메시지 확인할 것.");
    }

    @Test
    void 오류_종류가_다르면_회차를_각각_센다() {
        publisher.report(new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED), TODAY);
        publisher.report(new EscalateException(ErrorCode.IMAGE_STITCH_FAILED), TODAY);

        ArgumentCaptor<EscalationEvent> eventCaptor = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).message()).contains("1회차");
        assertThat(eventCaptor.getAllValues().get(1).message()).contains("1회차");
    }

    @Test
    void 날짜가_바뀌면_같은_오류의_회차를_초기화한다() {
        EscalateException failure = new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        publisher.report(failure, TODAY);
        publisher.report(failure, TODAY.plusDays(1));

        ArgumentCaptor<EscalationEvent> eventCaptor = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(EscalationEvent::message)
                .allSatisfy(message -> assertThat(message).contains("1회차"));
    }

    @Test
    void 해당_작업이_성공하면_연속_오류_횟수를_초기화한다() {
        EscalateException failure = new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        publisher.report("map-capture", failure, TODAY);
        publisher.report("map-capture", failure, TODAY);
        publisher.resolve("map-capture");
        publisher.report("map-capture", failure, TODAY);

        ArgumentCaptor<EscalationEvent> eventCaptor = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).message()).contains("1회차");
        assertThat(eventCaptor.getAllValues().get(1).message()).contains("2회차");
        assertThat(eventCaptor.getAllValues().get(2).message()).contains("1회차").doesNotContain("3회차");
    }
}
