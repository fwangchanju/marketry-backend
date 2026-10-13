package dev.eolmae.marketry.domain.view.service;

import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.entity.StockTradingSuspension;
import dev.eolmae.marketry.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketry.domain.stock.repository.StockTradingSuspensionRepository;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketry.domain.view.dto.MarketNoticeItem;
import dev.eolmae.marketry.domain.view.dto.MarketNoticeResponse;
import dev.eolmae.marketry.domain.view.enums.MarketNoticeType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈의 안내 틀에 띄울, 그날 새로 생긴 특수한 종목을 모은다. 새로 생긴 일만 그날 하루 알리므로 날짜가 지나면 사라진다.
 * 안내 종류: 거래정지가 연속 5거래일에 이르러 지도와 평균에서 빠지기 시작한 종목, 그날 새로 상장한 종목.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketNoticeService {

    // 하루에 새로 상장하는 종목이 이보다 많으면 새 환경의 첫 종목 동기화처럼 종목이 한꺼번에 들어온 것으로 보고 알리지 않는다.
    static final int MAX_NEW_LISTINGS_PER_DAY = 100;

    private final StockTradingSuspensionRepository suspensionRepository;
    private final StockInfoCacheService stockInfoCacheService;

    public MarketNoticeResponse getNotices(LocalDate date) {
        Map<String, StockInfo> stocks = stockInfoCacheService.getCache();
        List<MarketNoticeItem> items = new ArrayList<>();
        items.addAll(suspensionNotices(date, stocks));
        items.addAll(newListingNotices(date, stocks));
        return new MarketNoticeResponse(date, items);
    }

    private List<MarketNoticeItem> suspensionNotices(LocalDate date, Map<String, StockInfo> stocks) {
        return suspensionRepository.findByExcludedFrom(date).stream()
                .filter(StockTradingSuspension::isOpen)
                .map(suspension -> stocks.get(suspension.getStockCode()))
                .filter(stock -> stock != null && stock.isActiveAndOrdinary())
                .map(stock -> toItem(MarketNoticeType.TRADING_SUSPENSION, stock, date))
                .sorted(NOTICE_ORDER)
                .toList();
    }

    private List<MarketNoticeItem> newListingNotices(LocalDate date, Map<String, StockInfo> stocks) {
        List<MarketNoticeItem> listings = stocks.values().stream()
                .filter(StockInfo::isActive)
                .filter(stock -> stock.getCreatedAt().toLocalDate().equals(date))
                .filter(stock -> StockMarketCode.isNewListingTarget(stock.getMarketCode(), stock.getStockName()))
                .map(stock -> toItem(MarketNoticeType.NEW_LISTING, stock, date))
                .sorted(NOTICE_ORDER)
                .toList();
        if (listings.size() > MAX_NEW_LISTINGS_PER_DAY) {
            log.warn("하루 신규 상장 종목이 너무 많아 안내하지 않는다: date={}, count={}", date, listings.size());
            return List.of();
        }
        return listings;
    }

    private static MarketNoticeItem toItem(MarketNoticeType type, StockInfo stock, LocalDate baseDate) {
        return new MarketNoticeItem(type, stock.getStockCode(), stock.getStockName(), stock.getMarketType(), baseDate);
    }

    private static final Comparator<MarketNoticeItem> NOTICE_ORDER =
            Comparator.comparing(MarketNoticeItem::stockName).thenComparing(MarketNoticeItem::stockCode);
}
