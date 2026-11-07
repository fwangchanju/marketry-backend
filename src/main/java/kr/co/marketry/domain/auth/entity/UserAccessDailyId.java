package kr.co.marketry.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class UserAccessDailyId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "access_date", nullable = false)
    private LocalDate accessDate;
}
