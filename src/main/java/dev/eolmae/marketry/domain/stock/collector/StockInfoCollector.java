package dev.eolmae.marketry.domain.stock.collector;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.event.IndustryInfoCreatedEvent;
import dev.eolmae.marketry.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.common.util.Strings;
import dev.eolmae.marketry.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketry.domain.stock.client.NextradeStockListClient;
import dev.eolmae.marketry.domain.stock.client.TossStockListClient;
import dev.eolmae.marketry.domain.stock.dto.StockInfoRequest;
import dev.eolmae.marketry.domain.stock.dto.StockInfoResponse;
import dev.eolmae.marketry.domain.stock.entity.IndustryInfo;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketry.domain.stock.repository.IndustryInfoRepository;
import dev.eolmae.marketry.domain.stock.repository.StockInfoRepository;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketry.domain.stock.util.KiwoomValueParser;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockInfoCollector {

    // 키움 응답 중 토스가 상장폐지라고 하는 종목이 이 비율을 넘으면 어느 한쪽 응답이 이상한 것으로 본다.
    private static final double MAX_DELISTED_RATIO = 0.05;

    private final KiwoomApiClient kiwoomApiClient;
    private final NextradeStockListClient nextradeStockListClient;
    private final TossStockListClient tossStockListClient;
    private final StockInfoRepository stockInfoRepository;
    private final IndustryInfoRepository industryInfoRepository;
    private final StockInfoCacheService stockInfoCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void sync() {
        Map<String, FetchStockInfo> fetchedStocks = new HashMap<>();

        try {
            for (Market market : Market.values()) {
                collectMarket(market, fetchedStocks);
            }
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.STOCK_INFO_SYNC_FAILED, e);
        }

        int fetchedCount = fetchedStocks.size();
        // 키움은 상장폐지된 종목을 응답에 오래 남겨 둔다 — 토스가 상장폐지로 알리는 종목은 응답에 없는 것처럼 다뤄 비활성으로 돌린다.
        Set<String> delistedStockCodes = findDelistedStockCodes(fetchedStocks.keySet());
        delistedStockCodes.forEach(fetchedStocks::remove);

        Map<String, IndustryInfo> industryByName = syncIndustryInfo(fetchedStocks.values());
        // null이면 이번에는 NXT 거래 가능 여부를 알 수 없다는 뜻이라 기존 값을 그대로 둔다.
        Set<String> nxtStockCodes = resolveNxtStockCodes(fetchedStocks.values());

        for (StockInfo existing : stockInfoRepository.findAll()) {
            FetchStockInfo fetched = fetchedStocks.remove(existing.getStockCode());

            if (fetched == null) {
                if (existing.isActive()) {
                    existing.markInactive();
                    log.debug("종목 비활성화: stockCode={}, stockName={}", existing.getStockCode(), existing.getStockName());
                }
                continue;
            }

            existing.update(
                    fetched.stockName(),
                    fetched.market(),
                    fetched.marketCode(),
                    industryId(fetched, industryByName),
                    fetched.listCount(),
                    fetched.lastPrice(),
                    nxtStockCodes == null ? existing.isNxtEnabled() : nxtStockCodes.contains(existing.getStockCode()));
        }

        // DB에 없던 신규 종목
        List<StockInfo> newStocks = fetchedStocks.values().stream()
                .map(fetched -> StockInfo.create(
                        fetched.stockCode(),
                        fetched.stockName(),
                        fetched.market(),
                        fetched.marketCode(),
                        industryId(fetched, industryByName),
                        fetched.listCount(),
                        fetched.lastPrice(),
                        nxtStockCodes != null && nxtStockCodes.contains(fetched.stockCode())))
                .toList();
        stockInfoRepository.saveAllAndFlush(newStocks);
        List<String> newOrdinaryStockCodes = newStocks.stream()
                .filter(stock -> StockMarketCode.isNewListingTarget(stock.getMarketCode(), stock.getStockName()))
                .map(StockInfo::getStockCode)
                .toList();
        if (!newOrdinaryStockCodes.isEmpty()) {
            eventPublisher.publishEvent(new StockInfoSyncedEvent(newOrdinaryStockCodes));
        }
        // 커밋 전에 비우면 evict~커밋 사이에 다른 스레드가 캐시를 재적재해 옛 데이터를 캐시에 굳힐 수
        // 있다 — 그래서 evict는 이 트랜잭션이 커밋된 뒤로 미룬다.
        evictCacheAfterCommit();

        log.info(
                "종목 정보 동기화 완료: 조회 종목 수={}, 상장폐지로 제외={}, NXT 거래 가능={}",
                fetchedCount,
                delistedStockCodes.size(),
                nxtStockCodes == null ? "알 수 없음(기존 값 유지)" : nxtStockCodes.size());
    }

    /**
     * 키움이 돌려준 종목 중 토스가 상장폐지로 알리는 종목코드를 찾는다. 토스 조회에 실패하면 이번에는 거르지 않는다(키움 동기화는
     * 그대로 진행한다). 토스가 상장 종목의 일정 비율을 넘게 폐지라고 하면 응답 이상으로 보고 거르지 않는다.
     */
    private Set<String> findDelistedStockCodes(Set<String> fetchedStockCodes) {
        Set<String> tossDelisted;
        try {
            tossDelisted = tossStockListClient.fetchDelistedStockCodes();
        } catch (Exception e) {
            log.warn("토스 상장폐지 종목 조회 실패, 이번에는 상장폐지 종목을 거르지 않는다: {}", e.getMessage());
            return Set.of();
        }
        Set<String> delisted = new HashSet<>(tossDelisted);
        delisted.retainAll(fetchedStockCodes);
        if (delisted.size() > fetchedStockCodes.size() * MAX_DELISTED_RATIO) {
            log.error(
                    "토스 상장폐지 종목이 키움 응답의 {}%를 넘어 응답 이상으로 보고 거르지 않는다: 폐지={}, 키움 응답={}",
                    (int) (MAX_DELISTED_RATIO * 100), delisted.size(), fetchedStockCodes.size());
            return Set.of();
        }
        return delisted;
    }

    /**
     * NXT 거래 가능 종목 코드를 정한다. 키움 응답에 nxtEnable "Y"가 있으면 그것을 쓰고, 하나도 없으면(응답에 필드가 없는
     * 경우 등) NEXTRADE 홈페이지의 편입 종목 목록으로 대신한다. 둘 다 안 되면 null을 돌려주고, 호출부는 기존 값을 유지한다.
     */
    private Set<String> resolveNxtStockCodes(Iterable<FetchStockInfo> fetchedStocks) {
        Set<String> kiwoomNxtCodes = new HashSet<>();
        for (FetchStockInfo fetched : fetchedStocks) {
            if (fetched.nxtEnabled()) {
                kiwoomNxtCodes.add(fetched.stockCode());
            }
        }
        if (!kiwoomNxtCodes.isEmpty()) {
            return kiwoomNxtCodes;
        }
        try {
            return nextradeStockListClient.fetchTradableStockCodes();
        } catch (Exception e) {
            log.warn("NEXTRADE 편입 종목 조회 실패, NXT 거래 가능 여부는 기존 값을 유지한다: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, IndustryInfo> syncIndustryInfo(Iterable<FetchStockInfo> fetchedStocks) {
        Set<String> industryNames = new HashSet<>();
        for (FetchStockInfo fetched : fetchedStocks) {
            if (StockMarketCode.isOrdinaryShare(fetched.marketCode())
                    && fetched.categoryName() != null
                    && !fetched.categoryName().isBlank()) {
                industryNames.add(fetched.categoryName());
            }
        }
        if (industryNames.isEmpty()) {
            return Map.of();
        }

        Set<String> existingNames = industryInfoRepository.findByNameIn(industryNames).stream()
                .map(IndustryInfo::getName)
                .collect(Collectors.toSet());
        for (String name : industryNames.stream().sorted().toList()) {
            if (existingNames.contains(name)) {
                continue;
            }
            // ON CONFLICT DO NOTHING + RETURNING — 동시 수집 레이스에서 실제로 삽입된 이름만 받아
            // IndustryInfoCreatedEvent를 낸다. JPQL/QueryDSL에 없는 문법이라 JdbcTemplate으로 직접 쓴다.
            List<String> insertedNames = jdbcTemplate.query(
                    "INSERT INTO industry_info (name) VALUES (?) ON CONFLICT (name) DO NOTHING RETURNING name",
                    (resultSet, rowNumber) -> resultSet.getString("name"),
                    name);
            insertedNames.forEach(
                    insertedName -> eventPublisher.publishEvent(new IndustryInfoCreatedEvent(insertedName)));
        }
        return industryInfoRepository.findByNameIn(industryNames).stream()
                .collect(Collectors.toMap(IndustryInfo::getName, Function.identity()));
    }

    private Long industryId(FetchStockInfo fetched, Map<String, IndustryInfo> industryByName) {
        if (!StockMarketCode.isOrdinaryShare(fetched.marketCode())
                || fetched.categoryName() == null
                || fetched.categoryName().isBlank()) {
            return null;
        }
        IndustryInfo industry = industryByName.get(fetched.categoryName());
        return industry == null ? null : industry.getId();
    }

    // StockInfoCollectorTest처럼 활성 트랜잭션 없이 도는 단위 테스트에서는
    // registerSynchronization()이 IllegalStateException을 던지므로, 그런 경우엔 즉시 evict한다.
    private void evictCacheAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            stockInfoCacheService.evict();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stockInfoCacheService.evict();
            }
        });
    }

    private void collectMarket(Market market, Map<String, FetchStockInfo> fetchedStocks) {
        String mrktTp = MrktTp.from(market);

        var request = new StockInfoRequest(mrktTp);
        StockInfoResponse response = kiwoomApiClient.post(request, StockInfoResponse.class);

        if (response.list() == null) {
            return;
        }

        for (StockInfoResponse.StockItem item : response.list()) {
            String stockCode = Strings.trimToEmpty(item.code());
            if (stockCode.isEmpty()) {
                log.warn("종목코드 없는 항목 스킵: stockName={}", item.name());
                continue;
            }

            fetchedStocks.put(
                    stockCode,
                    new FetchStockInfo(
                            stockCode,
                            Strings.trimToEmpty(item.name()),
                            market,
                            item.marketCode(),
                            Strings.trimToEmpty(item.upName()),
                            KiwoomValueParser.parseLong(item.listCount()),
                            KiwoomValueParser.parseBigDecimal(item.lastPrice()),
                            "Y".equals(Strings.trimToEmpty(item.nxtEnable()))));
        }

        log.debug("종목 정보 시장별 동기화 완료: market={}", market);
    }

    /** API 응답(StockInfoResponse.StockItem) 파싱 + market 컨텍스트를 합친, DB 비교 전 임시 보관용 */
    private record FetchStockInfo(
            String stockCode,
            String stockName,
            Market market,
            String marketCode,
            String categoryName,
            Long listCount,
            BigDecimal lastPrice,
            boolean nxtEnabled) {}

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("10"); // ka10099 mrkt_tp (ka20001과 코드 체계 다름)
        final String value;

        MrktTp(String value) {
            this.value = value;
        }

        static String from(Market market) {
            return switch (market) {
                case KOSPI -> MrktTp.KOSPI.value;
                case KOSDAQ -> MrktTp.KOSDAQ.value;
            };
        }
    }
}
