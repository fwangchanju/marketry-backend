package kr.co.marketry.common.exception;

import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.Getter;

public abstract sealed class BusinessException extends RuntimeException
        permits BadRequestException, NotFoundException, ConflictException, EscalateException {

    @Getter
    private final ErrorCode errorCode;

    private final Object[] args;

    private static final String LOG_DELIMITER = "|";

    protected BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = args;
    }

    protected BusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    public String createLogMessage() {

        if (args == null || args.length == 0) {

            return String.format("[%s] %s", errorCode.name(), getMessage());
        } else {

            String context = Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(LOG_DELIMITER));
            return String.format("[%s] %s | context : %s", errorCode.name(), getMessage(), context);
        }
    }

    public String getCauseMessage() {
        if (getCause() == null) {
            return "";
        }
        return String.format(
                "\ncause: %s\n%s",
                getCause().getClass().getSimpleName(), getCause().getMessage());
    }
}
