package dev.eolmae.marketry.domain.stock.scheduler;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketry.domain.notification.schedule.TelegramSendSchedule;
import dev.eolmae.marketry.domain.notification.service.MarketMapTelegramReportSender;
import dev.eolmae.marketry.domain.notification.service.TelegramCollectionFailureNotifier;
import dev.eolmae.marketry.domain.notification.service.TelegramReportDispatcher;
import dev.eolmae.marketry.domain.stock.collector.HoldingsSyncService;
import dev.eolmae.marketry.domain.stock.collector.IndexContributionRankingCollector;
import dev.eolmae.marketry.domain.stock.collector.IntradayInvestorRankingCollector;
import dev.eolmae.marketry.domain.stock.collector.ProgramTradeDailyCollector;
import dev.eolmae.marketry.domain.stock.collector.ProgramTradeIntradayCollector;
import dev.eolmae.marketry.domain.stock.collector.ShortSellingTrendCollector;
import dev.eolmae.marketry.domain.stock.collector.StockInfoCollector;
import dev.eolmae.marketry.domain.stock.service.CalendarDayTimes;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionScheduler {

    private final HoldingsSyncService holdingsSyncService;
    private final IntradayInvestorRankingCollector intradayInvestorRankingCollector;
    private final ProgramTradeIntradayCollector programTradeIntradayCollector;
    private final ProgramTradeDailyCollector programTradeDailyCollector;
    private final IndexContributionRankingCollector indexContributionRankingCollector;
    private final ShortSellingTrendCollector shortSellingTrendCollector;
    private final StockInfoCollector stockInfoCollector;
    private final MarketMapTelegramReportSender marketMapTelegramReportSender;
    private final TelegramReportDispatcher telegramReportDispatcher;
    private final TelegramCollectionFailureNotifier telegramCollectionFailureNotifier;
    private final TelegramSendSchedule telegramSendSchedule;
    private final EscalationPublisher escalationPublisher;

    private final MarketCalendarTimeService marketCalendarTimeService;

    private static final String KST_ZONE_ID = "Asia/Seoul";

    private volatile CollectionResult lastCollection;

    /**
     * 장중 시장 데이터 수집: 평일 collect.start-hour~end-hour, interval-minutes 간격.
     * 날짜별 시간표의 전체 세션 시작부터 종료 정각까지 수집한다.
     * 수집 직후 텔레그램 발송을 매번 호출하되, 실제 발송 여부와 주기({@link TelegramSendSchedule#due},
     * {@link TelegramSendSchedule#dueForMap})는 여기서 한 곳에서만 게이팅한다(별도 스케줄로 분리하면 두
     * 트리거의 실행 순서를 보장할 수 없어, 같은 호출 안에서 순차 실행되도록 묶었다). 섹터(15분 간격)와
     * 맵(telegram.map-send-times, 하루 세 번) 발송은 서로 독립적인 시각 판정이라 별도 if로 나뉜다.
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

            boolean success = run("지수기여도랭킹", () -> indexContributionRankingCollector.collect(snapshotTime));
            lastCollection = new CollectionResult(snapshotTime, success);

            log.info("장중 시장 데이터 수집 완료: snapshotTime={}", snapshotTime);
        }

        CollectionResult collection = lastCollection;
        if (collection == null || collection.snapshotTime().toLocalDate().equals(snapshotTime.toLocalDate()) == false) {
            return;
        }
        LocalDateTime dataTime = collection.snapshotTime();

        if (telegramSendSchedule.due(snapshotTime, shouldCollect)) {
            if (collection.success() == false) {
                run("데이터수집실패알림", () -> telegramCollectionFailureNotifier.notify(dataTime));
            } else {
                // 마켓별로 섹터 이미지 1장 + 캡션 1개씩 각각 발송.
                run("섹터텔레그램발송", () -> telegramReportDispatcher.sendSector(dataTime));
            }
        }

        // 맵 발송은 섹터와 별개 시각(telegram.map-send-times)에, 별개 판정으로 돈다. 맵 이미지는
        // sector_price_snapshot(IndexContributionRankingCollector.collectSectorPrice가 씀)으로
        // 그려지므로, 수집이 실패해도 맵 페이지는 최신 공통 시각으로 그대로 그려진다 — 그래서
        // 마지막 수집 성공 여부로 가두지 않는다. 캡션은 그 시각 랭킹이 비면 자연히
        // 빠진다(MarketMapAlbumReportSender.buildCaption).
        if (telegramSendSchedule.dueForMap(snapshotTime, shouldCollect, dayTimes.regularMarketEnd())) {
            run("맵텔레그램발송", () -> telegramReportDispatcher.sendMap(dataTime));
        }
    }

    /**
     * [구버전] 장중 시장 데이터 수집: 평일 08:00~20:00, 1시간 간격.
     * 관심종목 구조 정리가 아직 안 끝나서 collectMarketData()로 완전히 대체하지 않고 당분간
     * 비활성화 상태로 남겨둔다 — 나중에 참고하거나 필요하면 되살리기 쉽도록 코드는 그대로 유지.
     */
    // @Scheduled(cron = "0 0 8-20 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectMarketDataHourly() {
        LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
        log.info("장중 시장 데이터 수집 시작: snapshotTime={}", snapshotTime);

        run("보유종목동기화", holdingsSyncService::sync);
        run("장중투자자랭킹", () -> intradayInvestorRankingCollector.collect(snapshotTime));
        run("프로그램매매히스토리", () -> programTradeIntradayCollector.collect(snapshotTime));
        run("지수기여도랭킹", () -> indexContributionRankingCollector.collect(snapshotTime));
        run("마켓맵텔레그램발송", () -> marketMapTelegramReportSender.send(snapshotTime, MarketQuery.KOSPI));

        log.info("장중 시장 데이터 수집 완료: snapshotTime={}", snapshotTime);
    }

    /**
     * 프로그램매매 일별 이력 수집: 평일 21:00 (장 마감 후 1회)
     * 관심종목 구조 정리 전까지 비활성화.
     */
    // @Scheduled(cron = "0 0 21 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectProgramTradingDaily() {
        log.info("프로그램매매 일별 이력 수집 시작");
        run("프로그램매매일별", programTradeDailyCollector::collect);
        log.info("프로그램매매 일별 이력 수집 완료");
    }

    /**
     * 공매도 데이터 수집: 평일 20:30 (당일 자료 18:30 이후 제공)
     * 관심종목 구조 정리 전까지 비활성화.
     */
    // @Scheduled(cron = "0 30 20 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectShortSelling() {
        LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
        log.info("공매도 데이터 수집 시작: snapshotTime={}", snapshotTime);

        run("공매도", () -> shortSellingTrendCollector.collect(snapshotTime));

        log.info("공매도 데이터 수집 완료: snapshotTime={}", snapshotTime);
    }

    /**
     * 종목 정보 동기화: 평일 07:00 (장 시작 전)
     */
    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = KST_ZONE_ID)
    public void syncStockInfo() {
        log.info("종목 정보 동기화 시작");

        run("종목정보", stockInfoCollector::sync);

        log.info("종목 정보 동기화 완료");
    }

    private record CollectionResult(LocalDateTime snapshotTime, boolean success) {}

    // 예외 없이 끝나면 true, 잡히면 escalate 후 false — 호출부가 "이 단계가 성공했는지"를 별도 재조회
    // 없이 실행 결과 자체로 바로 알 수 있다. 반환값이 필요 없는 호출부는 그냥 무시하면 된다.
    private boolean run(String collectorName, Runnable task) {
        LocalDateTime startedAt = LocalDateTime.now(Zone.KST.zoneId());
        log.info("[{}] 시작: {}", collectorName, startedAt);
        boolean success = true;
        try {
            task.run();
        } catch (Exception e) {
            escalationPublisher.report(EscalateException.wrap(ErrorCode.COLLECTOR_EXECUTION_FAILED, e, collectorName));
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
