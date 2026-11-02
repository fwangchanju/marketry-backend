package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.UserPreference;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.UserPreferenceRepository;
import dev.eolmae.marketry.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TelegramImageLegacySettingServiceTest {
    private final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    private final CustomSectorRepository sectors = mock(CustomSectorRepository.class);
    private final CustomValueTierThresholdService tiers = mock(CustomValueTierThresholdService.class);
    private final TelegramImageLegacySettingService service = new TelegramImageLegacySettingService(
            preferences, sectors, tiers, new MarketryProperties("http://localhost", 7L, 8L), new ObjectMapper());

    @Test
    void 렌더러_소유자_설정과_발행된_업종_제외_경로를_읽는다() {
        UserPreference owner = UserPreference.createEmpty(7L);
        owner.overwrite("{\"heatmap.boxSizeMarketCapRatio\":45}");
        CustomSector parent = CustomSector.createParent(8L, "금융");
        ReflectionTestUtils.setField(parent, "id", 10L);
        CustomSector child = CustomSector.createChild(8L, "지주", parent);
        ReflectionTestUtils.setField(child, "id", 11L);
        child.exclude();
        when(preferences.findById(7L)).thenReturn(Optional.of(owner));
        when(sectors.findAllByUserId(8L)).thenReturn(List.of(parent, child));
        when(tiers.getValueTiers(7L)).thenReturn(List.of(new CustomValueTierItem(1L, "대형주", 0L, false)));

        TelegramImageLegacySettingService.Response response = service.find();

        assertThat(response.preferences()).isEqualTo(Map.of("heatmap.boxSizeMarketCapRatio", 45));
        assertThat(response.excludedSectors()).containsExactly(List.of(11L, "금융 > 지주"));
        assertThat(response.tiers()).extracting(CustomValueTierItem::label).containsExactly("대형주");
    }
}
