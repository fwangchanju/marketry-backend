package kr.co.marketry.domain.stock.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.co.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import kr.co.marketry.domain.stock.collector.UsaConstituents;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService.Category;
import kr.co.marketry.domain.stock.service.OverseasClassificationPublicationService;
import kr.co.marketry.domain.stock.service.OverseasIndexCloseService;
import kr.co.marketry.domain.stock.service.OverseasIndexCloseService.IndexClose;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService.Classification;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService.QuoteSnapshot;
import kr.co.marketry.domain.stock.service.UsaGicsClassificationService;
import kr.co.marketry.domain.stock.service.UsaStockKoreanNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 미국 화면의 조회 API다. 관리자와 특별 회원만 본다. 수정은 /api/admin 아래 API가 맡는다.
@RestController
@RequestMapping("/api/usa")
@RequiredArgsConstructor
public class UsaController {
    private static final String MARKET = "USA";
    private static final String INDEX_MARKET = "SP500";

    private final OverseasStockQuoteService quoteService;
    private final OverseasStockClassificationService classificationService;
    private final OverseasClassificationCategoryService categoryService;
    private final OverseasClassificationPublicationService publications;
    private final UsaGicsClassificationService gics;
    private final UsaConstituents constituents;
    private final OverseasIndexCloseService indexCloseService;
    private final UsaStockKoreanNameService nameService;

    @GetMapping("/quotes")
    public QuoteSnapshot findQuotes(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime snapshotTime) {
        return quoteService.findQuotes(MARKET, snapshotTime);
    }

    @GetMapping("/quotes/snapshots")
    public List<LocalDateTime> findSnapshotTimes(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return quoteService.findSnapshotTimes(MARKET);
    }

    @GetMapping("/quotes/previous-trading-day")
    public LocalDate findPreviousTradingDay(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return quoteService.findPreviousTradingDay(MARKET, date);
    }

    @GetMapping("/index-closes")
    public List<IndexClose> findIndexCloses(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return indexCloseService.findCloses(INDEX_MARKET, from, to);
    }

    @GetMapping("/classifications")
    public Map<String, Classification> findClassifications(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return classificationService.findAll(MARKET);
    }

    // 설정창 맨 아래 "업데이트" 시각 — MARKETRY 분류를 마지막으로 업데이트하거나 롤백한 시각(없으면 null). 모든 사용자에게 보인다.
    @GetMapping("/classification-published-at")
    public PublishedAt findClassificationPublishedAt() {
        return new PublishedAt(publications.findPublishedAt(MARKET).orElse(null));
    }

    public record PublishedAt(LocalDateTime publishedAt) {}

    // 미국 S&P 500 종목의 GICS 분류(읽기 전용). 운영 분류와 따로 두고, 가운데 칸 GICS 모드가 쓴다.
    @GetMapping("/gics-classifications")
    public Map<String, Classification> findGicsClassifications() {
        return gics.findAll();
    }

    @GetMapping("/categories")
    public List<Category> findCategories(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return categoryService.findAll(MARKET);
    }

    @GetMapping("/stocks")
    public List<StockInfo> findStocks(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return constituents.all().stream()
                .map(constituent -> new StockInfo(constituent.symbol(), constituent.name()))
                .toList();
    }

    public record StockInfo(String symbol, String name) {}

    @GetMapping("/stock-names")
    public Map<String, String> findStockNames(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return nameService.findAll();
    }
}
