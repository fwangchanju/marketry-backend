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
 * 한국 수집과 텔레그램 발송에 영향이 없다. 점심 재개 시세의 지연을 고려해 11:58부터 12:48 전까지 조회를 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JapanQuoteScheduler {
    private static final LocalTime FIRST_COLLECTION_TIME = LocalTime.of(9, 18);
    // 판단 전제: Yahoo 시세가 약 15분 지연되고 정규 수집은 매시 03, 08, ... 53, 58분에 시작한다.
    // 오전장 마지막 11:30 시세는 11:45경 제공된다고 보고 11:48과 11:53에 조회한다.
    // 오후장 12:30 시세는 12:45경 제공된다고 보고 그 다음 정규 회차인 12:48에 조회를 재개한다.
    // 실제 제공 시각과 종목별 도착률은 아직 미측정이므로 측정 결과에 따라 시간을 조정해야 한다.
    // 이 기간에 조회를 중단하면 예상보다 늦게 도착한 오전장 시세는 12:48까지 저장이 늦어질 수 있다.
    private static final LocalTime LUNCH_COLLECTION_PAUSE_START = LocalTime.of(11, 58);
    private static final LocalTime LUNCH_COLLECTION_RESUME = LocalTime.of(12, 48);
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
        if (now.toLocalTime().isBefore(LUNCH_COLLECTION_PAUSE_START) == false
                && now.toLocalTime().isBefore(LUNCH_COLLECTION_RESUME)) {
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
