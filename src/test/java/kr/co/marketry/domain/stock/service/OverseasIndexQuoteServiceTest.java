package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuote;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasIndexQuoteRepository;
import org.junit.jupiter.api.Test;

class OverseasIndexQuoteServiceTest {
    private final OverseasIndexQuoteRepository repository = mock(OverseasIndexQuoteRepository.class);
    private final OverseasIndexQuoteService service = new OverseasIndexQuoteService(repository);

    @Test
    void 그_날짜_하루치를_시각과_등락률로_돌려준다() {
        LocalDate date = LocalDate.of(2026, 11, 13);
        LocalDateTime time = date.atTime(9, 5);
        when(repository
                        .findByIdMarketAndIdSnapshotTimeGreaterThanEqualAndIdSnapshotTimeLessThanOrderByIdSnapshotTimeAsc(
                                "NIKKEI225",
                                date.atStartOfDay(),
                                date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(OverseasIndexQuote.create(
                        new OverseasIndexQuoteId("NIKKEI225", time),
                        new BigDecimal("61142.35"),
                        new BigDecimal("61077.90"),
                        new BigDecimal("0.11"))));

        assertThat(service.findQuotes("NIKKEI225", date))
                .containsExactly(new OverseasIndexQuoteService.IndexQuote(time, new BigDecimal("0.11")));
    }

    @Test
    void 값이_없는_날은_빈_목록이다() {
        assertThat(service.findQuotes("NIKKEI225", LocalDate.of(2026, 11, 14))).isEmpty();
    }

    @Test
    void 지원하지_않는_지수는_조회하지_않고_막는다() {
        assertThatThrownBy(() -> service.findQuotes("SP500", LocalDate.of(2026, 11, 13)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }
}
