package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.repository.OverseasIndexQuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 해외 지수의 장중 등락률. 그래프에서 오늘이나 최근 며칠의 지수 막대를 그릴 때 쓴다. */
@Service
@RequiredArgsConstructor
public class OverseasIndexQuoteService {
    private static final Set<String> INDEX_MARKETS = Set.of("NIKKEI225");

    private final OverseasIndexQuoteRepository repository;

    /** 그 날짜의 저장된 값을 시각 순서로. 값이 없는 날(휴장·보관 기간 밖)은 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<IndexQuote> findQuotes(String indexMarket, LocalDate date) {
        if (INDEX_MARKETS.contains(indexMarket) == false) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
        return repository
                .findByIdMarketAndIdSnapshotTimeGreaterThanEqualAndIdSnapshotTimeLessThanOrderByIdSnapshotTimeAsc(
                        indexMarket, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .map(quote -> new IndexQuote(quote.getId().getSnapshotTime(), quote.getChangeRate()))
                .toList();
    }

    public record IndexQuote(LocalDateTime snapshotTime, BigDecimal changeRate) {}
}
