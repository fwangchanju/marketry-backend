package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategory;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import org.junit.jupiter.api.Test;

class OverseasClassificationCategoryServiceTest {
    private final OverseasClassificationCategoryRepository categories =
            mock(OverseasClassificationCategoryRepository.class);
    private final OverseasClassificationCategoryService service = new OverseasClassificationCategoryService(categories);

    @Test
    void 시장의_분류_목록을_id_상위_이름으로_돌려준다() {
        OverseasClassificationCategory category = mock(OverseasClassificationCategory.class);
        when(category.getId()).thenReturn(7L);
        when(category.getParentId()).thenReturn(3L);
        when(category.getName()).thenReturn("반도체");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(category));

        assertThat(service.findAll("JAPAN"))
                .containsExactly(new OverseasClassificationCategoryService.Category(7L, 3L, "반도체"));
    }

    @Test
    void 모르는_시장은_조회도_거부한다() {
        assertThatThrownBy(() -> service.findAll("TAIWAN")).isInstanceOf(BadRequestException.class);
    }
}
