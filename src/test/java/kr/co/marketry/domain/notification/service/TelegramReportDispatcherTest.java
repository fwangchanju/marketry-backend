package kr.co.marketry.domain.notification.service;

import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TelegramReportDispatcherTest {

    private final HomeReportSender homeReportSender = Mockito.mock(HomeReportSender.class);
    private final TelegramReportDispatcher dispatcher = new TelegramReportDispatcher(homeReportSender);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 8, 15);

    @Test
    void sendHome_홈_발송기를_부른다() {
        dispatcher.sendHome(dataTime);

        verify(homeReportSender).send(dataTime);
    }
}
