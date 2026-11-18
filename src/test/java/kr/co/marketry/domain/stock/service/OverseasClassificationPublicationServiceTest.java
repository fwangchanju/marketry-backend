package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.co.marketry.domain.stock.entity.OverseasClassificationPublication;
import kr.co.marketry.domain.stock.repository.OverseasClassificationPublicationRepository;
import org.junit.jupiter.api.Test;

class OverseasClassificationPublicationServiceTest {
    private final OverseasClassificationPublicationRepository publications =
            mock(OverseasClassificationPublicationRepository.class);
    private final OverseasClassificationPublicationService service =
            new OverseasClassificationPublicationService(publications);

    @Test
    void 기록이_없으면_없음으로_돌려준다() {
        when(publications.findById("JAPAN")).thenReturn(Optional.empty());

        assertThat(service.findPublishedAt("JAPAN")).isEmpty();
    }

    @Test
    void 처음_기록하면_새_줄을_저장한다() {
        when(publications.findById("JAPAN")).thenReturn(Optional.empty());

        service.recordPublished("JAPAN");

        verify(publications).save(any(OverseasClassificationPublication.class));
    }

    @Test
    void 다시_기록하면_같은_줄의_시각을_새로_쓴다() {
        var existing = OverseasClassificationPublication.create("USA", LocalDateTime.of(2026, 11, 1, 9, 0));
        when(publications.findById("USA")).thenReturn(Optional.of(existing));

        service.recordPublished("USA");

        assertThat(existing.getPublishedAt()).isAfter(LocalDateTime.of(2026, 11, 1, 9, 0));
        verify(publications, org.mockito.Mockito.never()).save(any());
    }
}
