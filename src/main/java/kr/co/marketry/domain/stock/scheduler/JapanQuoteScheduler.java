package kr.co.marketry.domain.stock.scheduler;

import kr.co.marketry.domain.stock.collector.JapanQuoteCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일본 시세 수집: 평일 09:02~15:57, 5분 간격. 한국 수집과 2분 어긋나게 돌리고 묶지 않아서, 야후가 느리거나 막혀도
 * 한국 수집과 텔레그램 발송에 영향이 없다. 휴장일과 점심시간에는 새 체결 시각이 없어 저장되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JapanQuoteScheduler {
    private final JapanQuoteCollector collector;

    @Scheduled(cron = "${japan-quote.cron:0 2/5 9-15 * * MON-FRI}", zone = "Asia/Seoul")
    public void collect() {
        try {
            JapanQuoteCollector.Result result = collector.collect();
            if (result.saved()) {
                log.info(
                        "[일본시세] 저장: snapshotTime={}, 받음={}/{}",
                        result.snapshotTime(),
                        result.successCount(),
                        result.totalCount());
                return;
            }
            log.info("[일본시세] 저장 안 함: {}", result.reason());
        } catch (RuntimeException failure) {
            log.warn("[일본시세] 수집 실패", failure);
        }
    }
}
