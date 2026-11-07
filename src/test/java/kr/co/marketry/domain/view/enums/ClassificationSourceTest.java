package kr.co.marketry.domain.view.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.co.marketry.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class ClassificationSourceTest {

    @Test
    void from_세_값을_대소문자_구분없이_해석한다() {
        assertThat(ClassificationSource.from("krx")).isEqualTo(ClassificationSource.KRX);
        assertThat(ClassificationSource.from("marketry")).isEqualTo(ClassificationSource.MARKETRY);
        assertThat(ClassificationSource.from("MINE")).isEqualTo(ClassificationSource.MINE);
        assertThat(ClassificationSource.from("Mine")).isEqualTo(ClassificationSource.MINE);
    }

    @Test
    void from_이름을_바꾸기_전_값_mymap도_mine으로_받는다() {
        assertThat(ClassificationSource.from("mymap")).isEqualTo(ClassificationSource.MINE);
    }

    @Test
    void from_모르는_값은_거부한다() {
        assertThatThrownBy(() -> ClassificationSource.from("other")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ClassificationSource.from("")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolve_source가_없으면_옛_isCustom을_따른다() {
        assertThat(ClassificationSource.resolve(null, true)).isEqualTo(ClassificationSource.MINE);
        assertThat(ClassificationSource.resolve(null, false)).isEqualTo(ClassificationSource.KRX);
    }

    @Test
    void resolve_source가_있으면_isCustom은_무시한다() {
        assertThat(ClassificationSource.resolve("marketry", true)).isEqualTo(ClassificationSource.MARKETRY);
        assertThat(ClassificationSource.resolve("marketry", false)).isEqualTo(ClassificationSource.MARKETRY);
        assertThat(ClassificationSource.resolve("krx", true)).isEqualTo(ClassificationSource.KRX);
    }
}
