package dev.eolmae.marketry.domain.stock.repository;

import static dev.eolmae.marketry.domain.stock.entity.QStockInfo.stockInfo;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.domain.stock.dto.MissingDailyPriceStock;
import dev.eolmae.marketry.domain.stock.entity.QStockDailyPrice;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.enums.StockMarketCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StockDailyPriceRepositoryImpl implements StockDailyPriceRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<MissingDailyPriceStock> findMissing(LocalDate date, List<String> stockCodes) {
        if (stockCodes.isEmpty()) {
            return List.of();
        }
        var krx = new QStockDailyPrice("dailyPriceKrx");
        var nxt = new QStockDailyPrice("dailyPriceNxt");
        var nxtMissing = stockInfo.nxtEnabled.isTrue().and(nxt.id.isNull());
        return queryFactory
                .select(Projections.constructor(
                        MissingDailyPriceStock.class,
                        stockInfo.stockCode,
                        stockInfo.stockName,
                        krx.id.isNull(),
                        nxtMissing))
                .from(stockInfo)
                .leftJoin(krx)
                .on(
                        krx.stockCode.eq(stockInfo.stockCode),
                        krx.exchangeType.eq(ExchangeType.KRX),
                        krx.tradeDate.eq(date))
                .leftJoin(nxt)
                .on(
                        nxt.stockCode.eq(stockInfo.stockCode),
                        nxt.exchangeType.eq(ExchangeType.NXT),
                        nxt.tradeDate.eq(date))
                .where(
                        stockInfo.stockCode.in(stockCodes),
                        stockInfo.active.isTrue(),
                        stockInfo.marketCode.in(StockMarketCode.KOSPI.code(), StockMarketCode.KOSDAQ.code()),
                        krx.id.isNull().or(nxtMissing))
                .orderBy(stockInfo.stockCode.asc())
                .setHint("jakarta.persistence.query.timeout", 5000)
                .fetch();
    }
}
