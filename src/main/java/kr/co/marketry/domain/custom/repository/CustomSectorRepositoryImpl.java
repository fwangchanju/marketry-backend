package kr.co.marketry.domain.custom.repository;

import static kr.co.marketry.domain.custom.entity.QCustomSector.customSector;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CustomSectorRepositoryImpl implements CustomSectorRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public LocalDateTime findLatestEditedAt(Long userId, ClassificationMarket market) {
        return queryFactory
                .select(customSector.updatedAt.max())
                .from(customSector)
                .where(
                        customSector.userId.eq(userId),
                        customSector.market.eq(market),
                        customSector.updatedAt.gt(customSector.createdAt))
                .fetchOne();
    }
}
