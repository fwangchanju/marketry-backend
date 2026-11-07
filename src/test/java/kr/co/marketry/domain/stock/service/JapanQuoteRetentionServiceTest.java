package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.Test;
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

        new JapanQuoteRetentionService(repository, transactions).cleanup(1);

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
}
