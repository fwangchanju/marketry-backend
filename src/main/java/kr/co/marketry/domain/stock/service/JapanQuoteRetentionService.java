package kr.co.marketry.domain.stock.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuote;
import kr.co.marketry.domain.stock.repository.OverseasIndexQuoteRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일본 5분 시세는 최근 거래일 몇 개만 그대로 두고, 그 앞 날짜는 그날의 마지막 시세(종가)만 남긴다. 일본 휴장일은 시세가 저장되지 않아 시세가 있는
 * 날짜를 거래일로 센다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JapanQuoteRetentionService {
    static final String MARKET = "JAPAN";
    static final String INDEX_MARKET = "NIKKEI225";

    private final OverseasStockQuoteRepository repository;
    private final OverseasIndexQuoteRepository indexRepository;
    private final TransactionTemplate transactionTemplate;

    public void cleanup(int retainedTradingDays) {
        Map<LocalDate, List<LocalDateTime>> timesByDate = new TreeMap<>();
        for (LocalDateTime time : repository.findSnapshotTimes(MARKET)) {
            timesByDate
                    .computeIfAbsent(time.toLocalDate(), ignored -> new java.util.ArrayList<>())
                    .add(time);
        }
        List<LocalDate> dates = List.copyOf(timesByDate.keySet());
        for (LocalDate date : cleanupDates(dates, retainedTradingDays)) {
            List<LocalDateTime> times = timesByDate.get(date);
            if (times.size() >= 2) {
                LocalDateTime last =
                        times.stream().max(LocalDateTime::compareTo).orElseThrow();
                Integer deleted = transactionTemplate.execute(status -> repository.deleteDayExceptLast(
                        MARKET, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), last));
                log.info("[일본시세정리] 삭제완료 | context : {}|{}", date, deleted);
            }
            cleanupIndexQuotes(date);
        }
    }

    // 종목과 같은 날짜의 지수 값도 마지막 값만 남긴다. 지수 정리가 실패해도 종목 정리는 이미 끝났다.
    private void cleanupIndexQuotes(LocalDate date) {
        List<OverseasIndexQuote> day =
                indexRepository
                        .findByIdMarketAndIdSnapshotTimeGreaterThanEqualAndIdSnapshotTimeLessThanOrderByIdSnapshotTimeAsc(
                                INDEX_MARKET,
                                date.atStartOfDay(),
                                date.plusDays(1).atStartOfDay());
        List<OverseasIndexQuote> older = indexQuotesToDelete(day);
        if (older.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> indexRepository.deleteAll(older));
        log.info("[일본지수정리] 삭제완료 | context : {}|{}", date, older.size());
    }

    /** 시각 순으로 정렬된 하루치 지수 값에서 마지막 하나만 빼고 지울 값을 돌려준다. */
    static List<OverseasIndexQuote> indexQuotesToDelete(List<OverseasIndexQuote> dayAscending) {
        return dayAscending.size() < 2 ? List.of() : dayAscending.subList(0, dayAscending.size() - 1);
    }

    /** 오래된 순으로 정렬된 날짜 중 최근 retainedTradingDays개를 뺀 앞 날짜. */
    static List<LocalDate> cleanupDates(List<LocalDate> datesAscending, int retainedTradingDays) {
        int end = Math.max(0, datesAscending.size() - retainedTradingDays);
        return datesAscending.subList(0, end);
    }
}
