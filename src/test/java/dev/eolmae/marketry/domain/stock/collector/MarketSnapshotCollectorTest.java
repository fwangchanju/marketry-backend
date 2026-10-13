package dev.eolmae.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketry.domain.stock.dto.SectorCurrentPriceRequest;
import dev.eolmae.marketry.domain.stock.dto.SectorCurrentPriceResponse;
import dev.eolmae.marketry.domain.stock.dto.SectorPriceListRequest;
import dev.eolmae.marketry.domain.stock.dto.SectorPriceListResponse;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.service.SectorPriceCacheService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

// transactionTemplate을 mock해 collectForMarket(실제 수집)이 돌지 않게 막고, 마켓 트랜잭션 직후의
// 캐시 적재(warmSectorPriceCache, 결정 5) 부분만 따로 검증한다.
class MarketSnapshotCollectorTest {

    private final KiwoomApiClient kiwoomApiClient = Mockito.mock(KiwoomApiClient.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository =
            Mockito.mock(MarketOverviewSnapshotRepository.class);
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository =
            Mockito.mock(SectorPriceSnapshotRepository.class);
    private final SectorPriceCacheService sectorPriceCacheService = Mockito.mock(SectorPriceCacheService.class);
    // executeWithoutResult는 void라 stub 없이도 mock이 아무 것도 안 한다 — collectForMarket이 실행되지
    // 않으므로 kiwoomApiClient 등 나머지 mock은 스텁할 필요가 없다.
    private final TransactionTemplate transactionTemplate = Mockito.mock(TransactionTemplate.class);
    private final MarketSnapshotCollector collector = new MarketSnapshotCollector(
            kiwoomApiClient,
            stockInfoCacheService,
            marketOverviewSnapshotRepository,
            sectorPriceSnapshotRepository,
            sectorPriceCacheService,
            transactionTemplate);

    @Test
    void collect_마켓별_트랜잭션_직후_그_마켓의_가격_캐시를_적재한다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        when(sectorPriceCacheService.getCache(any(Market.class), any(LocalDateTime.class)))
                .thenReturn(Map.of());

        collector.collect(snapshotTime);

        verify(sectorPriceCacheService).getCache(Market.KOSPI, snapshotTime);
        verify(sectorPriceCacheService).getCache(Market.KOSDAQ, snapshotTime);
    }

    // 5-6 — 캐시 적재는 성능 최적화지 정확성 조건이 아니다. 적재가 실패해도 collect() 자체는 예외
    // 없이 끝나야 한다(호출부 CollectionScheduler가 적재 실패로 escalate하면 안 된다).
    @Test
    void collect_캐시_적재가_실패해도_수집_자체는_예외_없이_끝난다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        doThrow(new RuntimeException("cache warm failed"))
                .when(sectorPriceCacheService)
                .getCache(any(Market.class), any(LocalDateTime.class));

        assertThatCode(() -> collector.collect(snapshotTime)).doesNotThrowAnyException();
    }

    // 한 마켓의 적재만 실패해도 나머지 마켓은 적재를 계속 시도해야 한다 — 마켓 하나 실패로 그 tick
    // 전체의 적재를 포기하면 안 된다.
    @Test
    void collect_한_마켓의_캐시_적재가_실패해도_다른_마켓_적재는_계속된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        doThrow(new RuntimeException("cache warm failed"))
                .when(sectorPriceCacheService)
                .getCache(Market.KOSPI, snapshotTime);
        when(sectorPriceCacheService.getCache(Market.KOSDAQ, snapshotTime)).thenReturn(Map.of());

        collector.collect(snapshotTime);

        verify(sectorPriceCacheService).getCache(Market.KOSPI, snapshotTime);
        verify(sectorPriceCacheService).getCache(Market.KOSDAQ, snapshotTime);
    }

    // 결정 1 — 저장 필터: stockInfoCache 조회 키는 접미사를 뗀 코드다. 접미사 있는 코드("005930_AL")를
    // 그대로 넣어서 조회 키 실수를 잡는다. 주권 + ETF(marketCode="8") + 캐시에 없는 종목을 섞고
    // saveAll에 주권만 넘어가는지 확인한다.
    @Test
    @SuppressWarnings("unchecked") // ArgumentCaptor.forClass(List.class)가 raw List를 요구한다
    void collect_저장할_때_stockInfoCache에서_주권만_거른다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        // executeWithoutResult가 콜백을 실제로 실행하게 함 — collectForMarket이 돌아야 저장 필터를 검증할 수 있다.
        doAnswer(invocation -> {
                    Consumer<TransactionStatus> action = invocation.getArgument(0);
                    action.accept(null);
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());

        StockInfo ordinaryShare = StockInfo.create(
                "005930", "삼성전자", Market.KOSPI, "0", 1L, 6_000_000_000L, BigDecimal.valueOf(70_000), false);
        StockInfo etf = StockInfo.create(
                "069500", "KODEX 200", Market.KOSPI, "8", null, 100_000_000L, BigDecimal.valueOf(30_000), false);
        Map<String, StockInfo> stockInfoCache = Map.of("005930", ordinaryShare, "069500", etf);
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        SectorPriceListResponse.StockItem ordinaryItem =
                new SectorPriceListResponse.StockItem("005930_AL", "삼성전자", "70100", "2", "100", "0.14");
        SectorPriceListResponse.StockItem etfItem =
                new SectorPriceListResponse.StockItem("069500_AL", "KODEX 200", "30100", "2", "100", "0.33");
        SectorPriceListResponse.StockItem unknownItem =
                new SectorPriceListResponse.StockItem("900110_AL", "미확인종목", "1000", "2", "10", "1.0");
        var sectorPriceListResponse =
                new SectorPriceListResponse("0", "정상", List.of(ordinaryItem, etfItem, unknownItem));
        when(kiwoomApiClient.post(any(SectorPriceListRequest.class), eq(SectorPriceListResponse.class)))
                .thenReturn(sectorPriceListResponse);

        var sectorCurrentPriceResponse = new SectorCurrentPriceResponse(
                "0", "정상", "1000", "2", "10", "0.1", "0", "0", "0", "0", "0", "0", "1", "0", "0", "0", "0", "0",
                List.of());
        when(kiwoomApiClient.post(any(SectorCurrentPriceRequest.class), eq(SectorCurrentPriceResponse.class)))
                .thenReturn(sectorCurrentPriceResponse);

        collector.collect(snapshotTime);

        ArgumentCaptor<List<SectorPriceSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(sectorPriceSnapshotRepository, Mockito.times(2)).saveAll(captor.capture());

        for (List<SectorPriceSnapshot> saved : captor.getAllValues()) {
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getStockCode()).isEqualTo("005930");
        }
    }
}
