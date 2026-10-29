package dev.eolmae.marketry.common.event;

/** 에스컬레이션 알림 이벤트. common이 텔레그램 발송을 직접 의존하지 않도록 분리. */
public record EscalationEvent(String message, String channelMessage) {

    public EscalationEvent(String message) {
        this(message, null);
    }
}
