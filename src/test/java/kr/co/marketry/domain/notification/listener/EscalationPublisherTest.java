package kr.co.marketry.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import kr.co.marketry.common.event.EscalationEvent;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class EscalationPublisherTest {
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final EscalationPublisher publisher = new EscalationPublisher(events);
    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 11, 4, 8, 10);

    @Test
    void 봇은_매번_받고_채널은_하루_세번째에_시각과_함께_한번_받는다() {
        var failure = new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        publisher.report(failure, FIRST);
        publisher.resolve("map-capture");
        publisher.report(
                new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED, new RuntimeException("다른 원인")),
                FIRST.plusMinutes(10));
        publisher.report(failure, FIRST.plusMinutes(25));
        publisher.report(failure, FIRST.plusMinutes(30));
        var captured = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(events, times(4)).publishEvent(captured.capture());
        assertThat(captured.getAllValues())
                .extracting(EscalationEvent::message)
                .allSatisfy(message -> assertThat(message).contains("SCREENSHOT_CAPTURE_FAILED"));
        assertThat(captured.getAllValues().get(2).channelMessage())
                .isEqualTo("⚠️ 스크린샷 캡처 실패. 오늘 3회 발생.\n발생 시각: 08:10, 08:20, 08:35");
        assertThat(captured.getAllValues().get(3).channelMessage()).isNull();
    }

    @Test
    void 오류_종류와_날짜별로_별도_집계한다() {
        var capture = new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        var stitch = new EscalateException(ErrorCode.IMAGE_STITCH_FAILED);
        publisher.report(capture, FIRST);
        publisher.report(stitch, FIRST);
        publisher.report(capture, FIRST.plusDays(1));
        var captured = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(events, times(3)).publishEvent(captured.capture());
        assertThat(captured.getAllValues())
                .extracting(EscalationEvent::message)
                .allSatisfy(message -> assertThat(message).contains("1회차"));
    }

    @Test
    void 국내와_일본_지도_실패는_시장명을_붙이고_각각_횟수를_집계한다() {
        var failure = new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);

        publisher.report("일본 지도", failure, FIRST);
        publisher.report("일본 지도", failure, FIRST.plusMinutes(5));
        publisher.report("국내 지도", failure, FIRST.plusMinutes(10));

        var captured = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(events, times(3)).publishEvent(captured.capture());
        assertThat(captured.getAllValues())
                .extracting(EscalationEvent::message)
                .containsExactly("⚠️ 일본 지도 스크린샷 캡처 실패 (1회차)", "⚠️ 일본 지도 스크린샷 캡처 실패 (2회차)", "⚠️ 국내 지도 스크린샷 캡처 실패 (1회차)");
    }

    @Test
    void 일본_지도_캡션_생성_실패도_일본_지도_원인으로_알린다() {
        publisher.report("일본 지도", new EscalateException(ErrorCode.MAP_CAPTION_BUILD_FAILED), FIRST);

        var captured = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(events).publishEvent(captured.capture());
        assertThat(captured.getValue().message()).isEqualTo("⚠️ 일본 지도 캡션 생성 실패 (1회차)");
    }
}
