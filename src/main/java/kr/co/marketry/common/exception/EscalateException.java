package kr.co.marketry.common.exception;

/**
 * 발생 즉시 개발자(DEVELOPER_CHAT_ID)에게 텔레그램 알림을 발송하는 예외.
 * 다음 기회가 없는 1회성 수집 실패, 전체 수집을 막는 근본 장애 등 즉시 인지가 필요한 구간에서 사용.
 */
public final class EscalateException extends BusinessException {

    public EscalateException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public EscalateException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }

    /** 이미 EscalateException이면 그대로, 아니면 fallbackErrorCode로 감싸서 반환. */
    public static EscalateException wrap(ErrorCode fallbackErrorCode, Exception e, Object... fallbackArgs) {
        if (e instanceof EscalateException escalateException) {
            return escalateException;
        }
        return new EscalateException(fallbackErrorCode, e, fallbackArgs);
    }
}
