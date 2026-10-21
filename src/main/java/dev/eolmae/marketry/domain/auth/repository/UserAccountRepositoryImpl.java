package dev.eolmae.marketry.domain.auth.repository;

import static dev.eolmae.marketry.domain.auth.entity.QUserAccount.userAccount;
import static dev.eolmae.marketry.domain.auth.entity.QUserProfile.userProfile;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.OrderSpecifier.NullHandling;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserAccountRepositoryImpl implements UserAccountRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public AdminMemberPageResponse findMembers(
            String query, Role role, String sort, boolean ascending, int page, int size) {
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
                .orderBy(orderBy(sort, ascending))
                .offset((long) page * size)
                .limit(size)
                .fetch();
        return new AdminMemberPageResponse(items, total == null ? 0 : total, false);
    }

    @Override
    public AdminMemberSummaryResponse summarize(LocalDateTime joinedSince) {
        long admins = count(userAccount.role.eq(Role.ADMIN));
        long users = count(userAccount.role.eq(Role.USER));
        return new AdminMemberSummaryResponse(
                admins + users, admins, users, count(userAccount.createdAt.goe(joinedSince)));
    }

    private long count(Predicate condition) {
        Long count = queryFactory
                .select(userAccount.count())
                .from(userAccount)
                .where(condition)
                .fetchOne();
        return count == null ? 0 : count;
    }

    // 정렬 기준이 같은 값이 많아도 페이지가 겹치거나 빠지지 않도록 같은 방향의 회원 ID를 보조 기준으로 둔다.
    // 이메일·닉네임은 비어 있을 수 있어 방향과 상관없이 비어 있는 쪽을 뒤로 보낸다.
    private OrderSpecifier<?>[] orderBy(String sort, boolean ascending) {
        Order direction = ascending ? Order.ASC : Order.DESC;
        OrderSpecifier<?> primary =
                switch (sort) {
                    case "id" -> new OrderSpecifier<>(direction, userAccount.id);
                    case "email" -> new OrderSpecifier<>(direction, userAccount.email, NullHandling.NullsLast);
                    case "nickname" -> new OrderSpecifier<>(direction, userProfile.nickname, NullHandling.NullsLast);
                    case "role" -> new OrderSpecifier<>(direction, userAccount.role);
                    // 접속 기록은 아직 저장하지 않아 모든 회원이 같은 값(없음)이다 — 지금은 회원 ID 순과 같고, 기록이 생기면 그 열로 바꾼다.
                    case "lastAccessAt" -> new OrderSpecifier<>(direction, userAccount.id);
                    default -> new OrderSpecifier<>(direction, userAccount.createdAt);
                };
        return "id".equals(sort) || "lastAccessAt".equals(sort)
                ? new OrderSpecifier<?>[] {primary}
                : new OrderSpecifier<?>[] {primary, new OrderSpecifier<>(direction, userAccount.id)};
    }
}
