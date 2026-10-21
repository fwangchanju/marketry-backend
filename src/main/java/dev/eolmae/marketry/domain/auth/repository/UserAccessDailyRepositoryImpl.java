package dev.eolmae.marketry.domain.auth.repository;

import static dev.eolmae.marketry.domain.auth.entity.QUserAccessDaily.userAccessDaily;
import static dev.eolmae.marketry.domain.auth.entity.QUserAccount.userAccount;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.domain.auth.dto.AdminDailyAccessResponse;
import dev.eolmae.marketry.domain.auth.entity.UserAccount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
}
