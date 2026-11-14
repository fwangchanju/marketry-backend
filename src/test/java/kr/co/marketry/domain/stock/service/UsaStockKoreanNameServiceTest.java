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
import kr.co.marketry.domain.stock.entity.UsaStockKoreanName;
import kr.co.marketry.domain.stock.repository.UsaStockKoreanNameRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsaStockKoreanNameServiceTest {
    private final UsaStockKoreanNameRepository repository = mock(UsaStockKoreanNameRepository.class);
    private final UsaStockKoreanNameService service = new UsaStockKoreanNameService(repository);

    @Test
    void 저장된_한글_이름을_티커별로_조회한다() {
        when(repository.findAll()).thenReturn(List.of(UsaStockKoreanName.create("AAPL", "애플")));
        assertThat(service.findAll()).containsEntry("AAPL", "애플");
    }

    @Test
    void 새_이름을_앞뒤_공백을_제거해_저장한다() {
        when(repository.findById("BRK-B")).thenReturn(Optional.empty());
        service.update("BRK-B", " 버크셔 해서웨이 ");
        ArgumentCaptor<UsaStockKoreanName> entry = ArgumentCaptor.forClass(UsaStockKoreanName.class);
        verify(repository).save(entry.capture());
        assertThat(entry.getValue().getKoreanName()).isEqualTo("버크셔 해서웨이");
    }

    @Test
    void 기존_이름을_변경한다() {
        UsaStockKoreanName entry = UsaStockKoreanName.create("AAPL", "애플");
        when(repository.findById("AAPL")).thenReturn(Optional.of(entry));
        service.update("AAPL", "애플 주식");
        verify(repository).save(any(UsaStockKoreanName.class));
        assertThat(entry.getKoreanName()).isEqualTo("애플 주식");
    }

    @Test
    void 빈_이름과_잘못된_티커를_거부한다() {
        assertThatThrownBy(() -> service.update("AAPL", "  ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("aapl", "애플")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("7203", "도요타")).isInstanceOf(BadRequestException.class);
    }
}
