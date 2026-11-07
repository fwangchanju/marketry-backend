package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import kr.co.marketry.common.enums.Country;
import kr.co.marketry.domain.stock.enums.MarketCalendarStatus;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "market_calendar", uniqueConstraints = @UniqueConstraint(columnNames = {"country", "date"}))
public class MarketCalendar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Country country;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketCalendarStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private IntegratedSessions integrated;

    protected MarketCalendar() {}

    public static MarketCalendar create(
            Country country, LocalDate date, MarketCalendarStatus status, IntegratedSessions integrated) {
        MarketCalendar calendar = new MarketCalendar();
        calendar.country = country;
        calendar.date = date;
        calendar.update(status, integrated);
        return calendar;
    }

    public void update(MarketCalendarStatus status, IntegratedSessions integrated) {
        this.status = status;
        this.integrated = integrated;
    }
}
