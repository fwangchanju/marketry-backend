package kr.co.marketry.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;

class ThrowableFilterTest {

    private final ThrowableFilter filter = new ThrowableFilter();

    @Test
    void decide_예외가_붙은_이벤트는_ACCEPT를_반환한다() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getThrowableProxy()).thenReturn(mock(IThrowableProxy.class));

        assertThat(filter.decide(event)).isEqualTo(FilterReply.ACCEPT);
    }

    @Test
    void decide_예외가_없는_이벤트는_DENY를_반환한다() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getThrowableProxy()).thenReturn(null);

        assertThat(filter.decide(event)).isEqualTo(FilterReply.DENY);
    }
}
