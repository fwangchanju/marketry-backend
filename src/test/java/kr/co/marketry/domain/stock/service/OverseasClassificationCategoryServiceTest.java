package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategory;
import kr.co.marketry.domain.stock.entity.OverseasStockClassification;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationId;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockClassificationRepository;
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
    void 사단계_생성과_중복_분류를_거부한다() {
        var sector = category(1, null, "기술");
        var industry = category(2, 1L, "반도체");
        var sub = category(3, 2L, "메모리");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, industry, sub));
        assertThatThrownBy(() -> service.create("JAPAN", 3L, "제조")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create("JAPAN", 1L, "반도체")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 소분류를_지정하면_세_단계를_함께_저장한다() {
        var sector = category(1, null, "금융");
        var middle = category(2, 1L, "보험");
        var sub = category(3, 2L, "생명 보험");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, middle, sub));
        var id = new OverseasStockClassificationId("JAPAN", "8750");
        var stock = OverseasStockClassification.create(id);
        when(stocks.findById(id)).thenReturn(Optional.of(stock));
        service.assign("JAPAN", List.of("8750"), 3);
        assertThat(stock.getSectorName()).isEqualTo("금융");
        assertThat(stock.getIndustryName()).isEqualTo("보험");
        assertThat(stock.getSubIndustryName()).isEqualTo("생명 보험");
    }

    @Test
    void 중분류를_다시_지정하면_소분류를_비운다() {
        var sector = category(1, null, "금융");
        var middle = category(2, 1L, "보험");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, middle));
        var id = new OverseasStockClassificationId("JAPAN", "8750");
        var stock = OverseasStockClassification.create(id);
        stock.updateSector("금융");
        stock.updateIndustry("보험");
        stock.updateSubIndustry("생명 보험");
        when(stocks.findById(id)).thenReturn(Optional.of(stock));
        service.assign("JAPAN", List.of("8750"), 2);
        assertThat(stock.getIndustryName()).isEqualTo("보험");
        assertThat(stock.getSubIndustryName()).isNull();
    }

    @Test
    void 중분류_이름을_바꾸면_소분류는_유지한다() {
        var sector = category(1, null, "금융");
        var middle = category(2, 1L, "보험");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, middle));
        var stock = OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", "8750"));
        stock.updateSector("금융");
        stock.updateIndustry("보험");
        stock.updateSubIndustry("생명 보험");
        var other = OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", "8306"));
        other.updateSector("금융");
        other.updateIndustry("은행");
        when(stocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(stock, other));
        service.rename("JAPAN", 2, "보험사");
        assertThat(stock.getIndustryName()).isEqualTo("보험사");
        assertThat(stock.getSubIndustryName()).isEqualTo("생명 보험");
        assertThat(other.getIndustryName()).isEqualTo("은행");
    }

    @Test
    void 소분류를_다른_중분류로_옮기면_종목의_경로도_바뀐다() {
        var sector = category(1, null, "금융");
        var oldMiddle = category(2, 1L, "보험");
        var sub = category(3, 2L, "생명 보험");
        var otherSector = category(4, null, "기타");
        var newMiddle = category(5, 4L, "복합");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, oldMiddle, sub, otherSector, newMiddle));
        var stock = OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", "8750"));
        stock.updateSector("금융");
        stock.updateIndustry("보험");
        stock.updateSubIndustry("생명 보험");
        when(stocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(stock));
        service.move("JAPAN", 3, 5L);
        assertThat(stock.getSectorName()).isEqualTo("기타");
        assertThat(stock.getIndustryName()).isEqualTo("복합");
        assertThat(stock.getSubIndustryName()).isEqualTo("생명 보험");
        verify(sub).reparent(5L);
    }

    @Test
    void 옮긴_뒤_세_단계를_넘으면_거부한다() {
        var first = category(1, null, "금융");
        var middle = category(2, 1L, "보험");
        var sub = category(3, 2L, "생명 보험");
        var other = category(4, null, "기타");
        var otherMiddle = category(5, 4L, "복합");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(first, middle, sub, other, otherMiddle));
        assertThatThrownBy(() -> service.move("JAPAN", 2, 5L)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.move("JAPAN", 1, 3L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 소분류를_삭제하면_해당_종목만_미분류로_옮긴다() {
        var sector = category(1, null, "금융");
        var middle = category(2, 1L, "보험");
        var sub = category(3, 2L, "생명 보험");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, middle, sub));
        var life = OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", "8750"));
        life.updateSector("금융");
        life.updateIndustry("보험");
        life.updateSubIndustry("생명 보험");
        var insurance = OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", "8766"));
        insurance.updateSector("금융");
        insurance.updateIndustry("보험");
        when(stocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(life, insurance));
        service.delete("JAPAN", 3, 1);
        assertThat(life.getSectorName()).isEqualTo("미분류");
        assertThat(life.getIndustryName()).isNull();
        assertThat(life.getSubIndustryName()).isNull();
        assertThat(insurance.getIndustryName()).isEqualTo("보험");
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
    void 다른_상위_분류나_다른_단계에_있는_이름도_쓸_수_없다() {
        var retail = category(1, null, "경기 소비재");
        var retailMiddle = category(2, 1L, "소매");
        var staples = category(3, null, "필수 소비재");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(retail, retailMiddle, staples));
        assertThatThrownBy(() -> service.create("JAPAN", 3L, "소매")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create("JAPAN", 1L, "필수 소비재")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.rename("JAPAN", 3, "소매")).isInstanceOf(BadRequestException.class);
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

    @Test
    void 미국_분류는_두_단계까지만_만들_수_있다() {
        var technology = category(1, null, "Technology");
        var software = category(2, 1L, "Software");
        when(categories.findAllByMarket("USA")).thenReturn(List.of(technology, software));
        when(categories.save(any(OverseasClassificationCategory.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.findAll("USA")).hasSize(2);
        assertThat(service.create("USA", 1L, "Hardware").name()).isEqualTo("Hardware");
        assertThatThrownBy(() -> service.create("USA", 2L, "Cloud")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 일본은_세_단계까지_만들_수_있다() {
        var sector = category(1, null, "기술");
        var industry = category(2, 1L, "반도체");
        when(categories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, industry));
        when(categories.save(any(OverseasClassificationCategory.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.create("JAPAN", 2L, "장비").name()).isEqualTo("장비");
    }

    @Test
    void 미국_종목_배정은_영문_티커를_받고_일본_코드는_거부한다() {
        var sector = category(1, null, "Technology");
        when(categories.findAllByMarket("USA")).thenReturn(List.of(sector));
        var id = new OverseasStockClassificationId("USA", "BRK-B");
        var stock = OverseasStockClassification.create(id);
        when(stocks.findById(id)).thenReturn(Optional.of(stock));

        service.assign("USA", List.of("BRK-B"), 1);

        assertThat(stock.getSectorName()).isEqualTo("Technology");
        assertThatThrownBy(() -> service.assign("USA", List.of("7203"), 1)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.assign("USA", List.of("aapl"), 1)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.assign("JAPAN", List.of("AAPL"), 1)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 모르는_시장은_조회도_거부한다() {
        assertThatThrownBy(() -> service.findAll("TAIWAN")).isInstanceOf(BadRequestException.class);
    }
}
