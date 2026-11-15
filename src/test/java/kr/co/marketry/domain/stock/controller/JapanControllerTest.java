package kr.co.marketry.domain.stock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import kr.co.marketry.domain.stock.service.JapanStockKoreanNameService;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService;
import kr.co.marketry.domain.stock.service.OverseasIndexCloseService;
import kr.co.marketry.domain.stock.service.OverseasIndexQuoteService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService;
import org.junit.jupiter.api.Test;

class JapanControllerTest {
    private final JapanStockKoreanNameService names = mock(JapanStockKoreanNameService.class);
    private final OverseasIndexCloseService indexCloses = mock(OverseasIndexCloseService.class);
    private final OverseasIndexQuoteService indexQuotes = mock(OverseasIndexQuoteService.class);
    private final JapanController controller = new JapanController(
            mock(OverseasStockQuoteService.class),
            mock(OverseasStockClassificationService.class),
            mock(OverseasClassificationCategoryService.class),
            names,
            indexCloses,
            indexQuotes);

    @Test
    void 일반_회원도_한글_이름을_조회한다() {
        when(names.findAll()).thenReturn(Map.of("7203", "도요타"));

        assertThat(controller.findStockNames(new AuthenticatedUserPrincipal(1L, Role.USER)))
                .containsEntry("7203", "도요타");
    }

    @Test
    void 관리자도_한글_이름을_조회한다() {
        when(names.findAll()).thenReturn(Map.of());

        assertThat(controller.findStockNames(new AuthenticatedUserPrincipal(2L, Role.ADMIN)))
                .isEmpty();
    }

    @Test
    void 읽기전용_캡처_토큰도_일본_지도_데이터를_조회한다() {
        when(names.findAll()).thenReturn(Map.of("7203", "도요타"));

        assertThat(controller.findStockNames(new AuthenticatedUserPrincipal(3L, Role.USER, true)))
                .containsEntry("7203", "도요타");
    }

    @Test
    void 일반_회원도_닛케이_지수_종가를_조회한다() {
        var closes =
                List.of(new OverseasIndexCloseService.IndexClose(LocalDate.of(2026, 11, 6), new BigDecimal("50000")));
        when(indexCloses.findCloses("NIKKEI225", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 6)))
                .thenReturn(closes);

        assertThat(controller.findIndexCloses(
                        new AuthenticatedUserPrincipal(1L, Role.USER),
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 6)))
                .isEqualTo(closes);
    }

    @Test
    void 일반_회원도_닛케이_장중_지수를_날짜로_조회한다() {
        var quotes = List.of(
                new OverseasIndexQuoteService.IndexQuote(LocalDateTime.of(2026, 11, 13, 9, 5), new BigDecimal("0.11")));
        when(indexQuotes.findQuotes("NIKKEI225", LocalDate.of(2026, 11, 13))).thenReturn(quotes);

        assertThat(controller.findIndexQuotes(
                        new AuthenticatedUserPrincipal(1L, Role.USER), LocalDate.of(2026, 11, 13)))
                .isEqualTo(quotes);
    }
}
