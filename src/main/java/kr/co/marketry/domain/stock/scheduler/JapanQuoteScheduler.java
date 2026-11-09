package kr.co.marketry.domain.stock.scheduler;

import java.time.LocalDateTime;
import java.time.LocalTime;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.stock.collector.JapanQuoteCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일본 시세 수집: 평일 09:18~15:58, 5분 간격. 묶지 않아서 야후가 느리거나 막혀도
 * 한국 수집과 텔레그램 발송에 영향이 없다. 휴장일과 점심시간에는 새 체결 시각이 없어 저장되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JapanQuoteScheduler {
    private static final LocalTime FIRST_COLLECTION_TIME = LocalTime.of(9, 18);
    private final JapanQuoteCollector collector;

    @Scheduled(cron = "${japan-quote.cron:0 3/5 9-15 * * MON-FRI}", zone = "Asia/Seoul")
    public synchronized void collect() {
        collect(KstClock.now());
    }

    /** Yahoo의 첫 당일 시세 제공 시각을 09:15부터 정규 수집 직전까지 한 번 측정한다. */
    @Scheduled(cron = "0 15 9 * * MON-FRI", zone = "Asia/Seoul")
    public synchronized void measureOpeningFreshness() {
        try {
            JapanQuoteCollector.OpeningMeasurement result = collector.measureOpeningFreshness();
            log.info(
                    "[일본시세] 개장 시세 측정: 시작={}, 종료={}, 오늘 시세={}/{}, 요청={}, 첫 오늘 시세={}, 90% 도달={}, 결과={}",
                    result.startedAt(),
                    result.finishedAt(),
                    result.todayQuoteCount(),
                    result.totalCount(),
                    result.requestCount(),
                    result.firstTodayQuoteAt(),
                    result.thresholdReachedAt(),
                    result.reason() == null ? "도달" : result.reason());
        } catch (RuntimeException failure) {
            log.warn("[일본시세] 개장 시세 측정 실패", failure);
        }
    }

    void collect(LocalDateTime now) {
        if (now.toLocalTime().isBefore(FIRST_COLLECTION_TIME)) {
            return;
        }
        try {
            JapanQuoteCollector.Result result = collector.collect();
            if (result.saved()) {
                log.info(
                        "[일본시세] 저장: snapshotTime={}, 오늘 시세={}/{}, 오늘 외={}, 조회 실패={}, 응답={}/{}",
                        result.snapshotTime(),
                        result.todayQuoteCount(),
                        result.totalCount(),
                        result.otherDateQuoteCount(),
                        result.failureCount(),
                        result.successCount(),
                        result.totalCount());
                return;
            }
            log.info(
                    "[일본시세] 저장 안 함: {}, 오늘 시세={}/{}, 오늘 외={}, 조회 실패={}, 응답={}/{}",
                    result.reason(),
                    result.todayQuoteCount(),
                    result.totalCount(),
                    result.otherDateQuoteCount(),
                    result.failureCount(),
                    result.successCount(),
                    result.totalCount());
        } catch (RuntimeException failure) {
            log.warn("[일본시세] 수집 실패", failure);
        }
    }
}
