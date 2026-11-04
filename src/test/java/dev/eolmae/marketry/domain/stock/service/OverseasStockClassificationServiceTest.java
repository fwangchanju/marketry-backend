package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassification;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassificationId;
import dev.eolmae.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OverseasStockClassificationServiceTest {
    private final OverseasStockClassificationRepository repository = mock(OverseasStockClassificationRepository.class);
    private final OverseasStockClassificationService service = new OverseasStockClassificationService(repository);

    @Test
    void 시장을_기준으로_분류를_조회한다() {
        OverseasStockClassification item =
                OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", "285A"));
        item.updateSector("반도체");
        when(repository.findAllByIdMarket("JAPAN")).thenReturn(List.of(item));
        assertThat(service.findAll("JAPAN").get("285A").sector()).isEqualTo("반도체");
    }

    @Test
    void 섹터를_입력하면_공백을_제거해_저장한다() {
        service.update("JAPAN", "285A", "sector", " 반도체 ");
        ArgumentCaptor<OverseasStockClassification> entry = ArgumentCaptor.forClass(OverseasStockClassification.class);
        verify(repository).save(entry.capture());
        assertThat(entry.getValue().getId().getMarket()).isEqualTo("JAPAN");
        assertThat(entry.getValue().getSectorName()).isEqualTo("반도체");
    }

    @Test
    void 업종을_지워도_기존_섹터는_유지한다() {
        OverseasStockClassificationId id = new OverseasStockClassificationId("JAPAN", "7203");
        OverseasStockClassification item = OverseasStockClassification.create(id);
        item.updateSector("자동차");
        item.updateIndustry("완성차");
        when(repository.findById(id)).thenReturn(Optional.of(item));
        service.update("JAPAN", "7203", "industry", " ");
        verify(repository).save(any(OverseasStockClassification.class));
        assertThat(item.getSectorName()).isEqualTo("자동차");
        assertThat(item.getIndustryName()).isNull();
    }

    @Test
    void 지원하지_않는_시장과_입력_범위를_거부한다() {
        assertThatThrownBy(() -> service.update("TAIWAN", "7203", "sector", "자동차"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("JAPAN", "invalid", "sector", "자동차"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("JAPAN", "7203", "other", "자동차"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("JAPAN", "7203", "sector", "1234567890123"))
                .isInstanceOf(BadRequestException.class);
    }
}
