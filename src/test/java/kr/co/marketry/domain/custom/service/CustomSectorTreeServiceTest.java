package kr.co.marketry.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.custom.dto.CustomSnapshotPayload;
import kr.co.marketry.domain.custom.entity.CustomScaleThreshold;
import kr.co.marketry.domain.custom.entity.CustomSector;
import kr.co.marketry.domain.custom.entity.CustomStockAlias;
import kr.co.marketry.domain.custom.entity.CustomStockSector;
import kr.co.marketry.domain.custom.entity.CustomValueTierThreshold;
import kr.co.marketry.domain.custom.entity.UserPreference;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.enums.ColorLabel;
import kr.co.marketry.domain.custom.repository.CustomScaleThresholdRepository;
import kr.co.marketry.domain.custom.repository.CustomSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomStockAliasRepository;
import kr.co.marketry.domain.custom.repository.CustomStockSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import kr.co.marketry.domain.custom.repository.UserPreferenceRepository;
import kr.co.marketry.domain.stock.collector.UsaConstituents;
import kr.co.marketry.domain.stock.repository.StockInfoRepository;
import kr.co.marketry.domain.stock.service.StockInfoCacheService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class CustomSectorTreeServiceTest {

    private static final long USER_ID = 41L;

    private final CustomSectorRepository sectorRepository = Mockito.mock(CustomSectorRepository.class);
    private final CustomStockSectorRepository stockSectorRepository = Mockito.mock(CustomStockSectorRepository.class);
    private final CustomStockAliasRepository stockAliasRepository = Mockito.mock(CustomStockAliasRepository.class);
    private final CustomScaleThresholdRepository scaleThresholdRepository =
            Mockito.mock(CustomScaleThresholdRepository.class);
    private final CustomValueTierThresholdRepository valueTierRepository =
            Mockito.mock(CustomValueTierThresholdRepository.class);
    private final UserPreferenceRepository userPreferenceRepository = Mockito.mock(UserPreferenceRepository.class);
    private final StockInfoRepository stockInfoRepository = Mockito.mock(StockInfoRepository.class);
    private final ProtectedSectorService protectedSectorService = Mockito.mock(ProtectedSectorService.class);
    private final SuspendedStockClassificationService suspendedClassification =
            Mockito.mock(SuspendedStockClassificationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomSectorTreeService service = new CustomSectorTreeService(
            sectorRepository,
            stockSectorRepository,
            stockAliasRepository,
            scaleThresholdRepository,
            valueTierRepository,
            userPreferenceRepository,
            new ClassifiableStocks(
                    Mockito.mock(StockInfoCacheService.class), stockInfoRepository, new UsaConstituents()),
            protectedSectorService,
            suspendedClassification,
            objectMapper);

    @Test
    void serializeCurrentSnapshot_사용자_데이터_전체와_v2_버전을_저장한다() throws Exception {
        CustomSector root = CustomSector.createParent(USER_ID, ClassificationMarket.KOREA, "산업");
        ReflectionTestUtils.setField(root, "id", 10L);
        CustomSector child = CustomSector.createChild(USER_ID, "반도체", root);
        ReflectionTestUtils.setField(child, "id", 11L);
        child.exclude();

        when(sectorRepository.findAllByUserIdAndMarket(USER_ID, ClassificationMarket.KOREA))
                .thenReturn(List.of(root, child));
        when(stockSectorRepository.findAllByIdUserIdAndMarket(USER_ID, ClassificationMarket.KOREA))
                .thenReturn(List.of(CustomStockSector.create(USER_ID, ClassificationMarket.KOREA, "005930", 11L)));
        when(stockAliasRepository.findAllByIdUserId(USER_ID))
                .thenReturn(List.of(CustomStockAlias.create(USER_ID, "005930", "삼전")));
        when(scaleThresholdRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(CustomScaleThreshold.create(USER_ID, BigDecimal.ONE, "#ff0000", ColorLabel.RED)));
        when(valueTierRepository.findAllByUserIdOrderByThresholdValueAsc(USER_ID))
                .thenReturn(List.of(CustomValueTierThreshold.create(USER_ID, "대형주", 1000L, false)));
        UserPreference preference = UserPreference.createEmpty(USER_ID);
        preference.overwrite("{\"showValue\":true}");
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(preference));

        String json = service.serializeCurrentSnapshot(USER_ID, ClassificationMarket.KOREA);
        CustomSnapshotPayload snapshot = objectMapper.readValue(json, CustomSnapshotPayload.class);

        assertThat(snapshot.snapshotVersion()).isEqualTo(3);
        assertThat(snapshot.sectors().getFirst().stableKey()).isNotNull();
        assertThat(snapshot.sectors())
                .extracting(CustomSnapshotPayload.Sector::id, CustomSnapshotPayload.Sector::parentId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, null), org.assertj.core.groups.Tuple.tuple(11L, 10L));
        assertThat(snapshot.assignments()).containsExactly(new CustomSnapshotPayload.StockAssignment("005930", 11L));
        assertThat(snapshot.aliases()).containsExactly(new CustomSnapshotPayload.StockAlias("005930", "삼전"));
        assertThat(snapshot.scaleThresholds()).hasSize(1);
        assertThat(snapshot.valueTierThresholds()).hasSize(1);
        assertThat(snapshot.preferences()).containsEntry("showValue", true);
        verify(sectorRepository).findAllByUserIdAndMarket(USER_ID, ClassificationMarket.KOREA);
        verify(stockSectorRepository).findAllByIdUserIdAndMarket(USER_ID, ClassificationMarket.KOREA);
        verify(stockAliasRepository).findAllByIdUserId(USER_ID);
    }

    @Test
    void 버전1_snapshot은_복원할_수_없고_버전2는_호환한다() {
        String legacySnapshot = "{\"snapshotVersion\":1,\"sectors\":[]}";

        assertThat(service.isCurrentSnapshotFormat(legacySnapshot)).isFalse();
        assertThat(service.isCurrentSnapshotFormat("{\"snapshotVersion\":2,\"sectors\":[]}"))
                .isTrue();
        assertThatThrownBy(() -> service.parseSnapshot(legacySnapshot)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void restore_섹터_삭제를_flush한_뒤에_섹터를_저장한다() throws Exception {
        CustomSnapshotPayload snapshot = new CustomSnapshotPayload(
                2,
                List.of(new CustomSnapshotPayload.Sector(10L, null, "산업", 0, false, null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of());
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        Mockito.when(sectorRepository.save(Mockito.any(CustomSector.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(stockInfoRepository.findAllById(Mockito.<Iterable<String>>any()))
                .thenReturn(List.of());

        service.restore(snapshotJson, USER_ID, ClassificationMarket.KOREA, 99L);

        InOrder inOrder = Mockito.inOrder(sectorRepository);
        inOrder.verify(sectorRepository).deleteAll(Mockito.anyList());
        inOrder.verify(sectorRepository).flush();
        inOrder.verify(sectorRepository).save(Mockito.any(CustomSector.class));
    }

    @Test
    void 일본_버전은_업종과_종목만_담고_되돌릴_때_설정을_건드리지_않는다() throws Exception {
        CustomSector japan = CustomSector.createParent(USER_ID, ClassificationMarket.JAPAN, "자동차");
        ReflectionTestUtils.setField(japan, "id", 20L);
        when(sectorRepository.findAllByUserIdAndMarket(USER_ID, ClassificationMarket.JAPAN))
                .thenReturn(List.of(japan));
        when(stockSectorRepository.findAllByIdUserIdAndMarket(USER_ID, ClassificationMarket.JAPAN))
                .thenReturn(List.of(CustomStockSector.create(USER_ID, ClassificationMarket.JAPAN, "7203", 20L)));
        when(sectorRepository.save(Mockito.any(CustomSector.class))).thenAnswer(invocation -> {
            CustomSector saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 21L);
            return saved;
        });

        String json = service.serializeCurrentSnapshot(USER_ID, ClassificationMarket.JAPAN);
        CustomSnapshotPayload snapshot = objectMapper.readValue(json, CustomSnapshotPayload.class);
        service.restore(json, USER_ID, ClassificationMarket.JAPAN, 5L);

        assertThat(snapshot.assignments()).containsExactly(new CustomSnapshotPayload.StockAssignment("7203", 20L));
        assertThat(snapshot.preferences()).isEmpty();
        verify(stockSectorRepository).deleteAllOfUserInMarket(USER_ID, ClassificationMarket.JAPAN);
        verify(stockSectorRepository, Mockito.never()).deleteAllOfUserInMarket(USER_ID, ClassificationMarket.KOREA);
        verify(protectedSectorService).ensureForUser(USER_ID, ClassificationMarket.JAPAN);
        Mockito.verifyNoInteractions(
                stockAliasRepository,
                scaleThresholdRepository,
                valueTierRepository,
                userPreferenceRepository,
                suspendedClassification);
    }
}
