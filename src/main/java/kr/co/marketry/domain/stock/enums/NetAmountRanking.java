package kr.co.marketry.domain.stock.enums;

import java.math.BigDecimal;
import java.util.Comparator;

public interface NetAmountRanking extends TradeType {

    /** SELL이면 오름차순(가장 큰 매도부터), BUY면 내림차순(가장 큰 매수부터) */
    default Comparator<BigDecimal> valueComparator() {
        return isSell() ? Comparator.naturalOrder() : Comparator.reverseOrder();
    }

    /** SELL이면 절댓값으로 변환해 화면에 양수로 표시 */
    default BigDecimal normalize(BigDecimal value) {
        return isSell() ? value.abs() : value;
    }
}
