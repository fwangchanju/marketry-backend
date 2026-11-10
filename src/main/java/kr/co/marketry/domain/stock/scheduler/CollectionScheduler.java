package kr.co.marketry.domain.stock.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.common.exception.BusinessException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.notification.schedule.TelegramSendSchedule;
import kr.co.marketry.domain.notification.service.TelegramCollectionFailureNotifier;
import kr.co.marketry.domain.notification.service.TelegramReportDispatcher;
import kr.co.marketry.domain.stock.collector.MarketSnapshotCollector;
import kr.co.marketry.domain.stock.collector.StockInfoCollector;
import kr.co.marketry.domain.stock.collector.StockSuspensionCollector;
import kr.co.marketry.domain.stock.service.CalendarDayTimes;
import kr.co.marketry.domain.stock.service.MarketCalendarTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionScheduler {

    private final MarketSnapshotCollector marketSnapshotCollector;
    private final StockInfoCollector stockInfoCollector;
    private final StockSuspensionCollector stockSuspensionCollector;
    private final TelegramReportDispatcher telegramReportDispatcher;
    private final TelegramCollectionFailureNotifier telegramCollectionFailureNotifier;
    private final TelegramSendSchedule telegramSendSchedule;
    private final EscalationPublisher escalationPublisher;

    private final MarketCalendarTimeService marketCalendarTimeService;

    private static final String KST_ZONE_ID = "Asia/Seoul";

    private volatile CollectionResult lastCollection;
    private volatile String lastCollectionFailureCode;

    /**
     * 장중 시장 데이터 수집: 평일 collect.start-hour~end-hour, interval-minutes 간격.
     * 날짜별 시간표의 전체 세션 시작부터 종료 정각까지 수집한다.
     * 수집 직후 텔레그램 발송을 매번 호출하되, 실제 발송 여부와 주기({@link TelegramSendSchedule#due})는
     * 여기서 한 곳에서만 게이팅한다(별도 스케줄로 분리하면 수집과 발송의 실행 순서를 보장할 수 없어, 같은 호출
     * 안에서 순차 실행되도록 묶었다). 정기 발송은 시장 지도 이미지 한 장을 보낸다.
     */
    @Scheduled(
            cron = "0 0/${collect.interval-minutes} ${collect.start-hour}-${collect.end-hour} * * MON-FRI",
            zone = KST_ZONE_ID)
    public void collectMarketData() {
        collectMarketData(KstClock.getNowTruncateMinute());
    }

    void collectMarketData(LocalDateTime snapshotTime) {
        CalendarDayTimes dayTimes = marketCalendarTimeService.resolveForCollection(snapshotTime.toLocalDate());
        if (dayTimes.holiday()) {
            log.info("[휴장일 수집 생략] | context : KR|{}", snapshotTime.toLocalDate());
            return;
        }

        boolean shouldCollect =
                !snapshotTime.isBefore(dayTimes.collectionStart()) && !snapshotTime.isAfter(dayTimes.collectionEnd());
        if (shouldCollect) {
            log.info("장중 시장 데이터 수집 시작: snapshotTime={}", snapshotTime);

            lastCollectionFailureCode = null;
            boolean success = run("시장스냅샷", () -> marketSnapshotCollector.collect(snapshotTime));
            lastCollection = new CollectionResult(snapshotTime, success, lastCollectionFailureCode);

            log.info("장중 시장 데이터 수집 완료: snapshotTime={}", snapshotTime);
        }

        CollectionResult collection = lastCollection;
        if (collection == null || collection.snapshotTime().toLocalDate().equals(snapshotTime.toLocalDate()) == false) {
            return;
        }
        LocalDateTime dataTime = collection.snapshotTime();

        // 정기 발송 시각은 관리자 메시지 페이지에서 바꾸고(기본 08:05부터 30분 간격), 발송 스위치가 꺼져 있어도 수집 실패 알림은 간다.
        if (telegramSendSchedule.due(snapshotTime, shouldCollect)) {
            if (collection.success() == false) {
                run("데이터수집실패알림", () -> telegramCollectionFailureNotifier.notify(dataTime));
            } else if (telegramSendSchedule.sendEnabled()) {
                // 수집 종료 뒤 due(false)는 설정된 endHour:sendMinute 한 번뿐이라 이 회차가 일일 마지막 발송이다.
                boolean finalDailySend = !shouldCollect;
                run("홈텔레그램발송", () -> telegramReportDispatcher.sendHome(dataTime, finalDailySend));
            }
        }
    }

    /**
     * 종목 정보 동기화와 거래정지 종목 기록: 평일 07:00 (장 시작 전)
     */
    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = KST_ZONE_ID)
    public void syncStockInfo() {
        log.info("종목 정보 동기화 시작");

        run("종목정보", stockInfoCollector::sync);
        run("거래정지종목", stockSuspensionCollector::sync);

        log.info("종목 정보 동기화 완료");
    }

    private record CollectionResult(LocalDateTime snapshotTime, boolean success, String failureCode) {}

    public record CollectionStatus(LocalDateTime snapshotTime, boolean success, String failureCode) {}

    public CollectionStatus lastCollectionStatus() {
        CollectionResult collection = lastCollection;
        return collection == null
                ? null
                : new CollectionStatus(collection.snapshotTime(), collection.success(), collection.failureCode());
    }

    // 예외 없이 끝나면 true, 잡히면 escalate 후 false — 호출부가 "이 단계가 성공했는지"를 별도 재조회
    // 없이 실행 결과 자체로 바로 알 수 있다. 반환값이 필요 없는 호출부는 그냥 무시하면 된다.
    private boolean run(String collectorName, Runnable task) {
        LocalDateTime startedAt = LocalDateTime.now(Zone.KST.zoneId());
        log.info("[{}] 시작: {}", collectorName, startedAt);
        boolean success = true;
        try {
            task.run();
            escalationPublisher.resolve(collectorName);
        } catch (Exception e) {
            if ("시장스냅샷".equals(collectorName)) {
                lastCollectionFailureCode = e instanceof BusinessException businessException
                        ? businessException.getErrorCode().name()
                        : e.getClass().getSimpleName();
            }
            escalationPublisher.report(
                    collectorName, EscalateException.wrap(ErrorCode.COLLECTOR_EXECUTION_FAILED, e, collectorName));
            success = false;
        } finally {
            LocalDateTime finishedAt = LocalDateTime.now(Zone.KST.zoneId());
            log.info(
                    "[{}] 종료: {} (소요 {}ms)",
                    collectorName,
                    finishedAt,
                    Duration.between(startedAt, finishedAt).toMillis());
        }
        return success;
    }
}
