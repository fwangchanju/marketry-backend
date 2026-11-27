package kr.co.marketry.domain.stock.scheduler;

import kr.co.marketry.domain.stock.collector.UsaPreMarketCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미국 프리마켓 시세 수집: 월~금 17:00~23:59(한국 시각), 5분 간격. 프리마켓은 한국 시각으로 겨울 18:00~23:30, 서머타임 17:00~22:30이라
 * 넉넉히 부르고, 실제로 프리마켓인지는 야후가 알려 주는 그날 세션 시각으로 수집기가 판단한다. 종가 수집(UsaQuoteScheduler)과 묶지 않는다.
 * 야후 5분 칸이 채워지도록 정각보다 2분 늦게 부른다.
 */
@Slf4j
@Component
public class UsaPreMarketScheduler {
    private final UsaPreMarketCollector collector;
    private final boolean collectEnabled;

    public UsaPreMarketScheduler(
            UsaPreMarketCollector collector, @Value("${usa-premarket.enabled:false}") boolean collectEnabled) {
        this.collector = collector;
        this.collectEnabled = collectEnabled;
    }

    @Scheduled(cron = "${usa-premarket.cron:0 2/5 17-23 * * MON-FRI}", zone = "Asia/Seoul")
    public synchronized void collect() {
        if (collectEnabled == false) {
            return;
        }
        try {
            UsaPreMarketCollector.Result result = collector.collect();
            log.info(
                    "[미국프리마켓] {}: snapshotTime={}, 저장={}/{}, {}",
                    result.saved() ? "저장" : "저장 안 함",
                    result.snapshotTime(),
                    result.savedCount(),
                    result.totalCount(),
                    result.reason() == null ? "" : result.reason());
        } catch (RuntimeException failure) {
            // TODO 정식 단계에서 연속 실패 알림(UsaQuoteScheduler와 같은 방식)을 붙인다. 시험 단계라 로그만 남긴다.
            log.warn("[미국프리마켓] 수집 실패", failure);
        }
    }
}
