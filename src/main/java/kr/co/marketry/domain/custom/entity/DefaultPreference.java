package kr.co.marketry.domain.custom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Zone;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "default_preference")
@Getter
public class DefaultPreference {
    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected DefaultPreference() {}

    public static DefaultPreference create() {
        DefaultPreference preference = new DefaultPreference();
        preference.id = 1L;
        return preference;
    }

    public void update(String payload) {
        this.payload = payload;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
