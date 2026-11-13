package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 해외 지수의 날짜별 종가. 기간 그래프에서 지수 막대를 그릴 때 쓴다. */
@Service
@RequiredArgsConstructor
public class OverseasIndexCloseService {
    private static final Set<String> INDEX_MARKETS = Set.of("NIKKEI225", "SP500");

    private final MarketIndexDailyRepository repository;

    @Transactional(readOnly = true)
    public List<IndexClose> findCloses(String indexMarket, LocalDate from, LocalDate to) {
        if (INDEX_MARKETS.contains(indexMarket) == false) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
        return repository.findByIdMarketAndIdTradeDateBetweenOrderByIdTradeDateAsc(indexMarket, from, to).stream()
                .map(index -> new IndexClose(index.getId().getTradeDate(), index.getCloseValue()))
                .toList();
    }

    public record IndexClose(LocalDate date, BigDecimal close) {}
}
