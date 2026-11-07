package kr.co.marketry.domain.stock.repository;

import static kr.co.marketry.domain.stock.entity.QMarketCalendar.marketCalendar;
import static kr.co.marketry.domain.stock.entity.QStockDailyPrice.stockDailyPrice;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.common.enums.Country;
import kr.co.marketry.domain.stock.enums.ExchangeType;
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
