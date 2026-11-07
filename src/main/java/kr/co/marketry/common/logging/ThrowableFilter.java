package kr.co.marketry.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * throwable이 붙은 로그 이벤트만 통과시킨다. logback.xml에서 이름으로만 참조하는 인프라 코드라 도메인이
 * 없다 — 어느 로거로 찍든, throwable이 붙어 있으면 EXCEPTION_FILE appender로 보낸다.
 */
public class ThrowableFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        return event.getThrowableProxy() != null ? FilterReply.ACCEPT : FilterReply.DENY;
    }
}
