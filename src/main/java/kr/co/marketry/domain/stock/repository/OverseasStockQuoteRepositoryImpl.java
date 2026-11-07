package kr.co.marketry.domain.stock.repository;

import static kr.co.marketry.domain.stock.entity.QOverseasStockQuote.overseasStockQuote;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OverseasStockQuoteRepositoryImpl implements OverseasStockQuoteRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<LocalDateTime> findSnapshotTimes(String market) {
        return queryFactory
                .select(overseasStockQuote.id.snapshotTime)
                .distinct()
                .from(overseasStockQuote)
                .where(overseasStockQuote.id.market.eq(market))
                .orderBy(overseasStockQuote.id.snapshotTime.asc())
                .fetch();
    }
}
