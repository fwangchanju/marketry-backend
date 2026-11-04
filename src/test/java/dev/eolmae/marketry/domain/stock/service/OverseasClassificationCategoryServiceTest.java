package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.stock.entity.OverseasClassificationCategory;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassification;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassificationId;
import dev.eolmae.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import dev.eolmae.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OverseasClassificationCategoryServiceTest {
    private final OverseasClassificationCategoryRepository categories =
            mock(OverseasClassificationCategoryRepository.class);
    private final OverseasStockClassificationRepository stocks = mock(OverseasStockClassificationRepository.class);
    private final OverseasClassificationCategoryService service =
            new OverseasClassificationCategoryService(categories, stocks);

    private OverseasClassificationCategory category(long id, Long parentId, String name) {
        OverseasClassificationCategory category = mock(OverseasClassificationCategory.class);
        when(category.getId()).thenReturn(id);
        when(category.getParentId()).thenReturn(parentId);
        when(category.getName()).thenReturn(name);
        return category;
    }

    @Test
    void 업종_일괄_지정은_부모_섹터와_업종을_함께_저장한다() {
        var sector = category(1, null, "기술");
        var industry = category(2, 1L, "반도체");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, industry));
        var id = new OverseasStockClassificationId("JAPAN", "7203");
        var stock = OverseasStockClassification.create(id);
        stock.updateSector("산업재");
        stock.updateIndustry("기계");
        when(stocks.findById(id)).thenReturn(Optional.of(stock));
        service.assign("JAPAN", List.of("7203"), 2);
        assertThat(stock.getSectorName()).isEqualTo("기술");
        assertThat(stock.getIndustryName()).isEqualTo("반도체");
        verify(stocks).save(stock);
    }

    @Test
    void 다른_섹터를_지정하면_기존_업종은_해제한다() {
        var sector = category(1, null, "기술");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector));
        var id = new OverseasStockClassificationId("JAPAN", "7203");
        var stock = OverseasStockClassification.create(id);
        stock.updateSector("산업재");
        stock.updateIndustry("기계");
        when(stocks.findById(id)).thenReturn(Optional.of(stock));
        service.assign("JAPAN", List.of("7203"), 1);
        assertThat(stock.getSectorName()).isEqualTo("기술");
        assertThat(stock.getIndustryName()).isNull();
    }

    @Test
    void 삼단계_생성과_중복_분류를_거부한다() {
        var sector = category(1, null, "기술");
        var industry = category(2, 1L, "반도체");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, industry));
        assertThatThrownBy(() -> service.create("JAPAN", 2L, "제조")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create("JAPAN", 1L, "반도체")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 다른_시장의_분류는_지정할_수_없다() {
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of());
        assertThatThrownBy(() -> service.assign("JAPAN", List.of("7203"), 100)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 업종을_옮기면_해당_종목의_섹터도_함께_바뀐다() {
        var oldSector = category(1, null, "기술");
        var industry = category(2, 1L, "반도체");
        var newSector = category(3, null, "산업재");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(oldSector, industry, newSector));
        var stock = OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", "7203"));
        stock.updateSector("기술");
        stock.updateIndustry("반도체");
        when(stocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(stock));
        service.move("JAPAN", 2, 3L);
        assertThat(stock.getSectorName()).isEqualTo("산업재");
        assertThat(stock.getIndustryName()).isEqualTo("반도체");
        verify(industry).reparent(3L);
    }

    @Test
    void 삭제할_종목_수가_달라지면_삭제하지_않는다() {
        var sector = category(1, null, "기술");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector));
        var stock = OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", "7203"));
        stock.updateSector("기술");
        when(stocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(stock));
        assertThatThrownBy(() -> service.delete("JAPAN", 1, 0)).isInstanceOf(BadRequestException.class);
        assertThat(stock.getSectorName()).isEqualTo("기술");
        org.mockito.Mockito.verify(categories, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void 국내_전용_보호_이름은_쓸_수_없다() {
        assertThatThrownBy(() -> service.create("JAPAN", null, "신규 상장")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.rename("JAPAN", 1, " 거래 정지 ")).isInstanceOf(BadRequestException.class);
        verify(categories, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void 미분류에는_하위_업종을_만들_수_없다() {
        var unclassified = category(1, null, "미분류");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(unclassified));
        assertThatThrownBy(() -> service.create("JAPAN", 1L, "기타")).isInstanceOf(BadRequestException.class);
    }
}
