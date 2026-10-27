package dev.eolmae.marketry.handler;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.BusinessException;
import dev.eolmae.marketry.common.exception.ConflictException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.common.exception.NotFoundException;
import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String ERROR_CODE_PROPERTY = "errorCode";
    private static final Duration SUPPRESSION_WINDOW = Duration.ofMinutes(5);
    private static final long SUPPRESSION_MAX_SIZE = 1000;

    private final EscalationPublisher escalationPublisher;

    // 같은 예외 클래스+메시지가 짧은 시간에 폭주해도(봇 스캔, 클라이언트 버그 등) 텔레그램은 5분에 1건만
    // 받는다. 억제된 횟수는 로그로만 남긴다. expireAfterWrite가 "5분에 1회" 창을 그대로 구현하고,
    // maximumSize가 예외 메시지에 요청값이 섞여 키가 매번 달라지는 경우에도 무한히 커지지 않게 막는다.
    private final Cache<String, AtomicInteger> suppressionByKey = Caffeine.newBuilder()
            .expireAfterWrite(SUPPRESSION_WINDOW)
            .maximumSize(SUPPRESSION_MAX_SIZE)
            .build();

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        HttpStatus status =
                switch (e) {
                    case BadRequestException ignored -> HttpStatus.BAD_REQUEST;
                    case NotFoundException ignored -> HttpStatus.NOT_FOUND;
                    case ConflictException ignored -> HttpStatus.CONFLICT;
                    case EscalateException ignored -> HttpStatus.INTERNAL_SERVER_ERROR;
                };

        if (e instanceof EscalateException escalateException) {
            escalationPublisher.report(escalateException);
        } else {
            // 400/404/409는 클라이언트 요청 문제라 실제 장애가 아니다. ERROR는 EscalateException 경로에만 남긴다.
            log.warn(e.createLogMessage(), e);
        }

        return toProblemDetail(status, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn(detail, e);

        return toProblemDetail(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, detail);
    }

    /**
     * 처리되지 않은 나머지 전부(NPE 등 진짜 예상 못 한 예외)를 잡아 알림을 보낸다.
     * ResponseEntityExceptionHandler가 다루는 MVC 내장 예외들은 대부분 ErrorResponse를 구현하지만,
     * TypeMismatchException 계열(MethodArgumentTypeMismatchException 등)과
     * HttpMessageNotWritableException·HttpMessageNotReadableException·AsyncRequestNotUsableException은
     * 그렇지 않다 — 이 다섯 부류는 전부 정상적인 4xx/클라이언트 연결 끊김이므로 그대로 다시 던져 Spring
     * 기본 처리로 보낸다. 안 그러면 domain/access의 403이 500으로 바뀌고, 봇 스캔의 404나 잘못된
     * 파라미터 하나하나가 텔레그램 알림이 된다.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception e) throws Exception {
        if (isSpringHandledException(e)) {
            throw e;
        }

        reportOrSuppress(e);
        return toProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason());
    }

    private boolean isSpringHandledException(Exception e) {
        return e instanceof ErrorResponse
                || e instanceof HttpMessageNotReadableException
                || e instanceof HttpMessageNotWritableException
                || e instanceof AsyncRequestNotUsableException
                || e instanceof TypeMismatchException;
    }

    private void reportOrSuppress(Exception e) {
        String key = e.getClass().getName() + "|" + e.getMessage();
        AtomicBoolean firstOccurrence = new AtomicBoolean(false);

        AtomicInteger suppressedCount = suppressionByKey.get(key, k -> {
            firstOccurrence.set(true);
            return new AtomicInteger(0);
        });

        if (firstOccurrence.get()) {
            escalationPublisher.report(EscalateException.wrap(ErrorCode.INTERNAL_ERROR, e));
        } else {
            // 알림은 억제하되 기록은 남긴다 — throwable을 붙여야 ThrowableFilter를 통과해 exception.log에도 남는다.
            log.warn("[예상 못한 예외 알림 억제] | key : {} | 억제 누적 : {}건", key, suppressedCount.incrementAndGet(), e);
        }
    }

    private ProblemDetail toProblemDetail(HttpStatus status, ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty(ERROR_CODE_PROPERTY, errorCode.name());
        return problemDetail;
    }
}
