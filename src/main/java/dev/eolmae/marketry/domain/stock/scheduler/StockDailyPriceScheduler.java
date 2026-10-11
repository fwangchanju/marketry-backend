package dev.eolmae.marketry.domain.stock.scheduler;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.event.DailyPriceCollectionFailedEvent;
import dev.eolmae.marketry.domain.stock.collector.StockDailyPriceCollector;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
public class StockDailyPriceScheduler {
    static final String DEADLINE_REASON = "일봉 수집을 제한 시간 내에 완료하지 못했습니다";
    static final String INCOMPLETE_REASON = "재시도 후에도 수집하지 못한 종목이 있습니다";

    private final StockDailyPriceCollector collector;
    private final ApplicationEventPublisher events;
    private final LocalTime stopTime;
    private final Clock clock;

    @Autowired
    public StockDailyPriceScheduler(
            StockDailyPriceCollector collector,
            ApplicationEventPublisher events,
            @Value("${daily-price.stop-time:05:50}") LocalTime stopTime) {
        this(collector, events, stopTime, Clock.system(Zone.KST.zoneId()));
    }

    StockDailyPriceScheduler(
            StockDailyPriceCollector collector, ApplicationEventPublisher events, LocalTime stopTime, Clock clock) {
        this.collector = collector;
        this.events = events;
        this.stopTime = stopTime;
        this.clock = clock;
    }

    @Scheduled(cron = "${daily-price.cron:0 0 5 * * *}", zone = "Asia/Seoul")
    public void collect() {
        collect(LocalDate.now(clock));
    }

    public void collect(LocalDate executionDate) {
        // 직전 거래일로 되돌리지 않는다. 토요일도 실행해야 금요일 데이터를 회수할 수 있다.
        LocalDate date = executionDate.minusDays(1);
        Instant deadline =
                executionDate.atTime(stopTime).atZone(Zone.KST.zoneId()).toInstant();
        StockDailyPriceCollector.Result result;
        try {
            result = collector.collect(date, List.of(), deadline);
        } catch (RuntimeException failure) {
            log.error(
                    "[일봉 수집 실행 실패] | context : {}|{}", date, failure.getClass().getSimpleName());
            events.publishEvent(new DailyPriceCollectionFailedEvent(
                    date, "수집 실행 실패: " + failure.getClass().getSimpleName(), List.of()));
            return;
        }
        log.info(
                "[일봉 수집 종료] | context : {}|{}|{}|{}|{}",
                date,
                result.status(),
                result.passes(),
                result.attempts(),
                result.inserted());
        switch (result.status()) {
            case INCOMPLETE ->
                events.publishEvent(new DailyPriceCollectionFailedEvent(
                        date,
                        INCOMPLETE_REASON,
                        result.failures().stream().map(this::createFailureLine).toList()));
            // 제한 시간 초과는 남은 대상 집계 없이 문구만 알린다.
            case DEADLINE -> events.publishEvent(new DailyPriceCollectionFailedEvent(date, DEADLINE_REASON, List.of()));
            case COMPLETE, HOLIDAY, INTERRUPTED -> {}
        }
    }

    private String createFailureLine(StockDailyPriceCollector.FailedStock stock) {
        return stock.stockCode() + "(" + stock.stockName() + ") "
                + stock.exchanges().stream().map(Enum::name).toList();
    }
}
