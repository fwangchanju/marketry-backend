package kr.co.marketry.domain.notification.schedule;

import kr.co.marketry.domain.notification.service.TelegramReportDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미국 지도 정기 발송: 미국 종가가 저장되면 하루 한 번 보낸다. 종가 수집(UsaQuoteScheduler, 화~토 05:00~08:50)보다
 * 2분 늦게 5분마다 확인하고, 오늘 저장된 종가가 이미 나갔으면 발송기가 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class UsaMapTelegramSchedule {

    private final TelegramReportDispatcher telegramReportDispatcher;

    @Scheduled(cron = "${usa-map-report.cron:0 2/5 5-8 * * TUE-SAT}", zone = "Asia/Seoul")
    public void send() {
        telegramReportDispatcher.sendUsaIfNew();
    }
}
