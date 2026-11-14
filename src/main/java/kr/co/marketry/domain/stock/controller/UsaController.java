package kr.co.marketry.domain.stock.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.co.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import kr.co.marketry.domain.auth.service.SpecialMemberGuard;
import kr.co.marketry.domain.stock.collector.UsaConstituents;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService.Category;
import kr.co.marketry.domain.stock.service.OverseasIndexCloseService;
import kr.co.marketry.domain.stock.service.OverseasIndexCloseService.IndexClose;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService.Classification;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService.QuoteSnapshot;
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

    private final SpecialMemberGuard guard;
    private final OverseasStockQuoteService quoteService;
    private final OverseasStockClassificationService classificationService;
    private final OverseasClassificationCategoryService categoryService;
    private final UsaConstituents constituents;
    private final OverseasIndexCloseService indexCloseService;
    private final UsaStockKoreanNameService nameService;

    @GetMapping("/quotes")
    public QuoteSnapshot findQuotes(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime snapshotTime) {
        guard.require(principal);
        return quoteService.findQuotes(MARKET, snapshotTime);
    }

    @GetMapping("/quotes/snapshots")
    public List<LocalDateTime> findSnapshotTimes(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        guard.require(principal);
        return quoteService.findSnapshotTimes(MARKET);
    }

    @GetMapping("/quotes/previous-trading-day")
    public LocalDate findPreviousTradingDay(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        guard.require(principal);
        return quoteService.findPreviousTradingDay(MARKET, date);
    }

    @GetMapping("/index-closes")
    public List<IndexClose> findIndexCloses(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        guard.require(principal);
        return indexCloseService.findCloses(INDEX_MARKET, from, to);
    }

    @GetMapping("/classifications")
    public Map<String, Classification> findClassifications(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        guard.require(principal);
        return classificationService.findAll(MARKET);
    }

    @GetMapping("/categories")
    public List<Category> findCategories(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        guard.require(principal);
        return categoryService.findAll(MARKET);
    }

    @GetMapping("/stocks")
    public List<StockInfo> findStocks(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        guard.require(principal);
        return constituents.all().stream()
                .map(constituent -> new StockInfo(constituent.symbol(), constituent.name()))
                .toList();
    }

    public record StockInfo(String symbol, String name) {}

    @GetMapping("/stock-names")
    public Map<String, String> findStockNames(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        guard.require(principal);
        return nameService.findAll();
    }
}
