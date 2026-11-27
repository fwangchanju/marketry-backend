package kr.co.marketry.domain.stock.collector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.ExtendedQuote;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.PricePoint;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.SessionTimes;
import kr.co.marketry.domain.stock.collector.UsaConstituents.Constituent;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * 미국 프리마켓 시세를 5분마다 저장한다(2026-11-27 사용자 결정, 정규장·애프터마켓은 다음 단계).
 *
 * <p>S&amp;P 500 종목을 20개씩 묶어 야후에서 받는다(종목마다 한 번씩 부르면 500번이라 묶음 25번으로 줄였다). 저장 시각은 다른 미국 시세와 같은 뉴욕
 * 현지 시각이고, 5분 단위로 내린다. 등락률은 전일 종가 대비(한국의 합산과 같은 뜻)다. 프리마켓에 아직 거래가 없는 종목은 전일 종가 그대로(0%)다.
 *
 * <p>프리마켓 시세는 그날 16:00 종가보다 늘 이른 시각이라, 장 마감 뒤에는 종가가 가장 최근 시세가 된다(텔레그램 종가 지도와 날짜 선택이 그대로다).
 * 시가총액은 직전 시세의 시가총액을 가격 변화만큼 늘리거나 줄인다. 받은 종목이 90% 미만이거나 야후가 호출을 제한하면 저장하지 않는다.
 */
@Slf4j
@Component
public class UsaPreMarketCollector {
    static final String MARKET = "USA";
    static final double MIN_SUCCESS_RATIO = 0.9;
    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(200);
    private static final int SNAPSHOT_GRID_MINUTES = 5;
    private static final int PRICE_SCALE = 2;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final YahooFinanceQuoteClient client;
    private final OverseasStockQuoteRepository repository;
    private final UsaConstituents constituents;
    private final Consumer<Duration> sleeper;
    private final Clock clock;

    @Autowired
    public UsaPreMarketCollector(
            YahooFinanceQuoteClient client, OverseasStockQuoteRepository repository, UsaConstituents constituents) {
        this(client, repository, constituents, UsaPreMarketCollector::sleep, Clock.systemUTC());
    }

