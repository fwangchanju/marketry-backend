package dev.eolmae.marketry.domain.custom.repository;

import static dev.eolmae.marketry.domain.custom.entity.QCustomSector.customSector;
import static dev.eolmae.marketry.domain.custom.entity.QCustomStockSector.customStockSector;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.enums.ProtectedSector;
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
                .join(customSector)
                .on(customSector.id.eq(customStockSector.sectorId))
                .where(customStockSector.id.userId.eq(userId), isNotAutoAssignedNewListing(), isNotInSuspendedSector())
                .fetchOne();
    }

    // 평일 07:00 종목 정보 동기화가 새 종목을 모든 사용자의 "신규 상장" 업종에 자동으로 배정한다(생성 시각 = 수정 시각).
    // 사용자가 직접 바꾼 것이 아니므로 분류 업데이트 시각에서 뺀다. 사용자가 그 종목을 다른 업종으로 옮기면 수정 시각이 달라져 센다.
    private BooleanExpression isNotAutoAssignedNewListing() {
        return customSector
                .parentId
                .isNotNull()
                .or(customSector.name.ne(CustomSector.NEW_LISTING_NAME))
                .or(customStockSector.updatedAt.ne(customStockSector.createdAt));
    }

    // 거래 정지 업종에는 서버만 종목을 넣는다(사용자는 직접 못 넣는다). 그 업종의 행은 모두 자동 배정이라 분류 업데이트 시각에서 뺀다.
    private BooleanExpression isNotInSuspendedSector() {
        return customSector.parentId.isNotNull().or(customSector.name.ne(ProtectedSector.SUSPENDED.getSectorName()));
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        queryFactory
                .delete(customStockSector)
                .where(customStockSector.id.userId.eq(userId))
                .execute();
    }
}
