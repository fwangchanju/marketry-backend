package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.Test;

class OverseasStockQuoteServiceTest {
    private static final LocalDateTime CLOSE = LocalDateTime.of(2026, 11, 4, 15, 30);
    private final OverseasStockQuoteRepository repository = mock(OverseasStockQuoteRepository.class);
    private final OverseasStockQuoteService service = new OverseasStockQuoteService(repository);

    private OverseasStockQuote quote(LocalDateTime time) {
        return OverseasStockQuote.create(
                new OverseasStockQuoteId("JAPAN", "7203", time),
                new BigDecimal("3028.00"),
                new BigDecimal("3051.00"),
                new BigDecimal("-0.75"),
                new BigDecimal("44100000000000"));
    }

    @Test
    void 시각을_주지_않으면_가장_최근_시세를_돌려준다() {
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.of(quote(CLOSE)));
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", CLOSE)).thenReturn(List.of(quote(CLOSE)));
        var snapshot = service.findQuotes("JAPAN", null);
        assertThat(snapshot.snapshotTime()).isEqualTo(CLOSE);
        assertThat(snapshot.quotes()).hasSize(1);
        assertThat(snapshot.quotes().getFirst().changeRate()).isEqualByComparingTo("-0.75");
        assertThat(snapshot.quotes().getFirst().closeVerified()).isNull();
    }

    @Test
    void 시각을_주면_그_시각의_시세를_돌려준다() {
        LocalDateTime earlier = CLOSE.minusDays(1);
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", earlier)).thenReturn(List.of(quote(earlier)));
        assertThat(service.findQuotes("JAPAN", earlier).snapshotTime()).isEqualTo(earlier);
    }

    @Test
    void 야후_종가_검증_여부를_시세_응답에_포함한다() {
        var verified = quote(CLOSE);
        verified.markCloseVerified();
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", CLOSE)).thenReturn(List.of(verified));

        var result = service.findQuotes("JAPAN", CLOSE);

        assertThat(result.quotes().getFirst().closeVerified()).isTrue();
    }

    @Test
    void 시세가_없으면_빈_결과를_돌려준다() {
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.empty());
        var snapshot = service.findQuotes("JAPAN", null);
        assertThat(snapshot.snapshotTime()).isNull();
        assertThat(snapshot.quotes()).isEmpty();
    }

    @Test
    void 지원하지_않는_시장은_거부한다() {
        assertThatThrownBy(() -> service.findQuotes("TAIWAN", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.findSnapshotTimes("TAIWAN")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 직전_거래일은_주말과_공식_휴장일을_건너뛴다() {
        assertThat(service.findPreviousTradingDay("JAPAN", java.time.LocalDate.parse("2026-11-09")))
                .isEqualTo(java.time.LocalDate.parse("2026-11-06"));
        assertThat(service.findPreviousTradingDay("JAPAN", java.time.LocalDate.parse("2026-11-04")))
                .isEqualTo(java.time.LocalDate.parse("2026-11-02"));
    }

    @Test
    void 미국은_저장된_날짜_중_바로_앞_날짜를_직전_거래일로_돌려준다() {
        when(repository.findSnapshotTimes("USA"))
                .thenReturn(List.of(
                        LocalDateTime.of(2026, 11, 9, 16, 0),
                        LocalDateTime.of(2026, 11, 11, 16, 0),
                        LocalDateTime.of(2026, 11, 12, 16, 0)));

        assertThat(service.findPreviousTradingDay("USA", LocalDate.of(2026, 11, 12)))
                .isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(service.findPreviousTradingDay("USA", LocalDate.of(2026, 11, 11)))
                .isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void 미국은_앞선_저장_날짜가_없으면_직전_거래일이_없다() {
        when(repository.findSnapshotTimes("USA")).thenReturn(List.of(LocalDateTime.of(2026, 11, 9, 16, 0)));

        assertThat(service.findPreviousTradingDay("USA", LocalDate.of(2026, 11, 9)))
                .isNull();
    }

    @Test
    void 미국_시세도_조회하고_알_수_없는_시장은_거절한다() {
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("USA")).thenReturn(Optional.empty());

        assertThat(service.findQuotes("USA", null).quotes()).isEmpty();
        assertThatThrownBy(() -> service.findQuotes("KOREA", null)).isInstanceOf(BadRequestException.class);
    }
}
