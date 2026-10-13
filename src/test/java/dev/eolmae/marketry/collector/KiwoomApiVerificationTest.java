package dev.eolmae.marketry.domain.stock.collector;

import dev.eolmae.marketry.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketry.domain.stock.dto.*;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
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
