package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendPlan;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendSettingRequest;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendSettingResponse;
import dev.eolmae.marketry.domain.notification.entity.TelegramSendSetting;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketry.domain.notification.repository.TelegramSendSettingRepository;
import dev.eolmae.marketry.domain.view.enums.AverageMode;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TelegramSendSettingServiceTest {

    private static final List<LocalTime> DEFAULT_MAP_TIMES =
            List.of(LocalTime.of(8, 15), LocalTime.of(9, 15), LocalTime.of(15, 30));

    private final TelegramSendSettingRepository repository = mock(TelegramSendSettingRepository.class);
    private final TelegramProperties properties = new TelegramProperties(
            "token", "chat", "developer", 10, 15, 15, AverageMode.SIMPLE, true, DEFAULT_MAP_TIMES);
    private final TelegramSendSettingService service = new TelegramSendSettingService(repository, properties);

    TelegramSendSettingServiceTest() {
        ReflectionTestUtils.setField(service, "startHour", 8);
        ReflectionTestUtils.setField(service, "endHour", 20);
        ReflectionTestUtils.setField(service, "collectIntervalMinutes", 5);
    }

    @Test
    void 저장된_설정이_없으면_꺼짐이고_시각은_서버_설정값이다() {
        when(repository.findById(TelegramSendSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        TelegramSendPlan plan = service.current();

        assertThat(plan).isEqualTo(new TelegramSendPlan(false, 10, 15, DEFAULT_MAP_TIMES));
    }

    @Test
    void 저장된_설정이_있으면_그_값을_쓴다() {
        TelegramSendSetting setting = TelegramSendSetting.create();
        setting.update(true, 5, 30, List.of(LocalTime.of(9, 0), LocalTime.of(15, 30)));
        when(repository.findById(TelegramSendSetting.SINGLETON_ID)).thenReturn(Optional.of(setting));

        TelegramSendPlan plan = service.current();

        assertThat(plan)
                .isEqualTo(new TelegramSendPlan(true, 5, 30, List.of(LocalTime.of(9, 0), LocalTime.of(15, 30))));
    }

    @Test
    void 처음_저장하면_행을_만들고_지도_시각은_정렬하고_중복을_없앤다() {
        when(repository.findById(TelegramSendSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(TelegramSendSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TelegramSendSettingResponse response = service.update(new TelegramSendSettingRequest(
                true, 10, 15, List.of(LocalTime.of(15, 30), LocalTime.of(9, 15), LocalTime.of(9, 15))));

        assertThat(response.enabled()).isTrue();
        assertThat(response.saved()).isTrue();
        assertThat(response.mapSendTimes()).containsExactly("09:15", "15:30");
        assertThat(response.defaultMapSendTimes()).containsExactly("08:15", "09:15", "15:30");
    }

    @Test
    void 수집_간격의_배수가_아닌_발송_간격은_저장하지_않는다() {
        assertThatThrownBy(() -> service.update(new TelegramSendSettingRequest(true, 10, 12, DEFAULT_MAP_TIMES)))
                .isInstanceOf(BadRequestException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void 지도_발송_시각이_비어_있으면_저장하지_않는다() {
        assertThatThrownBy(() -> service.update(new TelegramSendSettingRequest(true, 10, 15, List.of())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 수집_시간대_밖의_지도_발송_시각은_저장하지_않는다() {
        assertThatThrownBy(() -> service.update(new TelegramSendSettingRequest(
                        true, 10, 15, List.of(LocalTime.of(7, 0), LocalTime.of(15, 30)))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 저장된_행이_없을_때_조회는_저장_안_됨과_기본값을_돌려준다() {
        when(repository.findById(TelegramSendSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        TelegramSendSettingResponse response = service.find();

        assertThat(response.saved()).isFalse();
        assertThat(response.enabled()).isFalse();
        assertThat(response.sectorSendIntervalMinutes()).isEqualTo(15);
        assertThat(response.updatedAt()).isNull();
    }
}
