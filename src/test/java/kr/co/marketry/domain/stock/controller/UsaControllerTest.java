package kr.co.marketry.domain.stock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import kr.co.marketry.domain.auth.service.SpecialMemberGuard;
import kr.co.marketry.domain.stock.collector.UsaConstituents;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService;
import kr.co.marketry.domain.stock.service.OverseasIndexCloseService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService.QuoteSnapshot;
import kr.co.marketry.domain.stock.service.UsaStockKoreanNameService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UsaControllerTest {
    private final AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(1L, Role.USER);
    private final SpecialMemberGuard guard = mock(SpecialMemberGuard.class);
    private final OverseasStockQuoteService quotes = mock(OverseasStockQuoteService.class);
    private final OverseasStockClassificationService classifications = mock(OverseasStockClassificationService.class);
    private final OverseasClassificationCategoryService categories = mock(OverseasClassificationCategoryService.class);
    private final OverseasIndexCloseService indexCloses = mock(OverseasIndexCloseService.class);
    private final UsaStockKoreanNameService names = mock(UsaStockKoreanNameService.class);
    private final UsaConstituents constituents = new UsaConstituents(new ByteArrayInputStream(
            "symbol,name,date_added\nAAPL,Apple Inc.,1982-11-30\nXYZ,\"Block, Inc.\",2025-07-23\n"
                    .getBytes(StandardCharsets.UTF_8)));
    private final UsaController controller =
            new UsaController(guard, quotes, classifications, categories, constituents, indexCloses, names);

    @Test
    void 특별_회원이_아니면_어떤_조회도_하지_못한다() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(guard).require(principal);

        assertThatThrownBy(() -> controller.findQuotes(principal, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.findSnapshotTimes(principal)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.findPreviousTradingDay(principal, LocalDate.of(2026, 11, 12)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.findClassifications(principal)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.findCategories(principal)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.findStocks(principal)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.findStockNames(principal)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                        controller.findIndexCloses(principal, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(quotes, classifications, categories, indexCloses, names);
    }

    @Test
    void 한글_이름은_특별_회원에게_서비스_값을_그대로_준다() {
        when(names.findAll()).thenReturn(java.util.Map.of("AAPL", "애플"));

        assertThat(controller.findStockNames(principal)).containsEntry("AAPL", "애플");
        verify(guard).require(principal);
    }

    @Test
    void 시세는_미국_시장으로_조회한다() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 12, 16, 0);
        QuoteSnapshot snapshot = new QuoteSnapshot(time, List.of());
        when(quotes.findQuotes("USA", time)).thenReturn(snapshot);

        assertThat(controller.findQuotes(principal, time)).isEqualTo(snapshot);
        verify(guard).require(principal);
    }

    @Test
    void 종목_목록은_티커와_영문_이름을_돌려준다() {
        assertThat(controller.findStocks(principal))
                .containsExactly(
                        new UsaController.StockInfo("AAPL", "Apple Inc."),
                        new UsaController.StockInfo("XYZ", "Block, Inc."));
    }

    @Test
    void 직전_거래일은_미국_시장으로_조회한다() {
        when(quotes.findPreviousTradingDay("USA", LocalDate.of(2026, 11, 12))).thenReturn(LocalDate.of(2026, 11, 11));

        assertThat(controller.findPreviousTradingDay(principal, LocalDate.of(2026, 11, 12)))
                .isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void 지수_종가는_에스앤피500으로_조회한다() {
        var closes = List.of(
                new OverseasIndexCloseService.IndexClose(LocalDate.of(2026, 11, 12), new java.math.BigDecimal("7000")));
        when(indexCloses.findCloses("SP500", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12)))
                .thenReturn(closes);

        assertThat(controller.findIndexCloses(principal, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12)))
                .isEqualTo(closes);
    }
}
