package kr.co.marketry.domain.stock.scheduler;

import java.time.LocalDate;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.stock.collector.MarketCalendarCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketCalendarScheduler {
    private final MarketCalendarCollector collector;
    private LocalDate attemptDate;
    private boolean succeeded;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void refresh() {
        refresh(LocalDate.now(Zone.KST.zoneId()));
    }

    public synchronized void refresh(LocalDate date) {
        attemptDate = date;
        succeeded = collector.collect(date);
    }

    @Scheduled(cron = "0 5,15 6 * * *", zone = "Asia/Seoul")
    public void retry() {
        retry(LocalDate.now(Zone.KST.zoneId()));
    }

    public synchronized void retry(LocalDate date) {
        if (date.equals(attemptDate) && succeeded == false) {
            succeeded = collector.collect(date);
        }
    }
}
