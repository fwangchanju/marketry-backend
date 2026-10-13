package dev.eolmae.marketry.domain.stock.collector;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.util.NumberParser;
import dev.eolmae.marketry.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketry.domain.stock.dto.*;
import dev.eolmae.marketry.domain.stock.entity.IndexContributionRankingSnapshot;
import dev.eolmae.marketry.domain.stock.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Kiwoom API 응답 내용 확인용 테스트 — KRX 데이터마켓 대체 가능 여부 검토
 *
 * 목적:
 *   각 API가 어떤 데이터를 얼마나 정확하게 반환하는지 확인하여
 *   KRX 데이터마켓 직접 수집으로 대체 가능한지 판단
 *
 * 실행 조건:
 *   - KIWOOM_APP_KEY, KIWOOM_SECRET 환경변수 필요
 *   - 로컬 DB 기동 필요 (Spring context 초기화용)
 *
 * 실행 명령: ./gradlew manualTest --tests "*.KiwoomApiVerificationTest" -i
 *
 * 결과 확인: assert 없음 — 로그 기반 수동 확인
 */
@Slf4j
@Tag("manual")
@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(
        properties = {
            "spring.test.database.replace=none",
            "spring.datasource.url=jdbc:postgresql://localhost:5433/market_monitor_db"
        })
class KiwoomApiVerificationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${krx.login-id:}")
    String krxLoginId;

    @Autowired
    KiwoomApiClient kiwoomApiClient;

    @Autowired
    IndexContributionRankingCollector indexContributionRankingCollector;

    @Autowired
    IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository;

    @Autowired
    MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;

    /**
     * ka20001 — 업종현재가요청 (시장종합 대시보드)
     *
     * 수집 대상: KOSPI(mrktTp=0, indsCd=001), KOSDAQ(mrktTp=1, indsCd=101)
     * 확인 포인트:
     *   - 지수값, 전일대비, 등락률, 거래대금
     *   - 상승/하락/보합/상한/하한 종목수 (API가 직접 집계해서 반환)
     *   - 시간대별 지수 틱(priceTicks) 포함 여부
     */
    //    @Test
    void ka20001_시장종합() {
        log.info("=== ka20001 업종현재가 | 현재시각={} ===", LocalDateTime.now(KST));

        for (var market : List.of(new String[] {"KOSPI", "0", "001"}, new String[] {"KOSDAQ", "1", "101"})) {
            String label = market[0];
            try {
                var response = kiwoomApiClient.post(
                        new SectorCurrentPriceRequest(market[1], market[2]), SectorCurrentPriceResponse.class);

                log.info("[ka20001][{}] raw: {}", label, response);
                log.info(
                        "[ka20001][{}] 지수={} | 전일대비={} | 등락률={}% | 거래대금={}",
                        label, response.curPrc(), response.predPre(), response.fluRt(), response.trdePrica());
                log.info(
                        "[ka20001][{}] 시장상태={} | 상승={} 하락={} 보합={} | 상한={} 하한={}",
                        label,
                        response.mrktStatClsCode(),
                        response.rising(),
                        response.fall(),
                        response.stdns(),
                        response.upl(),
                        response.lst());

                int tickCount =
                        response.priceTicks() != null ? response.priceTicks().size() : 0;
                if (tickCount > 0) {
                    var latest = response.priceTicks().getFirst();
                    log.info(
                            "[ka20001][{}] 시간대별 틱: {}건 | 최근={} 지수={}",
                            label,
                            tickCount,
                            latest.tmN(),
                            latest.curPrcN());
                } else {
                    log.info("[ka20001][{}] 시간대별 틱: 없음", label);
                }
            } catch (Exception e) {
                log.warn("[ka20001][{}] 예외 발생: {}", label, e.getMessage());
            }
        }
    }

    /**
     * ka10065 — 장중투자자별매매상위요청 (장중 투자자별 매매 상위 대시보드)
     *
     * 외국합(외국인 9000 + 외국계 9100) 기준으로 종목코드별 합산 후 상위 10건 출력
     * 확인 포인트:
     *   - 금액순(amt_qty_tp=1) / 수량순(amt_qty_tp=2) 결과 비교
     *   - 외국인 + 외국계 합산 후 순위가 실제 화면 수치와 일치하는지
     *   - mrkt_tp: 001=KOSPI, 101=KOSDAQ (ka10065는 stex_tp 없음, 통합값만 제공)
     */
    //    @Test
    void ka10065_장중투자자별매매상위() {
        log.info("=== ka10065 장중투자자별매매상위 (외국합) | 현재시각={} ===", LocalDateTime.now(KST));

        record MarketInfo(String label, String mrktTp) {}
        record AmtQtyInfo(String label, String amtQtyTp) {}
        record InvestorInfo(String label, String orgnTp) {}

        var markets = List.of(new MarketInfo("KOSPI", "001"), new MarketInfo("KOSDAQ", "101"));
        var amtQtyTypes = List.of(new AmtQtyInfo("금액순", "1"));
        var foreignInvestors = List.of(new InvestorInfo("외국인", "9000"), new InvestorInfo("외국계", "9100"));

        // stockCode → [buyQty합산, selQty합산, netslmt합산]
        record StockAgg(long buyQty, long selQty, long netslmt) {}

        for (var market : markets) {
            for (var amtQty : amtQtyTypes) {
                Map<String, long[]> aggMap = new LinkedHashMap<>(); // [buy, sel, net]
                Map<String, String> stockNameMap = new HashMap<>();

                for (var investor : foreignInvestors) {
                    try {
                        var request = new IntradayInvestorRankingRequest(
                                "1", market.mrktTp(), investor.orgnTp(), amtQty.amtQtyTp());
                        var response = kiwoomApiClient.post(request, IntradayInvestorRankingResponse.class);
                        log.info(
                                "[ka10065][{}][{}][{}] raw: {}",
                                market.label(),
                                investor.label(),
                                amtQty.label(),
                                response);
                        if (response.items() == null) {
                            continue;
                        }
                        for (var item : response.items()) {
                            long buy = NumberParser.parseLong(item.buyQty());
                            long sel = NumberParser.parseLong(item.selQty());
                            long net = NumberParser.parseLong(item.netslmt());
                            aggMap.merge(item.stkCd(), new long[] {buy, sel, net}, (a, b) ->
                                    new long[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]});
                            stockNameMap.putIfAbsent(item.stkCd(), item.stkNm());
                        }
                    } catch (Exception e) {
                        log.warn(
                                "[ka10065][{}][외국합][{}] 예외({}): {}",
                                market.label(),
                                amtQty.label(),
                                investor.label(),
                                e.getMessage());
                    }
                }

                log.info("[ka10065][{}][외국합][{}] ===== 상위 10건 =====", market.label(), amtQty.label());
                int[] rank = {1};
                aggMap.entrySet().stream()
                        .sorted(Map.Entry.<String, long[]>comparingByValue((a, b) -> Long.compare(b[2], a[2])))
                        .limit(10)
                        .forEach(e -> {
                            long[] v = e.getValue();
                            log.info(
                                    "[ka10065][{}][외국합][{}] {}위 종목코드={} 종목명={} 매수={} 매도={} 순매수={}",
                                    market.label(),
                                    amtQty.label(),
                                    rank[0]++,
                                    e.getKey(),
                                    stockNameMap.get(e.getKey()),
                                    v[0],
                                    v[1],
                                    v[2]);
                        });
            }
        }
    }

    /**
     * ka90008 — 종목시간별프로그램매매추이요청 (종목별 프로그램매매 장중 추이)
     * ka90013 — 종목일별프로그램매매추이요청 (종목별 프로그램매매 일별 추이)
     *
     * 수집 대상: 관심종목 기준 — 테스트에서는 키움증권(039490) 사용
     * 확인 포인트:
     *   - KRX(039490) vs NXT(039490_NX) 응답 비교
     *   - 응답 내 stex_tp 필드 값 확인
     *   - 프로그램매수/매도/순매수금액(amt) vs 수량(qty) 둘 다 반환 여부
     *   - 장중(ka90008): 당일 시간별 틱 수 확인
     *   - 일별(ka90013): 최근 일별 데이터 확인
     */
    /**
     * ka10014 — 공매도추이요청
     *
     * 확인 포인트:
     *   - 관심종목 개별 호출 시 응답 필드 확인 (종가, 대비, 등락률, 거래량, 공매도수량 등)
     *   - 장중 실시간 갱신 여부 — 같은 날 다른 시간에 호출 시 당일 수치 변화 확인
     *     → 달라지면: 매시간 수집(08:00~20:00) 확정
     *     → 안 달라지면: 다른 실시간 데이터소스 탐색 필요
     */

    //    @Test
    void ka10014_공매도추이() {
        String today = LocalDate.now(KST).format(DATE_FMT);
        String startDt = LocalDate.now(KST).minusDays(30).format(DATE_FMT);
        String stockCode = "039490"; // 키움증권 (테스트용)

        log.info("=== ka10014 공매도추이 | 현재시각={} ===", LocalDateTime.now(KST));

        try {
            var response = kiwoomApiClient.post(
                    new ShortSellingTrendRequest(stockCode, "2", startDt, today), ShortSellingTrendResponse.class);

            log.info("[ka10014] raw: {}", response);

            if (response.ticks() == null || response.ticks().isEmpty()) {
                log.info("[ka10014] 응답: 빈 배열");
                return;
            }

            log.info("[ka10014] 응답 {}건", response.ticks().size());
            response.ticks()
                    .forEach(tick -> log.info(
                            "[ka10014] 일자={} 종가={} 대비={} 등락률={}% 거래량={} | 공매도수량={} 누적공매도량={} 매매비중={}% 거래대금={} 평균가={}",
                            tick.dt(),
                            tick.closePric(),
                            tick.predPre(),
                            tick.fluRt(),
                            tick.trdeQty(),
                            tick.shrtsQty(),
                            tick.ovrShrtsQty(),
                            tick.trdeWght(),
                            tick.shrtsTrdePrica(),
                            tick.shrtsAvgPric()));

            // 오늘 날짜 데이터 존재 여부 확인
            response.ticks().stream()
                    .filter(t -> today.equals(t.dt() != null ? t.dt().trim() : ""))
                    .findFirst()
                    .ifPresentOrElse(
                            t -> log.info("[ka10014] 오늘({}) 데이터 존재 → 장중 실시간 갱신 가능성 있음. 다른 시간대 재실행 후 수치 비교 필요", today),
                            () -> log.info("[ka10014] 오늘({}) 데이터 없음 → 장 종료 후에만 당일 데이터 제공될 수 있음", today));

        } catch (Exception e) {
            log.warn("[ka10014] 예외 발생: {}", e.getMessage());
        }
    }

    /**
     * 지수기여도 수집기 검증 — KRX MDCSTAT01501 크롤링 기반
     *
     * 확인 포인트:
     *   - KOSPI/KOSDAQ 각 상위 50종목 스냅샷 저장 확인
     *   - 기여도 합산 검증: Σ(종목 기여도) ≈ 당일 지수 등락폭(pt)
     *     → 일치하면 연산 공식 정합성 OK
     *     → 편차 크면 유동시총 vs 전체시총 혼용 등 점검 필요
     */
    //    @Test
    void indexContributionRanking_수집_및_공식검증() {
        assumeTrue(!krxLoginId.isBlank(), "KRX 로그인 정보 미설정 — KRX_ID/KRX_PW 환경변수 확인 후 재실행");

        LocalDateTime snapshotTime =
                LocalDateTime.now(KST).withMinute(0).withSecond(0).withNano(0);
        log.info("=== 지수기여도 수집 시작 | snapshotTime={} ===", snapshotTime);

        // IndexContributionRankingCollector가 MarketOverviewSnapshot/SectorPriceSnapshot도 함께 저장함
        indexContributionRankingCollector.collect(snapshotTime);

        for (Market market : List.of(Market.KOSPI, Market.KOSDAQ)) {
            List<IndexContributionRankingSnapshot> snapshots =
                    indexContributionRankingSnapshotRepository.findByMarketTypeAndSnapshotTimeOrderByRankAsc(
                            market, snapshotTime);

            log.info("[{}] 저장 건수={}", market, snapshots.size());

            BigDecimal sumContribution = snapshots.stream()
                    .map(IndexContributionRankingSnapshot::getContributionScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.info("[{}] 상위 {}종목 기여도 합산={}", market, snapshots.size(), sumContribution);

            // 실제 지수 등락폭과 비교 (MarketOverviewSnapshot의 changeValue)
            marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime).stream()
                    .filter(overview -> overview.getMarketType() == market)
                    .findFirst()
                    .ifPresent(overview -> log.info(
                            "[{}] 실제 지수등락={} | 기여도합산={} | 편차={}",
                            market,
                            overview.getChangeValue(),
                            sumContribution,
                            sumContribution.subtract(overview.getChangeValue()).abs()));

            if (!snapshots.isEmpty()) {
                log.info("[{}] 상위 5종목:", market);
                snapshots.stream()
                        .limit(5)
                        .forEach(s -> log.info(
                                "  {}위 {} {} 기여도={} 등락률={}%",
                                s.getRank(),
                                s.getStockCode(),
                                s.getStockName(),
                                s.getContributionScore(),
                                s.getPriceChangeRate()));
            }
        }
    }

    //    @Test
    void ka90008_ka90013_종목별프로그램매매추이() {
        String today = LocalDate.now(KST).format(DATE_FMT);
        log.info("=== ka90008/ka90013 종목별프로그램매매추이 | 현재시각={} ===", LocalDateTime.now(KST));

        record Agg(long buyAmt, long sellAmt, long apiNetAmt, long buyQty, long sellQty, long apiNetQty) {
            Agg merge(Agg o) {
                return new Agg(
                        buyAmt + o.buyAmt,
                        sellAmt + o.sellAmt,
                        apiNetAmt + o.apiNetAmt,
                        buyQty + o.buyQty,
                        sellQty + o.sellQty,
                        apiNetQty + o.apiNetQty);
            }
        }

        // ka90008 — 장중 (각 틱 raw 값 확인 + 최신 틱 합산 vs 전 틱 누적합 비교)
        log.info("--- ka90008 장중(시간별) KRX raw 틱 ---");
        HourlyProgramTradeTrendResponse krx90008 = null;
        HourlyProgramTradeTrendResponse nxt90008 = null;
        try {
            krx90008 = kiwoomApiClient.post(
                    new HourlyProgramTradeTrendRequest("039490", "1", today), HourlyProgramTradeTrendResponse.class);
            nxt90008 = kiwoomApiClient.post(
                    new HourlyProgramTradeTrendRequest("039490_NX", "1", today), HourlyProgramTradeTrendResponse.class);

            // KRX raw 틱 (시간 오름차순)
            log.info(
                    "[ka90008][KRX] 틱 수={}",
                    krx90008.ticks() == null ? 0 : krx90008.ticks().size());
            if (krx90008.ticks() != null) {
                krx90008.ticks().stream()
                        .sorted((a, b) -> a.tm().compareTo(b.tm()))
                        .forEach(t -> log.info(
                                "[ka90008][KRX][raw] 시간={} 매수금액={} 매도금액={} API순매수={} 계산순매수={} | 매수수량={} 매도수량={} API순매수수량={} 계산순매수수량={}",
                                t.tm(),
                                t.prmBuyAmt(),
                                t.prmSellAmt(),
                                t.prmNetprpsAmt(),
                                NumberParser.parseLong(t.prmBuyAmt()) - NumberParser.parseLong(t.prmSellAmt()),
                                t.prmBuyQty(),
                                t.prmSellQty(),
                                t.prmNetprpsQty(),
                                NumberParser.parseLong(t.prmBuyQty()) - NumberParser.parseLong(t.prmSellQty())));
            }

            // NXT raw 틱 (시간 오름차순)
            log.info(
                    "[ka90008][NXT] 틱 수={}",
                    nxt90008.ticks() == null ? 0 : nxt90008.ticks().size());
            if (nxt90008.ticks() != null) {
                nxt90008.ticks().stream()
                        .sorted((a, b) -> a.tm().compareTo(b.tm()))
                        .forEach(t -> log.info(
                                "[ka90008][NXT][raw] 시간={} 매수금액={} 매도금액={} API순매수={} 계산순매수={} | 매수수량={} 매도수량={} API순매수수량={} 계산순매수수량={}",
                                t.tm(),
                                t.prmBuyAmt(),
                                t.prmSellAmt(),
                                t.prmNetprpsAmt(),
                                NumberParser.parseLong(t.prmBuyAmt()) - NumberParser.parseLong(t.prmSellAmt()),
                                t.prmBuyQty(),
                                t.prmSellQty(),
                                t.prmNetprpsQty(),
                                NumberParser.parseLong(t.prmBuyQty()) - NumberParser.parseLong(t.prmSellQty())));
            }

            // [가설: 틱이 누적값] KRX 최신 틱 + NXT 최신 틱 합산
            log.info("[ka90008][가설-누적] KRX 최신 틱 + NXT 최신 틱 합산 (누적값 가설 검증)");
            HourlyProgramTradeTrendResponse.TradeTick krxLatest =
                    krx90008.ticks() != null && !krx90008.ticks().isEmpty()
                            ? krx90008.ticks().stream()
                                    .max((a, b) -> a.tm().compareTo(b.tm()))
                                    .orElse(null)
                            : null;
            HourlyProgramTradeTrendResponse.TradeTick nxtLatest =
                    nxt90008.ticks() != null && !nxt90008.ticks().isEmpty()
                            ? nxt90008.ticks().stream()
                                    .max((a, b) -> a.tm().compareTo(b.tm()))
                                    .orElse(null)
                            : null;
            if (krxLatest != null) {
                log.info(
                        "[ka90008][가설-누적][KRX최신] 시간={} 매수금액={} 매도금액={}",
                        krxLatest.tm(),
                        krxLatest.prmBuyAmt(),
                        krxLatest.prmSellAmt());
            }
            if (nxtLatest != null) {
                log.info(
                        "[ka90008][가설-누적][NXT최신] 시간={} 매수금액={} 매도금액={}",
                        nxtLatest.tm(),
                        nxtLatest.prmBuyAmt(),
                        nxtLatest.prmSellAmt());
            }
            long latestBuyAmt = (krxLatest != null ? NumberParser.parseLong(krxLatest.prmBuyAmt()) : 0)
                    + (nxtLatest != null ? NumberParser.parseLong(nxtLatest.prmBuyAmt()) : 0);
            long latestSellAmt = (krxLatest != null ? NumberParser.parseLong(krxLatest.prmSellAmt()) : 0)
                    + (nxtLatest != null ? NumberParser.parseLong(nxtLatest.prmSellAmt()) : 0);
            long latestBuyQty = (krxLatest != null ? NumberParser.parseLong(krxLatest.prmBuyQty()) : 0)
                    + (nxtLatest != null ? NumberParser.parseLong(nxtLatest.prmBuyQty()) : 0);
            long latestSellQty = (krxLatest != null ? NumberParser.parseLong(krxLatest.prmSellQty()) : 0)
                    + (nxtLatest != null ? NumberParser.parseLong(nxtLatest.prmSellQty()) : 0);
            log.info(
                    "[ka90008][가설-누적][합산] 매수금액={} 매도금액={} 계산순매수={} | 매수수량={} 매도수량={} 계산순매수수량={}",
                    latestBuyAmt,
                    latestSellAmt,
                    latestBuyAmt - latestSellAmt,
                    latestBuyQty,
                    latestSellQty,
                    latestBuyQty - latestSellQty);

            // [기존 방식: 전 틱 누적합] 비교용
            log.info("[ka90008][기존-누적합] 모든 틱 합산 (이전 방식)");
            Map<String, Agg> tickMap = new LinkedHashMap<>();
            if (krx90008.ticks() != null) {
                krx90008.ticks()
                        .forEach(t -> tickMap.merge(
                                t.tm(),
                                new Agg(
                                        NumberParser.parseLong(t.prmBuyAmt()),
                                        NumberParser.parseLong(t.prmSellAmt()),
                                        NumberParser.parseLong(t.prmNetprpsAmt()),
                                        NumberParser.parseLong(t.prmBuyQty()),
                                        NumberParser.parseLong(t.prmSellQty()),
                                        NumberParser.parseLong(t.prmNetprpsQty())),
                                Agg::merge));
            }
            if (nxt90008.ticks() != null) {
                nxt90008.ticks()
                        .forEach(t -> tickMap.merge(
                                t.tm(),
                                new Agg(
                                        NumberParser.parseLong(t.prmBuyAmt()),
                                        NumberParser.parseLong(t.prmSellAmt()),
                                        NumberParser.parseLong(t.prmNetprpsAmt()),
                                        NumberParser.parseLong(t.prmBuyQty()),
                                        NumberParser.parseLong(t.prmSellQty()),
                                        NumberParser.parseLong(t.prmNetprpsQty())),
                                Agg::merge));
            }
            long[] cumBuyAmt = {0};
            long[] cumSellAmt = {0};
            long[] cumBuyQty = {0};
            long[] cumSellQty = {0};
            tickMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                var a = e.getValue();
                cumBuyAmt[0] += a.buyAmt();
                cumSellAmt[0] += a.sellAmt();
                cumBuyQty[0] += a.buyQty();
                cumSellQty[0] += a.sellQty();
            });
            log.info(
                    "[ka90008][기존-누적합][최종] 매수금액={} 매도금액={} 계산순매수={} | 매수수량={} 매도수량={} 계산순매수수량={}",
                    cumBuyAmt[0],
                    cumSellAmt[0],
                    cumBuyAmt[0] - cumSellAmt[0],
                    cumBuyQty[0],
                    cumSellQty[0],
                    cumBuyQty[0] - cumSellQty[0]);
        } catch (Exception e) {
            log.warn("[ka90008] 예외 발생: {}", e.getMessage());
        }

        // ka90013 — 일별 (KRX+NXT 날짜별 합산)
        log.info("--- ka90013 일별 KRX+NXT 합산 ---");
        try {
            var krx = kiwoomApiClient.post(
                    new DailyProgramTradeTrendRequest("039490", "1"), DailyProgramTradeTrendResponse.class);
            var nxt = kiwoomApiClient.post(
                    new DailyProgramTradeTrendRequest("039490_NX", "1"), DailyProgramTradeTrendResponse.class);
            log.info("[ka90013][KRX] raw: {}", krx);
            log.info("[ka90013][NXT] raw: {}", nxt);

            Map<String, Agg> aggMap = new LinkedHashMap<>();
            if (krx.ticks() != null) {
                krx.ticks()
                        .forEach(t -> aggMap.merge(
                                t.dt(),
                                new Agg(
                                        NumberParser.parseLong(t.prmBuyAmt()),
                                        NumberParser.parseLong(t.prmSellAmt()),
                                        NumberParser.parseLong(t.prmNetprpsAmt()),
                                        NumberParser.parseLong(t.prmBuyQty()),
                                        NumberParser.parseLong(t.prmSellQty()),
                                        NumberParser.parseLong(t.prmNetprpsQty())),
                                Agg::merge));
            }
            if (nxt.ticks() != null) {
                nxt.ticks()
                        .forEach(t -> aggMap.merge(
                                t.dt(),
                                new Agg(
                                        NumberParser.parseLong(t.prmBuyAmt()),
                                        NumberParser.parseLong(t.prmSellAmt()),
                                        NumberParser.parseLong(t.prmNetprpsAmt()),
                                        NumberParser.parseLong(t.prmBuyQty()),
                                        NumberParser.parseLong(t.prmSellQty()),
                                        NumberParser.parseLong(t.prmNetprpsQty())),
                                Agg::merge));
            }

            aggMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                var a = e.getValue();
                log.info(
                        "[ka90013][합산] 날짜={} 매수금액={} 매도금액={} API순매수={} 계산순매수={} | 매수수량={} 매도수량={} API순매수수량={} 계산순매수수량={}",
                        e.getKey(),
                        a.buyAmt(),
                        a.sellAmt(),
                        a.apiNetAmt(),
                        a.buyAmt() - a.sellAmt(),
                        a.buyQty(),
                        a.sellQty(),
                        a.apiNetQty(),
                        a.buyQty() - a.sellQty());
            });
        } catch (Exception e) {
            log.warn("[ka90013] 예외 발생: {}", e.getMessage());
        }
    }

    @Test
    void kt00018_보유종목현황() {
        try {
            var response = kiwoomApiClient.post(AccountBalanceRequest.defaults(), AccountBalanceResponse.class);
            log.info("[kt00018] raw: {}", response);
        } catch (Exception e) {
            log.warn("[kt00018] 예외: {}", e.getMessage());
        }
    }

    //    @Test
    void ka20002_업종별주가() {
        for (var market : List.of(new String[] {"KOSPI", "0", "001"}, new String[] {"KOSDAQ", "1", "101"})) {
            try {
                var response = kiwoomApiClient.post(
                        new SectorPriceListRequest(market[1], market[2], "3"), SectorPriceListResponse.class);
                log.info("[ka20002][{}] raw: {}", market[0], response);
            } catch (Exception e) {
                log.warn("[ka20002][{}] 예외: {}", market[0], e.getMessage());
            }
        }
    }

    //    @Test
    void ka10099_종목정보리스트() {
        for (String mrktTp : List.of("0", "10")) {
            try {
                var response = kiwoomApiClient.post(new StockInfoRequest(mrktTp), StockInfoResponse.class);
                log.info("[ka10099][mrkt_tp={}] raw: {}", mrktTp, response);
            } catch (Exception e) {
                log.warn("[ka10099][mrkt_tp={}] 예외: {}", mrktTp, e.getMessage());
            }
        }
    }
}
