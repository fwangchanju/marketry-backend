package dev.eolmae.marketry.domain.stock.collector;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.util.StockCode;
import dev.eolmae.marketry.common.util.Strings;
import dev.eolmae.marketry.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketry.domain.stock.dto.SectorCurrentPriceRequest;
import dev.eolmae.marketry.domain.stock.dto.SectorCurrentPriceResponse;
import dev.eolmae.marketry.domain.stock.dto.SectorPriceListRequest;
import dev.eolmae.marketry.domain.stock.dto.SectorPriceListResponse;
import dev.eolmae.marketry.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.enums.StexType;
import dev.eolmae.marketry.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.service.SectorPriceCacheService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketry.domain.stock.util.KiwoomValueParser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketSnapshotCollector {

    /**
     * 시장 스냅샷 수집
     * ka20001: 업종현재가요청 — 지수 요약
     * ka20002: 업종별주가요청 — 종목별 시세
     * ka10099: stockInfoCache — 저장 대상(코스피·코스닥 주권) 판정
     */
    private final KiwoomApiClient kiwoomApiClient;

    private final StockInfoCacheService stockInfoCacheService;
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final SectorPriceCacheService sectorPriceCacheService;
    private final TransactionTemplate transactionTemplate;

    // 마켓별로 독립된 트랜잭션. 하나 실패하면 그대로 예외를 던져서(catch 안 함) 이후 마켓은 시도하지 않고,
    // 호출부(CollectionScheduler.run())가 한 곳에서만 escalate한다.
    public void collect(LocalDateTime snapshotTime) {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();

        for (Market market : Market.values()) {
            transactionTemplate.executeWithoutResult(status -> collectForMarket(market, stockInfoCache, snapshotTime));
            warmSectorPriceCache(market, snapshotTime);
        }
    }

    // 결정 5의 캐시 적재(워밍) — 마켓 트랜잭션이 커밋된 직후, 그 마켓에 대해 캐시 메서드를 한 번 불러
    // DB에서 다시 읽어 캐시에 채운다. collectSectorPrice의 API 응답을 직접 넣지 않는 이유는, 그 메서드가
    // 행이 이미 있으면 저장을 건너뛰면서도 API 응답은 그대로 돌려주기 때문이다(응답과 DB가 다를 수 있음).
    // 적재는 성능 최적화지 정확성 조건이 아니라, 실패해도 수집 자체를 실패로 만들면 안 된다 — 읽을 때
    // 캐시에 없으면 DB에서 그냥 읽는다.
    private void warmSectorPriceCache(Market market, LocalDateTime snapshotTime) {
        try {
            sectorPriceCacheService.getCache(market, snapshotTime);
        } catch (Exception e) {
            log.warn("섹터 가격 캐시 적재 실패: market={}, snapshotTime={}", market, snapshotTime, e);
        }
    }

    private void collectForMarket(Market market, Map<String, StockInfo> stockInfoCache, LocalDateTime snapshotTime) {
        String mrktTp = MrktTp.from(market);
        String indsCd = IndsCd.from(market);

        collectMarketOverview(market, mrktTp, indsCd, snapshotTime); // ka20001
        collectSectorPrice(market, mrktTp, indsCd, snapshotTime, stockInfoCache); // ka20002
    }

    private void collectMarketOverview(Market market, String mrktTp, String indsCd, LocalDateTime snapshotTime) {
        var request = new SectorCurrentPriceRequest(mrktTp, indsCd);
        SectorCurrentPriceResponse response = kiwoomApiClient.post(request, SectorCurrentPriceResponse.class);

        if (!marketOverviewSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, snapshotTime)) {
            marketOverviewSnapshotRepository.save(toMarketOverviewEntity(market, snapshotTime, response));
        }
    }

    private static MarketOverviewSnapshot toMarketOverviewEntity(
            Market market, LocalDateTime snapshotTime, SectorCurrentPriceResponse response) {
        return MarketOverviewSnapshot.create(
                market,
                snapshotTime,
                KiwoomValueParser.parseBigDecimal(response.curPrc()).abs(),
                KiwoomValueParser.parseBigDecimal(response.predPre()),
                KiwoomValueParser.parseBigDecimal(response.fluRt()),
                KiwoomValueParser.parseBigDecimal(response.trdePrica()),
                Strings.trimToEmpty(response.mrktStatClsCode()),
                KiwoomValueParser.parseInt(response.rising()),
                KiwoomValueParser.parseInt(response.fall()),
                KiwoomValueParser.parseInt(response.stdns()),
                KiwoomValueParser.parseInt(response.upl()),
                KiwoomValueParser.parseInt(response.lst()),
                LocalDateTime.now(Zone.KST.zoneId()));
    }

    private void collectSectorPrice(
            Market market,
            String mrktTp,
            String indsCd,
            LocalDateTime snapshotTime,
            Map<String, StockInfo> stockInfoCache) {

        String stexTp = StexType.KRX_NXT.code(); // KRX+NXT 합산
        var request = new SectorPriceListRequest(mrktTp, indsCd, stexTp);
        SectorPriceListResponse response = kiwoomApiClient.post(request, SectorPriceListResponse.class);

        if (response.items() == null || response.items().isEmpty()) {
            return;
        }

        if (!sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, snapshotTime)) {
            List<SectorPriceSnapshot> entities = new ArrayList<>();
            for (SectorPriceListResponse.StockItem item : response.items()) {
                // 화면은 코스피·코스닥 주권만 쓰므로 ELW·ETF 등과 stock_info에 없는 신규
                // 상장 종목은 저장하지 않는다
                StockInfo stockInfo = stockInfoCache.get(StockCode.removeSuffix(item.stkCd()));
                if (stockInfo == null || !StockMarketCode.isOrdinaryShare(stockInfo.getMarketCode())) {
                    continue;
                }
                entities.add(toSectorPriceEntity(market, snapshotTime, item));
            }
            sectorPriceSnapshotRepository.saveAll(entities);
        }
    }

    private static SectorPriceSnapshot toSectorPriceEntity(
            Market market, LocalDateTime snapshotTime, SectorPriceListResponse.StockItem item) {
        return SectorPriceSnapshot.create(
                market,
                snapshotTime,
                StockCode.removeSuffix(item.stkCd()),
                ExchangeType.from(item.stkCd()),
                Strings.trimToEmpty(item.stkNm()),
                KiwoomValueParser.parseBigDecimal(item.curPrc()).abs(),
                KiwoomValueParser.parseBigDecimal(item.predPre()),
                KiwoomValueParser.parseBigDecimal(item.fluRt()));
    }

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("1"); // ka20001/ka20002 mrkt_tp
        final String value;

        MrktTp(String value) {
            this.value = value;
        }

        static String from(Market market) {
            return switch (market) {
                case KOSPI -> KOSPI.value;
                case KOSDAQ -> KOSDAQ.value;
            };
        }
    }

    private enum IndsCd {
        KOSPI("001"),
        KOSDAQ("101"); // ka20001/ka20002 inds_cd
        final String value;

        IndsCd(String value) {
            this.value = value;
        }

        static String from(Market market) {
            return switch (market) {
                case KOSPI -> KOSPI.value;
                case KOSDAQ -> KOSDAQ.value;
            };
        }
    }
}
