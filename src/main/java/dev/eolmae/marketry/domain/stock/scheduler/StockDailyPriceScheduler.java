package dev.eolmae.marketry.domain.stock.scheduler;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.event.DailyPriceCollectionFailedEvent;
import dev.eolmae.marketry.domain.stock.collector.StockDailyPriceCollector;
import dev.eolmae.marketry.domain.stock.dto.MissingDailyPriceStock;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class StockDailyPriceScheduler {
    private final StockDailyPriceCollector collector;
    private final ApplicationEventPublisher events;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${daily-price.stop-time:05:50}")
    private LocalTime stopTime;

    @Scheduled(cron = "${daily-price.cron:0 0 5 * * *}", zone = "Asia/Seoul")
    public void collect() {
        LocalDate executionDate = LocalDate.now(Zone.KST.zoneId());
        collect(
                executionDate,
                executionDate.atTime(stopTime).atZone(Zone.KST.zoneId()).toInstant());
    }

    public void collect(LocalDate executionDate, Instant deadline) {
        // 직전 거래일로 되돌리지 않는다. 토요일도 실행해야 금요일 데이터를 회수할 수 있다.
        LocalDate date = executionDate.minusDays(1);
        var remaining = new AtomicReference<List<MissingDailyPriceStock>>(List.of());
        // 중단 후 JDBC 취소·롤백(5초 제한)이 끝나도록 마지막 10초는 새 작업에 쓰지 않는다.
        Instant workDeadline = deadline.minusSeconds(10);
        if (Instant.now().isBefore(workDeadline) == false) {
            publishFailure(date, "수집 종료 기한 경과", List.of());
            return;
        }
        var task = executor.submit(() -> collector.collect(date, List.of(), workDeadline, remaining::set));
        try {
            var result = task.get(Duration.between(Instant.now(), workDeadline).toNanos(), TimeUnit.NANOSECONDS);
            log.info(
                    "[일봉 수집 종료] | context : {}|{}|{}|{}|{}",
                    date,
                    result.status(),
                    result.passes(),
                    result.attempts(),
                    result.inserted());
            if (result.status() != StockDailyPriceCollector.Status.COMPLETE
                    && result.status() != StockDailyPriceCollector.Status.HOLIDAY) {
                publishFailure(date, result.status().name(), result.missing());
            }
        } catch (TimeoutException e) {
            task.cancel(true);
            publishFailure(date, "종료 기한 도달: 마지막 누락 목록의 저장 여부 확인 필요", remaining.get());
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            publishFailure(date, "수집 중단", remaining.get());
        } catch (ExecutionException e) {
            publishFailure(date, "수집 실행 실패: " + e.getCause().getClass().getSimpleName(), remaining.get());
        } catch (RuntimeException failure) {
            task.cancel(true);
            log.error(
                    "[일봉 수집 실행 실패] | context : {}|{}", date, failure.getClass().getSimpleName());
            events.publishEvent(new DailyPriceCollectionFailedEvent(
                    date, "수집 실행 실패: " + failure.getClass().getSimpleName(), List.of()));
        }
    }

    private void publishFailure(LocalDate date, String reason, List<MissingDailyPriceStock> missing) {
        events.publishEvent(new DailyPriceCollectionFailedEvent(
                date,
                reason,
                missing.stream()
                        .map(stock ->
                                stock.stockCode() + "(" + stock.stockName() + ") [" + (stock.krxMissing() ? "KRX" : "")
                                        + (stock.krxMissing() && stock.nxtMissing() ? ", " : "")
                                        + (stock.nxtMissing() ? "NXT" : "") + "]")
                        .toList()));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
