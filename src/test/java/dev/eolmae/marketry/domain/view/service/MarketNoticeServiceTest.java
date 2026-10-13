package dev.eolmae.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.entity.StockTradingSuspension;
import dev.eolmae.marketry.domain.stock.repository.StockTradingSuspensionRepository;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketry.domain.view.dto.MarketNoticeItem;
import dev.eolmae.marketry.domain.view.enums.MarketNoticeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MarketNoticeServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private final StockTradingSuspensionRepository suspensionRepository =
            Mockito.mock(StockTradingSuspensionRepository.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final MarketNoticeService service = new MarketNoticeService(suspensionRepository, stockInfoCacheService);

    private StockInfo stock(String code, String name, String marketCode, LocalDate createdOn) {
        StockInfo stock = StockInfo.create(code, name, Market.KOSPI, marketCode, null, 100L, BigDecimal.TEN, false);
        ReflectionTestUtils.setField(stock, "createdAt", createdOn.atTime(7, 0));
        return stock;
    }

    private StockTradingSuspension excludedOn(String code, LocalDate excludedFrom) {
        var suspension =
                StockTradingSuspension.start(code, excludedFrom.minusDays(6), LocalDateTime.of(2026, 10, 8, 7, 0));
        suspension.markExcluded(excludedFrom, LocalDateTime.of(2026, 10, 14, 7, 0));
        return suspension;
    }

    @Test
    void 오늘_제외가_시작된_거래정지와_오늘_상장한_종목을_알린다() {
        Map<String, StockInfo> stocks = new HashMap<>();
        stocks.put("005930", stock("005930", "삼성전자", "0", LocalDate.of(2020, 1, 1)));
        stocks.put("111111", stock("111111", "정지종목", "0", LocalDate.of(2020, 1, 1)));
        stocks.put("222222", stock("222222", "신규종목", "0", TODAY));
        when(stockInfoCacheService.getCache()).thenReturn(stocks);
        when(suspensionRepository.findByExcludedFrom(TODAY)).thenReturn(List.of(excludedOn("111111", TODAY)));

        var response = service.getNotices(TODAY);

        assertThat(response.date()).isEqualTo(TODAY);
        assertThat(response.items())
                .extracting(MarketNoticeItem::type, MarketNoticeItem::stockCode, MarketNoticeItem::baseDate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MarketNoticeType.TRADING_SUSPENSION, "111111", TODAY),
                        org.assertj.core.groups.Tuple.tuple(MarketNoticeType.NEW_LISTING, "222222", TODAY));
    }

    @Test
    void 이미_재개된_거래정지는_알리지_않는다() {
        Map<String, StockInfo> stocks = new HashMap<>();
        stocks.put("111111", stock("111111", "정지종목", "0", LocalDate.of(2020, 1, 1)));
        when(stockInfoCacheService.getCache()).thenReturn(stocks);
        var resumed = excludedOn("111111", TODAY);
        resumed.end(TODAY, LocalDateTime.of(2026, 10, 14, 7, 0));
        when(suspensionRepository.findByExcludedFrom(TODAY)).thenReturn(List.of(resumed));

        assertThat(service.getNotices(TODAY).items()).isEmpty();
    }

    @Test
    void 주권이_아니거나_비활성인_종목은_알리지_않는다() {
        StockInfo etfSuspended = stock("333333", "ETF", "8", LocalDate.of(2020, 1, 1));
        StockInfo etfListed = stock("444444", "새ETF", "8", TODAY);
        StockInfo inactive = stock("555555", "비활성신규", "0", TODAY);
        inactive.markInactive();
        Map<String, StockInfo> stocks = new HashMap<>();
        stocks.put("333333", etfSuspended);
        stocks.put("444444", etfListed);
        stocks.put("555555", inactive);
        when(stockInfoCacheService.getCache()).thenReturn(stocks);
        when(suspensionRepository.findByExcludedFrom(TODAY)).thenReturn(List.of(excludedOn("333333", TODAY)));

        assertThat(service.getNotices(TODAY).items()).isEmpty();
    }

    @Test
    void 이름에_스팩이_들어간_신규_상장_종목은_알린다() {
        Map<String, StockInfo> stocks = new HashMap<>();
        stocks.put("666666", stock("666666", "한국제1호스팩", "7", TODAY));
        when(stockInfoCacheService.getCache()).thenReturn(stocks);
        when(suspensionRepository.findByExcludedFrom(TODAY)).thenReturn(List.of());

        assertThat(service.getNotices(TODAY).items())
                .extracting(MarketNoticeItem::stockCode)
                .containsExactly("666666");
    }

    @Test
    void 하루_신규_상장이_너무_많으면_종목이_한꺼번에_들어온_것으로_보고_알리지_않는다() {
        Map<String, StockInfo> stocks = new HashMap<>();
        for (int index = 0; index <= MarketNoticeService.MAX_NEW_LISTINGS_PER_DAY; index++) {
            String code = String.format("%06d", index);
            stocks.put(code, stock(code, "종목" + index, "0", TODAY));
        }
        when(stockInfoCacheService.getCache()).thenReturn(stocks);
        when(suspensionRepository.findByExcludedFrom(TODAY)).thenReturn(List.of());

        assertThat(service.getNotices(TODAY).items()).isEmpty();
    }

    @Test
    void 최근_3개월_신규_상장_수는_기간_밖과_한꺼번에_들어온_날을_뺀다() {
        Map<String, StockInfo> stocks = new HashMap<>();
        stocks.put("100001", stock("100001", "오늘상장", "0", TODAY));
        stocks.put("100002", stock("100002", "두달전", "0", TODAY.minusMonths(2)));
        stocks.put("100003", stock("100003", "석달하루전", "0", TODAY.minusMonths(3).minusDays(1)));
        stocks.put("100004", stock("100004", "ETF", "8", TODAY));
        LocalDate bulkDay = TODAY.minusMonths(1);
        for (int index = 0; index <= MarketNoticeService.MAX_NEW_LISTINGS_PER_DAY; index++) {
            String code = String.format("2%05d", index);
            stocks.put(code, stock(code, "한꺼번에" + index, "0", bulkDay));
        }
        when(stockInfoCacheService.getCache()).thenReturn(stocks);
        when(suspensionRepository.findByExcludedFrom(TODAY)).thenReturn(List.of());
        when(suspensionRepository.findByEndedOnIsNull()).thenReturn(List.of());

        assertThat(service.getNotices(TODAY).recentNewListingCount()).isEqualTo(2);
    }

    @Test
    void 현재_거래정지_수는_재개되지_않은_활성_주권만_센다() {
        Map<String, StockInfo> stocks = new HashMap<>();
        stocks.put("111111", stock("111111", "정지1", "0", LocalDate.of(2020, 1, 1)));
        stocks.put("222222", stock("222222", "정지2", "0", LocalDate.of(2020, 1, 1)));
        stocks.put("333333", stock("333333", "ETF", "8", LocalDate.of(2020, 1, 1)));
        when(stockInfoCacheService.getCache()).thenReturn(stocks);
        when(suspensionRepository.findByExcludedFrom(TODAY)).thenReturn(List.of());
        // 아직 5거래일이 안 돼 지도에서 빠지기 전인 정지도 센다.
        var notYetExcluded = StockTradingSuspension.start("222222", TODAY, LocalDateTime.of(2026, 10, 14, 7, 0));
        when(suspensionRepository.findByEndedOnIsNull())
                .thenReturn(List.of(excludedOn("111111", TODAY), notYetExcluded, excludedOn("333333", TODAY)));

        assertThat(service.getNotices(TODAY).currentSuspensionCount()).isEqualTo(2);
    }
}
