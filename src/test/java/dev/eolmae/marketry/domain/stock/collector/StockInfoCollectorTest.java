package dev.eolmae.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.event.IndustryInfoCreatedEvent;
import dev.eolmae.marketry.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketry.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketry.domain.stock.client.NextradeStockListClient;
import dev.eolmae.marketry.domain.stock.client.TossStockListClient;
import dev.eolmae.marketry.domain.stock.dto.StockInfoRequest;
import dev.eolmae.marketry.domain.stock.dto.StockInfoResponse;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.repository.IndustryInfoRepository;
import dev.eolmae.marketry.domain.stock.repository.StockInfoRepository;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class StockInfoCollectorTest {

    private final KiwoomApiClient kiwoomApiClient = Mockito.mock(KiwoomApiClient.class);
    private final NextradeStockListClient nextradeStockListClient = Mockito.mock(NextradeStockListClient.class);
    private final TossStockListClient tossStockListClient = Mockito.mock(TossStockListClient.class);
    private final StockInfoRepository stockInfoRepository = Mockito.mock(StockInfoRepository.class);
    private final IndustryInfoRepository industryInfoRepository = Mockito.mock(IndustryInfoRepository.class);
    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final StockInfoCollector collector = new StockInfoCollector(
            kiwoomApiClient,
            nextradeStockListClient,
            tossStockListClient,
            stockInfoRepository,
            industryInfoRepository,
            stockInfoCacheService,
            eventPublisher,
            jdbcTemplate);

    @Test
    void sync_신규_industry_info가_생기면_업종_생성_이벤트를_발행한다() {
        StockInfoResponse response = new StockInfoResponse(
                "0",
                "정상",
                List.of(
                        new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000", null),
                        new StockInfoResponse.StockItem("051910", "LG화학", "0", "", "50", "20000", null)));
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(response);
        when(stockInfoRepository.findAll()).thenReturn(List.of());
        when(industryInfoRepository.findByNameIn(Mockito.anyCollection())).thenReturn(List.of());
        Mockito.doReturn(List.of("반도체"))
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<RowMapper<String>>any(), Mockito.<Object[]>any());

        collector.sync();

        verify(eventPublisher)
                .publishEvent(Mockito.<Object>argThat(event -> event instanceof IndustryInfoCreatedEvent industry
                        && industry.name().equals(response.list().get(0).upName())));
        verify(eventPublisher)
                .publishEvent(Mockito.<Object>argThat(event -> event instanceof StockInfoSyncedEvent synced
                        && synced.stockCodes().size() == 2
                        && synced.stockCodes().containsAll(List.of("005930", "051910"))));
    }

    @Test
    void sync_nxtEnable이_Y인_종목만_NXT_거래_가능으로_저장한다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse(
                        "0",
                        "정상",
                        List.of(
                                new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000", "Y"),
                                new StockInfoResponse.StockItem("051910", "LG화학", "0", "", "50", "20000", ""),
                                new StockInfoResponse.StockItem("000660", "SK하이닉스", "0", "", "50", "20000", null))));
        when(stockInfoRepository.findAll()).thenReturn(List.of());
        when(industryInfoRepository.findByNameIn(Mockito.anyCollection())).thenReturn(List.of());
        Mockito.doReturn(List.of("반도체"))
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<RowMapper<String>>any(), Mockito.<Object[]>any());

        collector.sync();

        verify(stockInfoRepository).saveAllAndFlush(Mockito.<List<StockInfo>>argThat(saved -> saved.stream()
                .filter(StockInfo::isNxtEnabled)
                .map(StockInfo::getStockCode)
                .toList()
                .equals(List.of("005930"))));
    }

    @Test
    void sync_키움에_nxtEnable이_하나도_없으면_NEXTRADE_편입_종목으로_채운다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse(
                        "0",
                        "정상",
                        List.of(
                                new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000", null),
                                new StockInfoResponse.StockItem("051910", "LG화학", "0", "", "50", "20000", ""))));
        when(nextradeStockListClient.fetchTradableStockCodes()).thenReturn(Set.of("005930"));
        when(stockInfoRepository.findAll()).thenReturn(List.of());
        when(industryInfoRepository.findByNameIn(Mockito.anyCollection())).thenReturn(List.of());
        Mockito.doReturn(List.of("반도체"))
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<RowMapper<String>>any(), Mockito.<Object[]>any());

        collector.sync();

        verify(stockInfoRepository).saveAllAndFlush(Mockito.<List<StockInfo>>argThat(saved -> saved.stream()
                .filter(StockInfo::isNxtEnabled)
                .map(StockInfo::getStockCode)
                .toList()
                .equals(List.of("005930"))));
    }

    @Test
    void sync_키움에_nxtEnable_Y가_있으면_NEXTRADE를_호출하지_않는다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse(
                        "0",
                        "정상",
                        List.of(new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000", "Y"))));
        when(stockInfoRepository.findAll()).thenReturn(List.of());
        when(industryInfoRepository.findByNameIn(Mockito.anyCollection())).thenReturn(List.of());
        Mockito.doReturn(List.of("반도체"))
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<RowMapper<String>>any(), Mockito.<Object[]>any());

        collector.sync();

        verify(nextradeStockListClient, never()).fetchTradableStockCodes();
    }

    @Test
    void sync_NEXTRADE_조회가_실패하면_기존_NXT_값을_유지하고_동기화는_계속한다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse(
                        "0",
                        "정상",
                        List.of(new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000", null))));
        when(nextradeStockListClient.fetchTradableStockCodes()).thenThrow(new IllegalStateException("응답 이상"));
        StockInfo existing =
                StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", null, 100L, java.math.BigDecimal.TEN, true);
        when(stockInfoRepository.findAll()).thenReturn(List.of(existing));
        when(industryInfoRepository.findByNameIn(Mockito.anyCollection())).thenReturn(List.of());
        Mockito.doReturn(List.of("반도체"))
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<RowMapper<String>>any(), Mockito.<Object[]>any());

        collector.sync();

        org.assertj.core.api.Assertions.assertThat(existing.isNxtEnabled()).isTrue();
    }

    @AfterEach
    void clearSynchronization() {
        // 테스트가 initSynchronization()만 하고 clear를 안 하면 ThreadLocal 상태가 다음 테스트로 샌다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sync_활성_트랜잭션이_없으면_캐시를_즉시_비운다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse("0", "정상", null));
        when(stockInfoRepository.findAll()).thenReturn(List.of());

        collector.sync();

        verify(stockInfoCacheService).evict();
        verify(eventPublisher, never()).publishEvent(any(StockInfoSyncedEvent.class));
    }

    @Test
    void sync_트랜잭션_커밋_전에는_캐시를_비우지_않고_커밋_후에_비운다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse("0", "정상", null));
        when(stockInfoRepository.findAll()).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();

        collector.sync();

        verify(stockInfoCacheService, never()).evict();

        TransactionSynchronizationUtils.triggerAfterCommit();

        verify(stockInfoCacheService).evict();
    }

    @Test
    void sync_ELW_ETF_등_주권_외_종목의_업종은_사용자_업종으로_전파하지_않는다() {
        StockInfoResponse response = new StockInfoResponse(
                "0",
                "정상",
                List.of(
                        new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000", null),
                        new StockInfoResponse.StockItem("57JJJJ", "삼성전자ELW", "3", "ELW", "100", "1000", null),
                        new StockInfoResponse.StockItem("069500", "KODEX200", "8", "ETF", "100", "30000", null)));
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(response);
        when(stockInfoRepository.findAll()).thenReturn(List.of());
        when(industryInfoRepository.findByNameIn(Mockito.anyCollection())).thenReturn(List.of());
        Mockito.doReturn(List.of("반도체"))
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<RowMapper<String>>any(), Mockito.<Object[]>any());

        collector.sync();

        verify(eventPublisher)
                .publishEvent(Mockito.<Object>argThat(event -> event instanceof IndustryInfoCreatedEvent industry
                        && industry.name().equals(response.list().get(0).upName())));
        verify(eventPublisher)
                .publishEvent(Mockito.<Object>argThat(event -> event instanceof StockInfoSyncedEvent synced
                        && synced.stockCodes().equals(List.of("005930"))));
        InOrder insertionOrder = Mockito.inOrder(stockInfoRepository, eventPublisher);
        insertionOrder.verify(stockInfoRepository).saveAllAndFlush(any());
        insertionOrder.verify(eventPublisher).publishEvent(any(StockInfoSyncedEvent.class));
    }

    @Test
    void sync_비활성_기존_종목의_재활성화에는_신규상장_이벤트를_발행하지_않는다() {
        StockInfo inactive = StockInfo.create("005930", "Samsung", Market.KOSPI, "0", null, 100L, null, false);
        inactive.markInactive();
        StockInfoResponse response = new StockInfoResponse(
                "0",
                "normal",
                List.of(new StockInfoResponse.StockItem("005930", "Samsung", "0", "", "100", "10000", null)));
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(response);
        when(stockInfoRepository.findAll()).thenReturn(List.of(inactive));

        collector.sync();

        verify(eventPublisher, never()).publishEvent(any(StockInfoSyncedEvent.class));
    }

    @Test
    void sync_토스가_상장폐지로_알리는_종목은_응답에_없는_것처럼_비활성으로_돌린다() {
        StockInfo delisted = StockInfo.create("100005", "폐지종목", Market.KOSPI, "0", null, 100L, null, false);
        StockInfo listed = StockInfo.create("100001", "상장종목", Market.KOSPI, "0", null, 100L, null, false);
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse("0", "정상", kiwoomItems(20)));
        when(tossStockListClient.fetchDelistedStockCodes()).thenReturn(Set.of("100005"));
        when(stockInfoRepository.findAll()).thenReturn(List.of(delisted, listed));

        collector.sync();

        assertThat(delisted.isActive()).isFalse();
        assertThat(listed.isActive()).isTrue();
    }

    @Test
    void sync_토스가_상장폐지로_알리는_신규_종목은_저장하지_않는다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse("0", "정상", kiwoomItems(20)));
        when(tossStockListClient.fetchDelistedStockCodes()).thenReturn(Set.of("100005"));
        when(stockInfoRepository.findAll()).thenReturn(List.of());

        collector.sync();

        verify(stockInfoRepository)
                .saveAllAndFlush(Mockito.<List<StockInfo>>argThat(saved -> saved.size() == 19
                        && saved.stream()
                                .noneMatch(stock -> stock.getStockCode().equals("100005"))));
    }

    @Test
    void sync_토스_조회가_실패하면_상장폐지를_거르지_않고_동기화는_계속한다() {
        StockInfo existing = StockInfo.create("100001", "종목1", Market.KOSPI, "0", null, 100L, null, false);
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse("0", "정상", kiwoomItems(20)));
        when(tossStockListClient.fetchDelistedStockCodes()).thenThrow(new IllegalStateException("조회 실패"));
        when(stockInfoRepository.findAll()).thenReturn(List.of(existing));

        collector.sync();

        assertThat(existing.isActive()).isTrue();
        verify(stockInfoRepository).saveAllAndFlush(Mockito.<List<StockInfo>>argThat(saved -> saved.size() == 19));
    }

    @Test
    void sync_토스_상장폐지가_키움_응답의_5퍼센트를_넘으면_응답_이상으로_보고_거르지_않는다() {
        StockInfo existing = StockInfo.create("100001", "종목1", Market.KOSPI, "0", null, 100L, null, false);
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse("0", "정상", kiwoomItems(20)));
        // 20개 중 2개(10%)가 폐지라고 하면 5%를 넘는다.
        when(tossStockListClient.fetchDelistedStockCodes()).thenReturn(Set.of("100001", "100002"));
        when(stockInfoRepository.findAll()).thenReturn(List.of(existing));

        collector.sync();

        assertThat(existing.isActive()).isTrue();
    }

    private static List<StockInfoResponse.StockItem> kiwoomItems(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> new StockInfoResponse.StockItem(
                        String.valueOf(100_000 + index), "종목" + index, "0", "", "100", "10000", null))
                .toList();
    }

    @Test
    void sync_신규_스팩주도_신규_상장_이벤트에_포함하고_신주인수권과_ELW는_제외한다() {
        StockInfoResponse response = new StockInfoResponse(
                "0",
                "normal",
                List.of(
                        new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000", null),
                        new StockInfoResponse.StockItem("0164H0", "한국제16호스팩", "30", "", "100", "2000", null),
                        new StockInfoResponse.StockItem("0164H1", "한국제16호스팩 신주인수권", "5", "", "100", "100", null),
                        new StockInfoResponse.StockItem("57JJJJ", "삼성스팩ELW", "3", "ELW", "100", "1000", null)));
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(response);
        when(stockInfoRepository.findAll()).thenReturn(List.of());
        when(industryInfoRepository.findByNameIn(Mockito.anyCollection())).thenReturn(List.of());
        Mockito.doReturn(List.of("반도체"))
                .when(jdbcTemplate)
                .query(Mockito.anyString(), Mockito.<RowMapper<String>>any(), Mockito.<Object[]>any());

        collector.sync();

        verify(eventPublisher)
                .publishEvent(Mockito.<Object>argThat(event -> event instanceof StockInfoSyncedEvent synced
                        && synced.stockCodes().size() == 2
                        && synced.stockCodes().containsAll(List.of("005930", "0164H0"))));
    }
}
