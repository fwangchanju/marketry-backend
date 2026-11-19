package kr.co.marketry.domain.notification.service;

import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TelegramReportDispatcherTest {

    private final MapReportSender mapReportSender = Mockito.mock(MapReportSender.class);
    private final TelegramReportDispatcher dispatcher = new TelegramReportDispatcher(mapReportSender);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 8, 15);

    @Test
    void sendReport_지도_발송기를_부른다() {
        dispatcher.sendReport(dataTime);

        verify(mapReportSender).send(dataTime);
    }
}
