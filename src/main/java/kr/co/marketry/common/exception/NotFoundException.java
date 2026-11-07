package kr.co.marketry.common.exception;

public final class NotFoundException extends BusinessException {

    public NotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public NotFoundException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