    UsaPreMarketCollector(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository repository,
            UsaConstituents constituents,
            Consumer<Duration> sleeper,
            Clock clock) {
        this.client = client;
        this.repository = repository;
        this.constituents = constituents;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    public Result collect() {
        List<String> symbols =
                constituents.all().stream().map(Constituent::symbol).toList();
        Fetched fetched = fetchAll(symbols);
        if (fetched.quotes().size() < symbols.size() * MIN_SUCCESS_RATIO) {
            String reason = fetched.limited() ? "야후 호출 제한" : "응답 종목 부족";
            return Result.skipped(reason + ": " + fetched.quotes().size() + "/" + symbols.size());
        }
        SessionTimes sessions = fetched.quotes().values().iterator().next().sessions();
        Instant now = clock.instant();
        if (now.isBefore(sessions.preStart()) || now.isBefore(sessions.regularStart()) == false) {
            return Result.skipped("프리마켓 시간이 아닙니다");
        }
        Instant latest = null;
        for (ExtendedQuote quote : fetched.quotes().values()) {
            PricePoint point = lastPreMarketPrice(quote);
            if (point != null && (latest == null || point.time().isAfter(latest))) {
                latest = point.time();
            }
        }
        if (latest == null) {
            return Result.skipped("프리마켓 거래가 아직 없습니다");
        }
        LocalDateTime snapshotTime = snapshotTimeOf(latest);
        LocalDateTime lastSnapshotTime = repository
                .findFirstByIdMarketOrderByIdSnapshotTimeDesc(MARKET)
                .map(quote -> quote.getId().getSnapshotTime())
                .orElse(null);
        if (lastSnapshotTime == null) {
            return Result.skipped("기준이 될 미국 시세가 없습니다");
        }
        if (snapshotTime.isAfter(lastSnapshotTime) == false) {
            return Result.skipped("새 시세가 없습니다");
        }
        Map<String, OverseasStockQuote> lastByCode = new HashMap<>();
        for (OverseasStockQuote quote : repository.findAllByIdMarketAndIdSnapshotTime(MARKET, lastSnapshotTime)) {
            lastByCode.put(quote.getId().getStockCode(), quote);
        }
        List<OverseasStockQuote> rows = new ArrayList<>();
        for (String symbol : symbols) {
            OverseasStockQuote last = lastByCode.get(symbol);
            ExtendedQuote quote = fetched.quotes().get(symbol);
            // 직전 시세가 없는 종목(새로 편입)은 시가총액 기준이 없어 넣지 않는다. 종가 수집이 처음 넣는다.
            if (last == null || quote == null) {
                continue;
            }
            rows.add(toQuote(symbol, quote, last, snapshotTime));
        }
        repository.saveAll(rows);
        return Result.saved(snapshotTime, rows.size(), symbols.size());
    }

    // 20개씩 묶어 받는다. 야후가 호출을 제한하면(429) 나머지 묶음은 부르지 않는다.
    private Fetched fetchAll(List<String> symbols) {
        Map<String, ExtendedQuote> quotes = new HashMap<>();
        for (int start = 0; start < symbols.size(); start += YahooFinanceQuoteClient.SPARK_MAX_SYMBOLS) {
            List<String> batch =
                    symbols.subList(start, Math.min(symbols.size(), start + YahooFinanceQuoteClient.SPARK_MAX_SYMBOLS));
            try {
                for (ExtendedQuote quote : client.fetchExtendedQuotes(batch)) {
                    quotes.put(quote.symbol(), quote);
                }
            } catch (RestClientResponseException failure) {
                log.warn("[미국프리마켓] 묶음 조회 실패: status={} | context : {}", failure.getStatusCode(), batch.getFirst());
                if (failure.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                    return new Fetched(quotes, true);
                }
            } catch (RuntimeException failure) {
                log.warn("[미국프리마켓] 묶음 조회 실패: {} | context : {}", failure.getMessage(), batch.getFirst());
            }
            sleeper.accept(REQUEST_INTERVAL);
        }
        return new Fetched(quotes, false);
    }

    /** 프리마켓 시간(프리마켓 시작 ~ 정규장 시작)에 거래된 마지막 가격. 없으면 null이다. */
    static PricePoint lastPreMarketPrice(ExtendedQuote quote) {
        PricePoint found = null;
        for (PricePoint point : quote.prices()) {
            boolean inPreMarket = point.time().isBefore(quote.sessions().preStart()) == false
                    && point.time().isBefore(quote.sessions().regularStart());
            if (inPreMarket) {
                found = point;
            }
        }
        return found;
    }

    private static OverseasStockQuote toQuote(
            String symbol, ExtendedQuote quote, OverseasStockQuote last, LocalDateTime snapshotTime) {
        BigDecimal previousClose = quote.previousClose().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        PricePoint point = lastPreMarketPrice(quote);
        BigDecimal price = previousClose;
        if (point != null) {
            price = point.price().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        }
        return OverseasStockQuote.create(
                new OverseasStockQuoteId(MARKET, symbol, snapshotTime),
                price,
                previousClose,
                price.subtract(previousClose)
                        .multiply(PERCENT)
                        .divide(previousClose, PRICE_SCALE, RoundingMode.HALF_UP),
                last.getTotalMarketValue().multiply(price).divide(last.getCurrentPrice(), 0, RoundingMode.HALF_UP));
    }

    // 야후 5분 칸의 시작 시각을 뉴욕 현지 시각으로 바꿔 5분 단위로 내린다. 다른 미국 시세(16:00 종가)와 같은 현지 시각 기준이다.
    static LocalDateTime snapshotTimeOf(Instant latest) {
        LocalDateTime time = LocalDateTime.ofInstant(latest, UsaMarketSession.NEW_YORK);
        return time.withSecond(0)
                .withNano(0)
                .withMinute(time.getMinute() / SNAPSHOT_GRID_MINUTES * SNAPSHOT_GRID_MINUTES);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("미국 프리마켓 수집이 중단됐습니다", interrupted);
        }
    }

    private record Fetched(Map<String, ExtendedQuote> quotes, boolean limited) {}

    /** 한 회차 결과. 저장하지 않았으면 reason에 이유가 있다. */
    public record Result(boolean saved, LocalDateTime snapshotTime, int savedCount, int totalCount, String reason) {
        static Result saved(LocalDateTime snapshotTime, int savedCount, int totalCount) {
            return new Result(true, snapshotTime, savedCount, totalCount, null);
        }

        static Result skipped(String reason) {
            return new Result(false, null, 0, 0, reason);
        }
    }
}
