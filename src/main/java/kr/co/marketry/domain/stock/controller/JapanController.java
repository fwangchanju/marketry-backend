package kr.co.marketry.domain.stock.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.co.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import kr.co.marketry.domain.stock.service.JapanStockKoreanNameService;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService.Category;
import kr.co.marketry.domain.stock.service.OverseasIndexCloseService;
import kr.co.marketry.domain.stock.service.OverseasIndexCloseService.IndexClose;
import kr.co.marketry.domain.stock.service.OverseasIndexQuoteService;
import kr.co.marketry.domain.stock.service.OverseasIndexQuoteService.IndexQuote;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService.Classification;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService.QuoteSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 일본 화면의 조회 전용 API다. 관리자와 특별 회원만 본다. 수정은 /api/admin 아래의 관리자 API에만 둔다.
// 특별 회원 여부는 토큰에 없어서 회원 정보를 읽어 확인한다. 지정을 해제하면 바로 막힌다.
@RestController
@RequestMapping("/api/japan")
@RequiredArgsConstructor
public class JapanController {
    private static final String MARKET = "JAPAN";
    private static final String INDEX_MARKET = "NIKKEI225";

    private final OverseasStockQuoteService quoteService;
    private final OverseasStockClassificationService classificationService;
    private final OverseasClassificationCategoryService categoryService;
    private final JapanStockKoreanNameService nameService;
    private final OverseasIndexCloseService indexCloseService;
    private final OverseasIndexQuoteService indexQuoteService;

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

    @GetMapping("/index-quotes")
    public List<IndexQuote> findIndexQuotes(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return indexQuoteService.findQuotes(INDEX_MARKET, date);
    }

    @GetMapping("/classifications")
    public Map<String, Classification> findClassifications(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return classificationService.findAll(MARKET);
    }

    @GetMapping("/categories")
    public List<Category> findCategories(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return categoryService.findAll(MARKET);
    }

    @GetMapping("/stock-names")
    public Map<String, String> findStockNames(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return nameService.findAll();
    }
}
