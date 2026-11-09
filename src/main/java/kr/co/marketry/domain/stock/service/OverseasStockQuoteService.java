package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OverseasStockQuoteService {
    private final OverseasStockQuoteRepository repository;

    @Transactional(readOnly = true)
    public List<LocalDateTime> findSnapshotTimes(String market) {
        validateMarket(market);
        return repository.findSnapshotTimes(market);
    }

    // 시각을 주지 않으면 가장 최근 시세를 돌려준다. 시세가 하나도 없으면 시각은 null이고 목록은 비어 있다.
    @Transactional(readOnly = true)
    public QuoteSnapshot findQuotes(String market, LocalDateTime requestedTime) {
        validateMarket(market);
        LocalDateTime snapshotTime = requestedTime;
        if (snapshotTime == null) {
            snapshotTime = repository
                    .findFirstByIdMarketOrderByIdSnapshotTimeDesc(market)
                    .map(quote -> quote.getId().getSnapshotTime())
                    .orElse(null);
        }
        if (snapshotTime == null) return new QuoteSnapshot(null, List.of());
        List<Quote> quotes = repository.findAllByIdMarketAndIdSnapshotTime(market, snapshotTime).stream()
                .map(Quote::from)
                .toList();
        return new QuoteSnapshot(snapshotTime, quotes);
    }

    @Transactional(readOnly = true)
    public LocalDate findPreviousTradingDay(String market, LocalDate date) {
        validateMarket(market);
        return kr.co.marketry.domain.stock.collector.JapanMarketReference.previousTradingDay(date);
    }

    private void validateMarket(String market) {
        if ("JAPAN".equals(market)) return;
        throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }

    public record QuoteSnapshot(LocalDateTime snapshotTime, List<Quote> quotes) {}

    public record Quote(
            String stockCode,
            BigDecimal currentPrice,
            BigDecimal lastPrice,
            BigDecimal changeRate,
            BigDecimal totalMarketValue,
            Boolean closeVerified) {
        private static Quote from(OverseasStockQuote quote) {
            return new Quote(
                    quote.getId().getStockCode(),
                    quote.getCurrentPrice(),
                    quote.getLastPrice(),
                    quote.getChangeRate(),
                    quote.getTotalMarketValue(),
                    quote.getCloseVerified());
        }
    }
}
