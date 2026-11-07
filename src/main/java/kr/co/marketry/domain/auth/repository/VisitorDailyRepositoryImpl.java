package kr.co.marketry.domain.auth.repository;

import static kr.co.marketry.domain.auth.entity.QVisitorDaily.visitorDaily;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.domain.auth.dto.AdminDailyAccessResponse;
import kr.co.marketry.domain.auth.dto.AdminVisitorSourceResponse;
import kr.co.marketry.domain.auth.enums.VisitorSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class VisitorDailyRepositoryImpl implements VisitorDailyRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void recordVisit(LocalDate visitDate, String visitorKey, VisitorSource source) {
        jdbcTemplate.update("""
                INSERT INTO visitor_daily (visit_date, visitor_key, source)
                VALUES (?, ?, ?)
                ON CONFLICT (visit_date, visitor_key) DO NOTHING
                """, visitDate, visitorKey, source.name());
    }

    @Override
    public List<AdminVisitorSourceResponse> countVisitorsBySource(LocalDate from, LocalDate to) {
        return queryFactory
                .select(Projections.constructor(
                        AdminVisitorSourceResponse.class, visitorDaily.source, visitorDaily.id.visitorKey.count()))
                .from(visitorDaily)
                .where(visitorDaily.id.visitDate.between(from, to), visitorDaily.source.isNotNull())
                .groupBy(visitorDaily.source)
                .fetch();
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
