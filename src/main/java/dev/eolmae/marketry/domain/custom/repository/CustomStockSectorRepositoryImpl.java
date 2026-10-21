package dev.eolmae.marketry.domain.custom.repository;

import static dev.eolmae.marketry.domain.custom.entity.QCustomStockSector.customStockSector;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CustomStockSectorRepositoryImpl implements CustomStockSectorRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public void deleteByIdUserIdAndIdStockCode(Long userId, String stockCode) {
        queryFactory
                .delete(customStockSector)
                .where(customStockSector.id.userId.eq(userId), customStockSector.id.stockCode.eq(stockCode))
                .execute();
    }

    @Override
    public void deleteByUserIdAndSectorIdIn(Long userId, List<Long> sectorIds) {
        queryFactory
                .delete(customStockSector)
                .where(customStockSector.id.userId.eq(userId), customStockSector.sectorId.in(sectorIds))
                .execute();
    }

    @Override
    public void deleteAllByIdUserId(Long userId) {
        queryFactory
                .delete(customStockSector)
                .where(customStockSector.id.userId.eq(userId))
                .execute();
    }

    @Override
    public LocalDateTime findLatestUpdatedAtByUserId(Long userId) {
        return queryFactory
                .select(customStockSector.updatedAt.max())
                .from(customStockSector)
                .where(customStockSector.id.userId.eq(userId))
                .fetchOne();
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        queryFactory
                .delete(customStockSector)
                .where(customStockSector.id.userId.eq(userId))
                .execute();
    }
}
