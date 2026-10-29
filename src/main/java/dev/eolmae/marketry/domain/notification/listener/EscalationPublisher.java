package dev.eolmae.marketry.domain.notification.listener;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.event.EscalationEvent;
import dev.eolmae.marketry.common.exception.EscalateException;
import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 예외 로그(root를 타고 EXCEPTION_FILE로) 기록 + EscalationEvent 발행.
 * 수집기처럼 예외를 던지면 이후 실행이 멈추는 경우, 던지지 않고 이 메서드를 직접 호출해 동일한 처리를 받는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscalationPublisher {

    private static final int MAX_NOTIFICATIONS_PER_ERROR = 3;
    private static final long MAX_TRACKED_ERRORS = 1000;
    private static final Duration ERROR_TRACKING_DURATION = Duration.ofDays(1);

    private final ApplicationEventPublisher eventPublisher;
    private final Cache<String, FailureState> failureBySource = Caffeine.newBuilder()
            .expireAfterWrite(ERROR_TRACKING_DURATION)
            .maximumSize(MAX_TRACKED_ERRORS)
            .build();
    private LocalDate occurrenceDate;

    public synchronized void report(EscalateException e) {
        report(e.createLogMessage() + e.getCauseMessage(), e, LocalDate.now(Zone.KST.zoneId()));
    }

    public synchronized void report(String source, EscalateException e) {
        report(source, e, LocalDate.now(Zone.KST.zoneId()));
    }

    public synchronized void resolve(String source) {
        failureBySource.invalidate(source);
    }

    synchronized void report(EscalateException e, LocalDate today) {
        report(e.createLogMessage() + e.getCauseMessage(), e, today);
    }

    synchronized void report(String source, EscalateException e, LocalDate today) {
        String logMessage = e.createLogMessage();
        log.error(logMessage, e);

        if (today.equals(occurrenceDate) == false) {
            occurrenceDate = today;
            failureBySource.invalidateAll();
        }

        String errorKey = logMessage + e.getCauseMessage();
        FailureState previousFailure = failureBySource.getIfPresent(source);
        int occurrence = previousFailure != null && previousFailure.errorKey().equals(errorKey)
                ? previousFailure.occurrence() + 1
                : 1;
        failureBySource.put(source, new FailureState(errorKey, occurrence));
        if (occurrence > MAX_NOTIFICATIONS_PER_ERROR) {
            log.warn("[동일 오류 알림 억제] | 오류 코드 : {} | 발생일 : {} | source : {}", e.getErrorCode(), today, source);
            return;
        }

        String summary = e.getMessage().replace("에 실패했습니다.", " 실패.");
        String notification = "[" + e.getErrorCode().name() + "]\n⚠️ " + summary + " " + occurrence + "회차.";
        String causeMessage = e.getCauseMessage();
        if (occurrence == MAX_NOTIFICATIONS_PER_ERROR) {
            notification += "\n같은 오류 3회차 발생으로 오류 알림 중단";
        }
        if (causeMessage.isBlank() == false) {
            notification += "\n" + causeMessage;
        }

        String channelMessage =
                occurrence == MAX_NOTIFICATIONS_PER_ERROR ? "⚠️ " + summary + " 3회차.\n봇 메시지 확인할 것." : null;
        eventPublisher.publishEvent(new EscalationEvent(notification, channelMessage));
    }

    private record FailureState(String errorKey, int occurrence) {}
}
