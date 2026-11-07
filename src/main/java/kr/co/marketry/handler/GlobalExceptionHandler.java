package kr.co.marketry.handler;

import java.util.Set;
import java.util.stream.Collectors;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.BusinessException;
import kr.co.marketry.common.exception.ConflictException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.common.exception.NotFoundException;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final String FOREIGN_KEY_VIOLATION_STATE = "23503";
    // 업종 삭제와 종목 배정·하위 업종 생성이 동시에 들어오면, 외래키(커밋 시점 검사)를 어긴 쪽이 이 두 제약으로 실패한다.
    private static final Set<String> CONCURRENT_SECTOR_CONSTRAINTS =
            Set.of("fk_custom_stock_sector_sector_user", "fk_custom_sector_parent_user");
    private final EscalationPublisher escalationPublisher;

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
    /** 업종 동시 변경으로 생긴 외래키 위반만 409로 알린다. 그 밖의 무결성 오류는 실제 결함일 수 있어 기존처럼 알림을 남긴다. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e) throws Exception {
        if (isConcurrentSectorChange(e)) {
            ConflictException conflict = new ConflictException(ErrorCode.SECTOR_CHANGED_CONCURRENTLY);
            log.warn(conflict.createLogMessage(), e);
            return toProblemDetail(HttpStatus.CONFLICT, conflict.getErrorCode(), conflict.getMessage());
        }
        return handleUnexpectedException(e);
    }

    private boolean isConcurrentSectorChange(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            // 오류 문구(언어에 따라 달라짐)가 아니라 PostgreSQL이 구조화해서 내려 주는 제약 이름을 본다.
            if (cause instanceof PSQLException postgresException
                    && FOREIGN_KEY_VIOLATION_STATE.equals(postgresException.getSQLState())
                    && postgresException.getServerErrorMessage() != null) {
                return CONCURRENT_SECTOR_CONSTRAINTS.contains(
                        postgresException.getServerErrorMessage().getConstraint());
            }
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception e) throws Exception {
        if (isSpringHandledException(e)) {
            throw e;
        }

        escalationPublisher.report(EscalateException.wrap(ErrorCode.INTERNAL_ERROR, e));
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

    private ProblemDetail toProblemDetail(HttpStatus status, ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty(ERROR_CODE_PROPERTY, errorCode.name());
        return problemDetail;
    }
}
