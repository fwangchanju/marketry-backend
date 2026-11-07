package kr.co.marketry.domain.custom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CustomStockSectorId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;
}
