package dev.eolmae.marketry.domain.auth.repository;

import static dev.eolmae.marketry.domain.auth.entity.QUserAccount.userAccount;
import static dev.eolmae.marketry.domain.auth.entity.QUserProfile.userProfile;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserAccountRepositoryImpl implements UserAccountRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public AdminMemberPageResponse findMembers(String query, Role role, int page, int size) {
        var predicate = new BooleanBuilder();
        if (!query.isEmpty()) {
            predicate.and(
                    userAccount.email.containsIgnoreCase(query).or(userProfile.nickname.containsIgnoreCase(query)));
        }
        if (role != null) {
            predicate.and(userAccount.role.eq(role));
        }
        Long total = queryFactory
                .select(userAccount.count())
                .from(userAccount)
                .leftJoin(userProfile)
                .on(userProfile.userId.eq(userAccount.id))
                .where(predicate)
                .fetchOne();
        // 접속 기록은 스키마 선행 배포 뒤 별도 단계에서 추가한다. 프로필 수정 시각으로 대체하지 않는다.
        var items = queryFactory
                .select(Projections.constructor(
                        AdminMemberResponse.class,
                        userAccount.id,
                        userAccount.email,
                        userProfile.nickname,
                        userAccount.role,
                        userAccount.createdAt,
                        Expressions.nullExpression(LocalDateTime.class)))
                .from(userAccount)
                .leftJoin(userProfile)
                .on(userProfile.userId.eq(userAccount.id))
                .where(predicate)
                .orderBy(userAccount.createdAt.desc(), userAccount.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();
        return new AdminMemberPageResponse(items, total == null ? 0 : total, false);
    }
}
