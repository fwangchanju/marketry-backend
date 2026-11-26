package kr.co.marketry.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.co.marketry.domain.custom.entity.CustomSector;
import kr.co.marketry.domain.custom.entity.CustomStockSector;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.repository.CustomSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomSnapshotRepository;
import kr.co.marketry.domain.custom.repository.CustomStockSectorRepository;
import kr.co.marketry.domain.notification.properties.MarketryProperties;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService;
import kr.co.marketry.domain.stock.service.OverseasClassificationPublicationService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PublishedClassificationServiceTest {

    private static final long PUBLISHED_ID = 900000L;

    private final CustomSectorRepository sectors = mock(CustomSectorRepository.class);
    private final CustomStockSectorRepository stocks = mock(CustomStockSectorRepository.class);
    private final OverseasClassificationCategoryService legacyCategories =
            mock(OverseasClassificationCategoryService.class);
    private final OverseasStockClassificationService legacyStocks = mock(OverseasStockClassificationService.class);
    private final PublishedClassificationService service = new PublishedClassificationService(
            new MarketryProperties("http://localhost", 1L, PUBLISHED_ID),
            sectors,
            stocks,
            mock(CustomSnapshotRepository.class),
            mock(OverseasClassificationCategoryRepository.class),
            legacyCategories,
            legacyStocks,
            mock(OverseasClassificationPublicationService.class));

    @Test
    void 옮기기_전에는_옛_자리를_그대로_읽는다() {
        when(sectors.findAllByUserIdAndMarket(PUBLISHED_ID, ClassificationMarket.JAPAN))
                .thenReturn(List.of());
        when(legacyCategories.findAll("JAPAN"))
                .thenReturn(List.of(new OverseasClassificationCategoryService.Category(5L, null, "금융")));
        when(legacyStocks.findAll("JAPAN"))
                .thenReturn(Map.of("8306", new OverseasStockClassificationService.Classification("금융", "은행", null)));

        assertThat(service.isMoved(ClassificationMarket.JAPAN)).isFalse();
        assertThat(service.findCategories(ClassificationMarket.JAPAN))
                .containsExactly(new PublishedClassificationService.Category(5L, null, "금융", null, null));
        assertThat(service.findClassifications(ClassificationMarket.JAPAN))
                .containsEntry("8306", new PublishedClassificationService.Classification("금융", "은행", null));
    }

    @Test
    void 옮긴_뒤에는_발행_계정의_그_시장_업종을_경로로_돌려준다() {
        CustomSector root = CustomSector.createParent(PUBLISHED_ID, ClassificationMarket.USA, "금융");
        ReflectionTestUtils.setField(root, "id", 10L);
        CustomSector child = CustomSector.createChild(PUBLISHED_ID, "은행", root);
        ReflectionTestUtils.setField(child, "id", 11L);
        CustomSector leaf = CustomSector.createChild(PUBLISHED_ID, "지역 은행", child);
        ReflectionTestUtils.setField(leaf, "id", 12L);
        when(sectors.findAllByUserIdAndMarket(PUBLISHED_ID, ClassificationMarket.USA))
                .thenReturn(List.of(root, child, leaf));
        when(stocks.findAllByIdUserIdAndMarket(PUBLISHED_ID, ClassificationMarket.USA))
                .thenReturn(List.of(
                        CustomStockSector.create(PUBLISHED_ID, ClassificationMarket.USA, "JPM", 11L),
                        CustomStockSector.create(PUBLISHED_ID, ClassificationMarket.USA, "RF", 12L),
                        CustomStockSector.create(PUBLISHED_ID, ClassificationMarket.USA, "BRK-B", 10L)));

        assertThat(service.isMoved(ClassificationMarket.USA)).isTrue();
        assertThat(service.findClassifications(ClassificationMarket.USA))
                .containsEntry("JPM", new PublishedClassificationService.Classification("금융", "은행", null))
                .containsEntry("RF", new PublishedClassificationService.Classification("금융", "은행", "지역 은행"))
                .containsEntry("BRK-B", new PublishedClassificationService.Classification("금융", null, null));
        assertThat(service.findCategories(ClassificationMarket.USA))
                .extracting(PublishedClassificationService.Category::id)
                .containsExactly(10L, 11L, 12L);
        verifyNoInteractions(legacyCategories, legacyStocks);
    }

    @Test
    void 옛_업종_번호의_고정_이름표는_복사_스크립트와_같다() {
        // PostgreSQL에서 SELECT md5('JAPAN:live:36')::uuid 로 얻은 값
        assertThat(PublishedClassificationService.legacyStableKey(ClassificationMarket.JAPAN, 36L))
                .isEqualTo(UUID.fromString("2d218bab-a404-4800-1afa-013579b4c554"));
    }
}
