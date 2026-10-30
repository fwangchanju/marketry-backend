package dev.eolmae.marketry.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import java.lang.reflect.Method;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final EscalationPublisher escalationPublisher = Mockito.mock(EscalationPublisher.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(escalationPublisher);

    @Test
    void handleBusinessException_EscalateException은_알림을_보내고_500을_반환한다() {
        ProblemDetail result = handler.handleBusinessException(new EscalateException(ErrorCode.INTERNAL_ERROR));

        verify(escalationPublisher).report(Mockito.any());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    void handleBusinessException_일반_BusinessException은_알림을_보내지_않는다() {
        ProblemDetail result = handler.handleBusinessException(new BadRequestException(ErrorCode.INVALID_INPUT));

        verify(escalationPublisher, never()).report(Mockito.any());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void handleUnexpectedException_예상못한예외는_알림을_보내고_500을_반환한다() throws Exception {
        ProblemDetail result = handler.handleUnexpectedException(new NullPointerException("boom"));

        verify(escalationPublisher).report(Mockito.any());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getProperties()).containsEntry("errorCode", ErrorCode.INTERNAL_ERROR.name());
    }

    @Test
    void handleUnexpectedException_ResponseStatusException은_다시_던지고_알림을_보내지_않는다() {
        ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> handler.handleUnexpectedException(forbidden)).isSameAs(forbidden);

        verify(escalationPublisher, never()).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_HttpMessageNotReadableException은_다시_던지고_알림을_보내지_않는다() {
        HttpMessageNotReadableException e =
                new HttpMessageNotReadableException("malformed", Mockito.mock(HttpInputMessage.class));

        assertThatThrownBy(() -> handler.handleUnexpectedException(e)).isSameAs(e);

        verify(escalationPublisher, never()).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_AsyncRequestNotUsableException은_다시_던지고_알림을_보내지_않는다() {
        AsyncRequestNotUsableException e = new AsyncRequestNotUsableException("client disconnected");

        assertThatThrownBy(() -> handler.handleUnexpectedException(e)).isSameAs(e);

        verify(escalationPublisher, never()).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_MethodArgumentTypeMismatchException은_다시_던지고_알림을_보내지_않는다()
            throws NoSuchMethodException {
        // ErrorResponse를 구현하지 않는 TypeMismatchException 계열 — @PathVariable/@RequestParam 타입이
        // 안 맞는 정상적인 400 요청까지 500+알림으로 잘못 처리되던 버그의 재발 방지 테스트.
        Method method = String.class.getMethod("charAt", int.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", int.class, "index", parameter, new NumberFormatException("abc"));

        assertThatThrownBy(() -> handler.handleUnexpectedException(e)).isSameAs(e);

        verify(escalationPublisher, never()).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_같은_예외도_중앙_알림_횟수_제한에_전달한다() throws Exception {
        handler.handleUnexpectedException(new IllegalStateException("반복"));
        handler.handleUnexpectedException(new IllegalStateException("반복"));

        verify(escalationPublisher, times(2)).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_예외_클래스나_메시지가_다르면_각각_알림을_보낸다() throws Exception {
        handler.handleUnexpectedException(new IllegalStateException("A"));
        handler.handleUnexpectedException(new IllegalStateException("B"));

        verify(escalationPublisher, times(2)).report(Mockito.any());
    }

    private static DataIntegrityViolationException foreignKeyViolation(String constraint, String sqlState) {
        SQLException cause = new SQLException(
                "ERROR: insert or update on table \"custom_stock_sector\" violates foreign key constraint \""
                        + constraint + "\"",
                sqlState);
        return new DataIntegrityViolationException("Unable to commit against JDBC Connection", cause);
    }

    @Test
    void handleDataIntegrityViolation_업종_동시_변경으로_생긴_외래키_위반은_409로_알리고_알림은_보내지_않는다() throws Exception {
        for (String constraint : new String[] {"fk_custom_stock_sector_sector_user", "fk_custom_sector_parent_user"}) {
            ProblemDetail result = handler.handleDataIntegrityViolation(foreignKeyViolation(constraint, "23503"));

            assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(result.getProperties()).containsEntry("errorCode", ErrorCode.SECTOR_CHANGED_CONCURRENTLY.name());
        }
        verify(escalationPublisher, never()).report(Mockito.any());
    }

    @Test
    void handleDataIntegrityViolation_다른_제약의_외래키_위반은_알림을_보내고_500을_반환한다() throws Exception {
        ProblemDetail result =
                handler.handleDataIntegrityViolation(foreignKeyViolation("fk_custom_stock_sector_stock", "23503"));

        verify(escalationPublisher).report(Mockito.any());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    void handleDataIntegrityViolation_외래키_위반이_아닌_무결성_오류는_알림을_보내고_500을_반환한다() throws Exception {
        ProblemDetail result = handler.handleDataIntegrityViolation(
                foreignKeyViolation("fk_custom_stock_sector_sector_user", "23505"));

        verify(escalationPublisher).report(Mockito.any());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
