package dev.eolmae.marketry.domain.stock.repository;

import static dev.eolmae.marketry.domain.stock.entity.QMarketCalendar.marketCalendar;
import static dev.eolmae.marketry.domain.stock.entity.QStockDailyPrice.stockDailyPrice;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StockDailyPriceRepositoryImpl implements StockDailyPriceRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<LocalDate> findCalendarDatesWithPrice(ExchangeType exchangeType, LocalDate from, LocalDate to) {
        return queryFactory
                .select(marketCalendar.date)
                .from(marketCalendar)
                .where(
                        marketCalendar.country.eq(Country.KR),
                        marketCalendar.date.between(from, to),
                        JPAExpressions.selectOne()
                                .from(stockDailyPrice)
                                .where(
                                        stockDailyPrice.exchangeType.eq(exchangeType),
                                        stockDailyPrice.tradeDate.eq(marketCalendar.date))
                                .exists())
                .fetch();
    }
}
