package kr.co.marketry.domain.notification.listener;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.common.event.EscalationEvent;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
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
        report(null, e, LocalDateTime.now(Zone.KST.zoneId()));
    }

    public synchronized void report(String source, EscalateException e) {
        report(source, e, LocalDateTime.now(Zone.KST.zoneId()));
    }

    public synchronized void resolve(String source) {
        // 일일 집계는 성공 후에도 유지한다.
    }

    synchronized void report(EscalateException e, LocalDateTime now) {
        report(null, e, now);
    }

    void report(String source, EscalateException e, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        String logMessage = e.createLogMessage();
        log.error(logMessage, e);

        if (today.equals(occurrenceDate) == false) {
            occurrenceDate = today;
            failureBySource.invalidateAll();
        }

        boolean marketMapImageFailure = source != null
                && source.isBlank() == false
                && (e.getErrorCode() == ErrorCode.SCREENSHOT_CAPTURE_FAILED
                        || e.getErrorCode() == ErrorCode.TELEGRAM_IMAGE_SEND_FAILED
                        || e.getErrorCode() == ErrorCode.MAP_CAPTION_BUILD_FAILED);
        String errorKey =
                (marketMapImageFailure ? source + "|" : "") + e.getErrorCode().name();
        FailureState previous = failureBySource.getIfPresent(errorKey);
        List<LocalDateTime> failures = previous == null ? new ArrayList<>() : previous.times();
        if (failures.size() < MAX_NOTIFICATIONS_PER_ERROR) failures.add(now);
        int occurrence = previous == null ? 1 : previous.count() + 1;
        failureBySource.put(errorKey, new FailureState(occurrence, failures));

        String summary = e.getMessage().replace("에 실패했습니다.", " 실패.");
        String notification = "[" + e.getErrorCode().name() + "]\n⚠️ " + summary + " " + occurrence + "회차.";
        if (marketMapImageFailure) {
            notification = "⚠️ " + source + " " + summary.replaceAll("\\.$", "") + " (" + occurrence + "회차)";
            if (e.getCause() != null) {
                notification += "\n원인: " + e.getCause().getClass().getSimpleName();
                if (e.getCause().getMessage() != null
                        && e.getCause().getMessage().isBlank() == false) {
                    notification += "\n상세: " + e.getCause().getMessage();
                }
            }
        } else if (e.getCauseMessage().isBlank() == false) {
            notification += "\n" + e.getCauseMessage();
        }

        String channelMessage = null;
        if (occurrence == MAX_NOTIFICATIONS_PER_ERROR) {
            String times = failures.stream()
                    .map(time -> time.format(DateTimeFormatter.ofPattern("HH:mm")))
                    .collect(java.util.stream.Collectors.joining(", "));
            channelMessage =
                    (marketMapImageFailure ? "⚠️ " + source + " " : "⚠️ ") + summary + " 오늘 3회 발생.\n발생 시각: " + times;
        }
        eventPublisher.publishEvent(new EscalationEvent(notification, channelMessage));
    }

    private record FailureState(int count, List<LocalDateTime> times) {}
}
