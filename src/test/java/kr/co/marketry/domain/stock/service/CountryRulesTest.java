package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.collector.UsaConstituents;
import org.junit.jupiter.api.Test;

class CountryRulesTest {
    @Test
    void 시장마다_분류_단계_수가_다르다() {
        assertThat(CountryRules.JAPAN.levels()).isEqualTo(3);
        assertThat(CountryRules.USA.levels()).isEqualTo(2);
    }

    @Test
    void 일본_종목코드는_숫자로_시작하는_네_글자다() {
        assertThat(CountryRules.JAPAN.isValidStockCode("7203")).isTrue();
        assertThat(CountryRules.JAPAN.isValidStockCode("285A")).isTrue();
        assertThat(CountryRules.JAPAN.isValidStockCode("AAPL")).isFalse();
        assertThat(CountryRules.JAPAN.isValidStockCode(null)).isFalse();
    }

    @Test
    void 미국_종목코드는_영문_티커이고_클래스_문자를_허용한다() {
        assertThat(CountryRules.USA.isValidStockCode("AAPL")).isTrue();
        assertThat(CountryRules.USA.isValidStockCode("BRK-B")).isTrue();
        assertThat(CountryRules.USA.isValidStockCode("aapl")).isFalse();
        assertThat(CountryRules.USA.isValidStockCode("7203")).isFalse();
        assertThat(CountryRules.USA.isValidStockCode("TOOLONGX")).isFalse();
    }

    @Test
    void S_AND_P_500_구성_종목_티커가_모두_미국_규칙을_통과한다() {
        new UsaConstituents().all().forEach(constituent -> assertThat(
                        CountryRules.USA.isValidStockCode(constituent.symbol()))
                .as(constituent.symbol())
                .isTrue());
    }

    @Test
    void 모르는_시장은_거부한다() {
        assertThatThrownBy(() -> CountryRules.of("TAIWAN")).isInstanceOf(BadRequestException.class);
        assertThat(CountryRules.of("USA")).isEqualTo(CountryRules.USA);
    }
}
