package kr.co.marketry.common.exception;

public final class ConflictException extends BusinessException {

    public ConflictException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ConflictException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
