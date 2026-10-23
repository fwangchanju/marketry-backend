package dev.eolmae.marketry.domain.custom.repository;

import static dev.eolmae.marketry.domain.custom.entity.QCustomSector.customSector;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CustomSectorRepositoryImpl implements CustomSectorRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public LocalDateTime findLatestEditedAtByUserId(Long userId) {
        return queryFactory
                .select(customSector.updatedAt.max())
                .from(customSector)
                .where(customSector.userId.eq(userId), customSector.updatedAt.gt(customSector.createdAt))
                .fetchOne();
    }
}
