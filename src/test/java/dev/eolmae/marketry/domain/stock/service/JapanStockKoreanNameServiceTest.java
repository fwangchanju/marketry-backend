package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.stock.entity.JapanStockKoreanName;
import dev.eolmae.marketry.domain.stock.repository.JapanStockKoreanNameRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JapanStockKoreanNameServiceTest {
    private final JapanStockKoreanNameRepository repository = mock(JapanStockKoreanNameRepository.class);
    private final JapanStockKoreanNameService service = new JapanStockKoreanNameService(repository);

    @Test
    void 관리자_공통_이름을_조회한다() {
        when(repository.findAll()).thenReturn(List.of(JapanStockKoreanName.create("285A", "키옥시아")));
        assertThat(service.findAll()).containsEntry("285A", "키옥시아");
    }

    @Test
    void 새_이름을_앞뒤_공백을_제거해_저장한다() {
        when(repository.findById("285A")).thenReturn(Optional.empty());
        service.update("285A", " 키옥시아 ");
        ArgumentCaptor<JapanStockKoreanName> entry = ArgumentCaptor.forClass(JapanStockKoreanName.class);
        verify(repository).save(entry.capture());
        assertThat(entry.getValue().getKoreanName()).isEqualTo("키옥시아");
    }

    @Test
    void 기존_이름을_변경한다() {
        JapanStockKoreanName entry = JapanStockKoreanName.create("7203", "토요타");
        when(repository.findById("7203")).thenReturn(Optional.of(entry));
        service.update("7203", "도요타");
        verify(repository).save(any(JapanStockKoreanName.class));
        assertThat(entry.getKoreanName()).isEqualTo("도요타");
    }

    @Test
    void 빈_이름과_잘못된_코드를_거부한다() {
        assertThatThrownBy(() -> service.update("7203", "  ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("bad", "도요타")).isInstanceOf(BadRequestException.class);
    }
}
