package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.DefaultPreference;
import dev.eolmae.marketry.domain.custom.entity.UserPreference;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.DefaultPreferenceRepository;
import dev.eolmae.marketry.domain.custom.repository.UserPreferenceRepository;
import dev.eolmae.marketry.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TelegramImageLegacySettingServiceTest {
    private final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    private final DefaultPreferenceRepository defaultPreferences = mock(DefaultPreferenceRepository.class);
    private final CustomSectorRepository sectors = mock(CustomSectorRepository.class);
    private final CustomValueTierThresholdService tiers = mock(CustomValueTierThresholdService.class);
    private final TelegramImageLegacySettingService service = new TelegramImageLegacySettingService(
            preferences,
            defaultPreferences,
            sectors,
            tiers,
            new MarketryProperties("http://localhost", 7L, 8L),
            new ObjectMapper());

    @Test
    void 렌더러_소유자_설정과_발행된_업종_제외_경로를_읽는다() {
        UserPreference owner = UserPreference.createEmpty(7L);
        UUID stableKey = UUID.fromString("b8993daa-26f1-4c1d-b09c-a2b03b2d4709");
        owner.overwrite("{\"heatmap.boxSizeMarketCapRatio\":45,\"heatmap.profile.marketry.sectorFilterEnabled\":false,"
                + "\"heatmap.profile.marketry.excludedSectorKeys\":[\"" + stableKey + "\"]}");
        CustomSector parent = CustomSector.createParent(8L, "금융");
        ReflectionTestUtils.setField(parent, "id", 10L);
        CustomSector child = CustomSector.createChild(8L, "지주", parent, stableKey);
        ReflectionTestUtils.setField(child, "id", 11L);
        child.exclude();
        when(preferences.findById(7L)).thenReturn(Optional.of(owner));
        when(sectors.findAllByUserId(8L)).thenReturn(List.of(parent, child));
        when(tiers.getValueTiers(7L)).thenReturn(List.of(new CustomValueTierItem(1L, "대형주", 0L, false)));

        TelegramImageLegacySettingService.Response response = service.find();

        assertThat(response.preferences())
                .containsEntry("heatmap.boxSizeMarketCapRatio", 45)
                .containsEntry("heatmap.sectorFilterEnabled", false)
                .containsEntry("heatmap.excludedSectorKeys", List.of(stableKey.toString()));
        assertThat(response.excludedSectors()).containsExactly(List.of(stableKey.toString(), "금융 > 지주"));
        assertThat(response.tiers()).extracting(CustomValueTierItem::label).containsExactly("대형주");
    }

    @Test
    void 저장된_MARKETRY_기본값이_소유자_개인값보다_캡처에_우선한다() {
        UUID stableKey = UUID.fromString("b8993daa-26f1-4c1d-b09c-a2b03b2d4709");
        UserPreference owner = UserPreference.createEmpty(7L);
        owner.overwrite("{\"heatmap.boxSizeMarketCapRatio\":45,\"heatmap.profile.marketry.excludedSectorKeys\":[]}");
        DefaultPreference defaults = DefaultPreference.create();
        defaults.update("{\"marketry\":{\"boxSizeMarketCapRatio\":80,\"excludedSectorKeys\":[\"" + stableKey + "\"]}}");
        CustomSector sector = CustomSector.createParent(8L, "금융");
        ReflectionTestUtils.setField(sector, "id", 10L);
        ReflectionTestUtils.setField(sector, "stableKey", stableKey);
        when(preferences.findById(7L)).thenReturn(Optional.of(owner));
        when(defaultPreferences.findById(1L)).thenReturn(Optional.of(defaults));
        when(sectors.findAllByUserId(8L)).thenReturn(List.of(sector));
        when(tiers.getValueTiers(7L)).thenReturn(List.of());

        TelegramImageLegacySettingService.Response response = service.findForCapture();

        assertThat(response.preferences()).containsEntry("heatmap.boxSizeMarketCapRatio", 80);
        assertThat(response.preferences()).doesNotContainKey("heatmap.stockLabelModeIndex");
        assertThat(response.excludedSectors()).containsExactly(List.of(stableKey.toString(), "금융"));
        verify(preferences, never()).findById(7L);
    }
}
