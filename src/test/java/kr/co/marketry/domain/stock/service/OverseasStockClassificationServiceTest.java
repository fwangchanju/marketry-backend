package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.entity.OverseasStockClassification;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationId;
import kr.co.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import org.junit.jupiter.api.Test;

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
    void 미국_분류도_조회할_수_있고_지원하지_않는_시장은_거부한다() {
        when(repository.findAllByIdMarket("USA")).thenReturn(List.of());

        assertThat(service.findAll("USA")).isEmpty();
        assertThatThrownBy(() -> service.findAll("TAIWAN")).isInstanceOf(BadRequestException.class);
    }
}
