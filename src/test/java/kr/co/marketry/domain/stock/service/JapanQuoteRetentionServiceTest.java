package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuote;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasIndexQuoteRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class JapanQuoteRetentionServiceTest {
    @Test
    void 최근_거래일은_남기고_앞_날짜만_고른다() {
        var dates = List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5));

        assertThat(JapanQuoteRetentionService.cleanupDates(dates, 2)).containsExactly(LocalDate.of(2026, 11, 2));
        assertThat(JapanQuoteRetentionService.cleanupDates(dates, 10)).isEmpty();
    }

    @Test
    void 일본은_휴장일을_건너뛰고_시세가_있는_최근_5일을_보존한다() {
        var dates = List.of(
                LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6));

        assertThat(JapanQuoteRetentionService.cleanupDates(dates, 5)).containsExactly(LocalDate.of(2026, 10, 29));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 오래된_날은_마지막_시세만_남기고_한_건뿐인_날은_건드리지_않는다() {
        var repository = mock(OverseasStockQuoteRepository.class);
        var transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any(TransactionCallback.class)))
                .thenAnswer(call -> ((TransactionCallback<Integer>) call.getArgument(0)).doInTransaction(null));
        LocalDate old = LocalDate.of(2026, 11, 2);
        LocalDate closeOnly = LocalDate.of(2026, 11, 3);
        LocalDate recent = LocalDate.of(2026, 11, 4);
        when(repository.findSnapshotTimes("JAPAN"))
                .thenReturn(List.of(
                        old.atTime(9, 5),
                        old.atTime(15, 30),
                        closeOnly.atTime(15, 30),
                        recent.atTime(9, 5),
                        recent.atTime(15, 30)));

        new JapanQuoteRetentionService(repository, mock(OverseasIndexQuoteRepository.class), transactions).cleanup(1);

        verify(repository)
                .deleteDayExceptLast(
                        eq("JAPAN"),
                        eq(old.atStartOfDay()),
                        eq(old.plusDays(1).atStartOfDay()),
                        eq(old.atTime(15, 30)));
        verify(repository, never())
                .deleteDayExceptLast(eq("JAPAN"), eq(closeOnly.atStartOfDay()), any(LocalDateTime.class), any());
        verify(repository, never())
                .deleteDayExceptLast(eq("JAPAN"), eq(recent.atStartOfDay()), any(LocalDateTime.class), any());
    }

    @Test
    void 하루치_지수_값은_마지막_하나만_남기고_지울_값을_고른다() {
        var first = indexQuote(LocalDateTime.of(2026, 11, 2, 9, 5));
        var middle = indexQuote(LocalDateTime.of(2026, 11, 2, 12, 0));
        var last = indexQuote(LocalDateTime.of(2026, 11, 2, 15, 30));

        assertThat(JapanQuoteRetentionService.indexQuotesToDelete(List.of(first, middle, last)))
                .containsExactly(first, middle);
        assertThat(JapanQuoteRetentionService.indexQuotesToDelete(List.of(last)))
                .isEmpty();
        assertThat(JapanQuoteRetentionService.indexQuotesToDelete(List.of())).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 오래된_날의_지수_값도_마지막_값만_남기고_최근_날은_건드리지_않는다() {
        var repository = mock(OverseasStockQuoteRepository.class);
        var indexRepository = mock(OverseasIndexQuoteRepository.class);
        var transactions = mock(TransactionTemplate.class);
        doAnswer(call -> {
                    ((Consumer<TransactionStatus>) call.getArgument(0)).accept(null);
                    return null;
                })
                .when(transactions)
                .executeWithoutResult(any());
        LocalDate old = LocalDate.of(2026, 11, 2);
        LocalDate recent = LocalDate.of(2026, 11, 4);
        when(repository.findSnapshotTimes("JAPAN"))
                .thenReturn(List.of(old.atTime(9, 5), old.atTime(15, 30), recent.atTime(9, 5), recent.atTime(15, 30)));
        var oldFirst = indexQuote(old.atTime(9, 5));
        var oldLast = indexQuote(old.atTime(15, 30));
        when(indexRepository
                        .findByIdMarketAndIdSnapshotTimeGreaterThanEqualAndIdSnapshotTimeLessThanOrderByIdSnapshotTimeAsc(
                                "NIKKEI225", old.atStartOfDay(), old.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(oldFirst, oldLast));

        new JapanQuoteRetentionService(repository, indexRepository, transactions).cleanup(1);

        verify(indexRepository).deleteAll(List.of(oldFirst));
        verify(indexRepository, never())
                .findByIdMarketAndIdSnapshotTimeGreaterThanEqualAndIdSnapshotTimeLessThanOrderByIdSnapshotTimeAsc(
                        eq("NIKKEI225"), eq(recent.atStartOfDay()), any(LocalDateTime.class));
    }

    private static OverseasIndexQuote indexQuote(LocalDateTime time) {
        return OverseasIndexQuote.create(
                new OverseasIndexQuoteId("NIKKEI225", time),
                new BigDecimal("61000.00"),
                new BigDecimal("60000.00"),
                new BigDecimal("1.67"));
    }
}
