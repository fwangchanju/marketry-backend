package dev.eolmae.marketry.domain.auth.repository;

import static dev.eolmae.marketry.domain.auth.entity.QVisitorDaily.visitorDaily;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.domain.auth.dto.AdminDailyAccessResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class VisitorDailyRepositoryImpl implements VisitorDailyRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void recordVisit(LocalDate visitDate, String visitorKey) {
        jdbcTemplate.update("""
                INSERT INTO visitor_daily (visit_date, visitor_key)
                VALUES (?, ?)
                ON CONFLICT (visit_date, visitor_key) DO NOTHING
                """, visitDate, visitorKey);
    }

    @Override
    public List<AdminDailyAccessResponse> countVisitorsByDate(LocalDate from, LocalDate to) {
        return queryFactory
                .select(Projections.constructor(
                        AdminDailyAccessResponse.class, visitorDaily.id.visitDate, visitorDaily.id.visitorKey.count()))
                .from(visitorDaily)
                .where(visitorDaily.id.visitDate.between(from, to))
                .groupBy(visitorDaily.id.visitDate)
                .orderBy(visitorDaily.id.visitDate.asc())
                .fetch();
    }
}
