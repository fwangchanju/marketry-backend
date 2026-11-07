package kr.co.marketry.domain.stock.exception;

/** {@code @Retryable}이 감지하는 연결 실패·타임아웃·5xx 재시도 신호 전용. 컨트롤러 경계까지 도달하지 않아 상태/메시지를 갖지 않는다. */
public class KiwoomTransientFailureException extends RuntimeException {}
