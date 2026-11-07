package kr.co.marketry.domain.notification.exception;

/**
 * TelegramClient 발송 실패 전용. BusinessException을 상속하지 않는다 — sealed 목록은 HTTP 상태
 * 코드 매핑표라 컨트롤러까지 도달하지 않는 이 예외를 넣지 않는다. 원본 예외를 cause로 달지 않는다 —
 * RestClient 예외 메시지에는 요청 URI가 그대로 담기고 거기에 봇 토큰이 박혀 있어서, cause로 달면
 * 예외 로그의 스택트레이스와 알림 본문에 토큰이 샌다. 마스킹된 메시지와 원본 타입명만 갖는다.
 */
public final class TelegramSendException extends RuntimeException {

    public TelegramSendException(String originalTypeName, String maskedMessage) {
        super(originalTypeName + ": " + maskedMessage);
    }
}
