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
import dev.eolmae.marketry.domain.auth.dto.AdminDailyAccessResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import dev.eolmae.marketry.domain.auth.entity.UserAccount;
import dev.eolmae.marketry.domain.auth.enums.MemberGrade;
import dev.eolmae.marketry.domain.auth.enums.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserAccountRepositoryImpl implements UserAccountRepositoryCustom {
    private static final String NO_PROVIDER_FILTER = "NONE";
    private static final Set<String> OAUTH_ISSUERS =
            Set.of("https://nid.naver.com", "https://kauth.kakao.com", "https://accounts.google.com");
    private final JPAQueryFactory queryFactory;

    @Override
    public AdminMemberPageResponse findMembers(
            String query, Set<MemberGrade> grades, String sort, boolean ascending, int page, int size) {
        return findMembers(query, grades, Set.of(), sort, ascending, page, size);
    }

    @Override
    public AdminMemberPageResponse findMembers(
            String query,
            Set<MemberGrade> grades,
            Set<String> issuers,
            String sort,
            boolean ascending,
            int page,
            int size) {
        var predicate = new BooleanBuilder(managedMember());
        if (!query.isEmpty()) {
            predicate.and(
                    userAccount.email.containsIgnoreCase(query).or(userProfile.nickname.containsIgnoreCase(query)));
        }
        if (!grades.isEmpty()) {
            var gradePredicate = new BooleanBuilder();
            grades.forEach(grade -> gradePredicate.or(gradeCondition(grade)));
            predicate.and(gradePredicate);
        }
        if (!issuers.isEmpty()) {
            var issuerPredicate = new BooleanBuilder();
            if (issuers.contains(NO_PROVIDER_FILTER)) {
                issuerPredicate.or(userAccount.issuer.isNull().or(userAccount.issuer.notIn(OAUTH_ISSUERS)));
            }
            issuers.stream()
                    .filter(issuer -> !NO_PROVIDER_FILTER.equals(issuer))
                    .forEach(issuer -> issuerPredicate.or(userAccount.issuer.eq(issuer)));
            predicate.and(issuerPredicate);
        }
        Long total = queryFactory
                .select(userAccount.count())
                .from(userAccount)
                .leftJoin(userProfile)
                .on(userProfile.userId.eq(userAccount.id))
                .where(predicate)
                .fetchOne();
        var items = queryFactory
                .select(Projections.constructor(
                        AdminMemberResponse.class,
                        userAccount.id,
                        userAccount.issuer,
                        userAccount.email,
                        userProfile.nickname,
                        userAccount.role,
                        userAccount.special,
                        userAccount.createdAt,
                        userAccount.lastAccessAt,
                        // 접속한 날 수는 서비스가 일별 접속 기록에서 채운다.
                        Expressions.nullExpression(Long.class)))
                .from(userAccount)
                .leftJoin(userProfile)
                .on(userProfile.userId.eq(userAccount.id))
                .where(predicate)
                .orderBy(orderBy(sort, ascending))
                .offset((long) page * size)
                .limit(size)
                .fetch();
        return new AdminMemberPageResponse(items, total == null ? 0 : total, true);
    }

    @Override
    public AdminMemberSummaryResponse summarize(LocalDateTime joinedSince, LocalDateTime accessedSince) {
        long admins = count(gradeCondition(MemberGrade.ADMIN));
        long specials = count(gradeCondition(MemberGrade.SPECIAL));
        long users = count(gradeCondition(MemberGrade.USER));
        return new AdminMemberSummaryResponse(
                admins + specials + users,
                admins,
                specials,
                users,
                count(userAccount.createdAt.goe(joinedSince)),
                count(userAccount.lastAccessAt.goe(accessedSince)));
    }

    @Override
    public List<AdminDailyAccessResponse> countJoinedByDate(LocalDate from, LocalDate to) {
        // 가입 시각을 가져와 날짜별로 센다 — DB의 날짜 변환(cast as date)은 java.sql.Date를 돌려줘 LocalDate로 받지 못한다.
        // 기간 안의 가입자만 가져오므로(회원 수만큼) 양이 작다.
        List<LocalDateTime> joinedAt = queryFactory
                .select(userAccount.createdAt)
                .from(userAccount)
                .where(
                        managedMember(),
                        userAccount.createdAt.goe(from.atStartOfDay()),
                        userAccount.createdAt.lt(to.plusDays(1).atStartOfDay()))
                .fetch();
        return joinedAt.stream()
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, TreeMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> new AdminDailyAccessResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public long countJoinedBefore(LocalDateTime before) {
        return count(userAccount.createdAt.lt(before));
    }

    @Override
    public long countMembers() {
        return count(userAccount.id.isNotNull());
    }

    // 관리자는 권한으로, 특별 회원은 일반 권한(USER) 중 특별 표시가 켜진 회원으로 본다.
    private Predicate gradeCondition(MemberGrade grade) {
        return switch (grade) {
            case ADMIN -> userAccount.role.eq(Role.ADMIN);
            case SPECIAL -> userAccount.role.eq(Role.USER).and(userAccount.special.isTrue());
            case USER -> userAccount.role.eq(Role.USER).and(userAccount.special.isFalse());
        };
    }

    // 회원 관리(목록·집계·내보내기)에는 실제 가입 회원만 센다 — 로컬 테스트 회원과 시스템 회원은 따로 관리한다.
    // 연동 값으로 거르므로 나중에 시스템 회원이 더 생겨도(예: 거래소 공개본 계정) 자동으로 빠진다.
    private Predicate managedMember() {
        return userAccount
                .issuer
                .isNull()
                .or(userAccount.issuer.notIn(UserAccount.LOCAL_TEST_ISSUER, UserAccount.SYSTEM_ISSUER));
    }

    private long count(Predicate condition) {
        Long count = queryFactory
                .select(userAccount.count())
                .from(userAccount)
                .where(managedMember(), condition)
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
                    case "issuer" -> new OrderSpecifier<>(direction, userAccount.issuer, NullHandling.NullsLast);
                    case "email" -> new OrderSpecifier<>(direction, userAccount.email, NullHandling.NullsLast);
                    case "nickname" -> new OrderSpecifier<>(direction, userProfile.nickname, NullHandling.NullsLast);
                    case "role" -> new OrderSpecifier<>(direction, userAccount.role);
                    // 접속 기록이 없는 회원(기록이 생기기 전 가입자)은 방향과 상관없이 뒤로 보낸다.
                    case "lastAccessAt" ->
                        new OrderSpecifier<>(direction, userAccount.lastAccessAt, NullHandling.NullsLast);
                    default -> new OrderSpecifier<>(direction, userAccount.createdAt);
                };
        if ("role".equals(sort)) {
            // 구분 순서는 관리자 → 특별 회원 → 일반 회원이고, 내림차순이면 그 반대다.
            Order specialDirection = ascending ? Order.DESC : Order.ASC;
            return new OrderSpecifier<?>[] {
                primary,
                new OrderSpecifier<>(specialDirection, userAccount.special),
                new OrderSpecifier<>(direction, userAccount.id)
            };
        }
        return "id".equals(sort)
                ? new OrderSpecifier<?>[] {primary}
                : new OrderSpecifier<?>[] {primary, new OrderSpecifier<>(direction, userAccount.id)};
    }
}
