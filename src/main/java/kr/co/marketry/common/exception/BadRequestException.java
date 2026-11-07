package kr.co.marketry.common.exception;

public final class BadRequestException extends BusinessException {

    public BadRequestException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public BadRequestException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
