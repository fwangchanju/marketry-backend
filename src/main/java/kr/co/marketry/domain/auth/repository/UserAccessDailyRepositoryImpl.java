package kr.co.marketry.domain.auth.repository;

import static kr.co.marketry.domain.auth.entity.QUserAccessDaily.userAccessDaily;
import static kr.co.marketry.domain.auth.entity.QUserAccount.userAccount;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.marketry.domain.auth.dto.AdminDailyAccessResponse;
import kr.co.marketry.domain.auth.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class UserAccessDailyRepositoryImpl implements UserAccessDailyRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void recordAccess(Long userId, LocalDateTime accessedAt) {
        jdbcTemplate.update("""
                INSERT INTO user_access_daily (user_id, access_date, first_access_at, last_access_at, access_count)
                VALUES (?, ?, ?, ?, 1)
                ON CONFLICT (user_id, access_date)
                DO UPDATE SET last_access_at = EXCLUDED.last_access_at,
                              access_count = user_access_daily.access_count + 1
                """, userId, accessedAt.toLocalDate(), accessedAt, accessedAt);
    }

    @Override
    public List<AdminDailyAccessResponse> countMembersByDate(LocalDate from, LocalDate to) {
        return queryFactory
                .select(Projections.constructor(
                        AdminDailyAccessResponse.class,
                        userAccessDaily.id.accessDate,
                        userAccessDaily.id.userId.countDistinct()))
                .from(userAccessDaily)
                .join(userAccount)
                .on(userAccount.id.eq(userAccessDaily.id.userId))
                .where(
                        userAccessDaily.id.accessDate.between(from, to),
                        userAccount
                                .issuer
                                .isNull()
                                .or(userAccount.issuer.notIn(UserAccount.LOCAL_TEST_ISSUER, UserAccount.SYSTEM_ISSUER)))
                .groupBy(userAccessDaily.id.accessDate)
                .orderBy(userAccessDaily.id.accessDate.asc())
                .fetch();
    }

    @Override
    public Map<Long, Long> countAccessDaysByUser(Collection<Long> userIds, LocalDate from, LocalDate to) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> daysByUser = new HashMap<>();
        for (Tuple row : queryFactory
                .select(userAccessDaily.id.userId, userAccessDaily.id.accessDate.count())
                .from(userAccessDaily)
                .where(userAccessDaily.id.userId.in(userIds), userAccessDaily.id.accessDate.between(from, to))
                .groupBy(userAccessDaily.id.userId)
                .fetch()) {
            daysByUser.put(row.get(userAccessDaily.id.userId), row.get(userAccessDaily.id.accessDate.count()));
        }
        return daysByUser;
    }

    @Override
    public List<Long> countAccessDaysPerMember(LocalDate from, LocalDate to) {
        return queryFactory
                .select(userAccessDaily.id.accessDate.count())
                .from(userAccessDaily)
                .join(userAccount)
                .on(userAccount.id.eq(userAccessDaily.id.userId))
                .where(
                        userAccessDaily.id.accessDate.between(from, to),
                        userAccount
                                .issuer
                                .isNull()
                                .or(userAccount.issuer.notIn(UserAccount.LOCAL_TEST_ISSUER, UserAccount.SYSTEM_ISSUER)))
                .groupBy(userAccessDaily.id.userId)
                .fetch();
    }
}
