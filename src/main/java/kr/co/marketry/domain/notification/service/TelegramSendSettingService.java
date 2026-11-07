package kr.co.marketry.domain.notification.service;

import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.notification.dto.TelegramSendPlan;
import kr.co.marketry.domain.notification.dto.TelegramSendSettingRequest;
import kr.co.marketry.domain.notification.dto.TelegramSendSettingResponse;
import kr.co.marketry.domain.notification.entity.TelegramSendSetting;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import kr.co.marketry.domain.notification.repository.TelegramSendSettingRepository;
import kr.co.marketry.domain.notification.schedule.TelegramSendSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 바꾸는 텔레그램 발송 설정 — 저장된 값이 없으면 꺼짐이고, 시각은 서버 설정값(telegram.*)을 쓴다. */
@Service
@RequiredArgsConstructor
public class TelegramSendSettingService {

    private final TelegramSendSettingRepository repository;
    private final TelegramProperties telegramProperties;

    @Value("${collect.start-hour}")
    private int startHour;

    @Value("${collect.end-hour}")
    private int endHour;

    @Value("${collect.interval-minutes}")
    private int collectIntervalMinutes;

    @Transactional(readOnly = true)
    public TelegramSendPlan current() {
        return repository
                .findById(TelegramSendSetting.SINGLETON_ID)
                .map(this::toPlan)
                .orElseGet(this::defaultPlan);
    }

    @Transactional(readOnly = true)
    public TelegramSendSettingResponse find() {
        return toResponse(repository.findById(TelegramSendSetting.SINGLETON_ID).orElse(null));
    }

    @Transactional
    public TelegramSendSettingResponse update(TelegramSendSettingRequest request) {
        validate(request.sectorSendMinute(), request.sectorSendIntervalMinutes());
        TelegramSendSetting setting =
                repository.findById(TelegramSendSetting.SINGLETON_ID).orElseGet(() -> TelegramSendSetting.create());
        setting.update(request.enabled(), request.sectorSendMinute(), request.sectorSendIntervalMinutes());
        return toResponse(repository.save(setting));
    }

    // 서버가 시작할 때 설정값에 하던 시각 검사와 같은 기준이다 — 저장된 값 때문에 발송 시각 판정이 깨지지 않게 한다.
    private void validate(int sectorSendMinute, int sectorSendIntervalMinutes) {
        try {
            TelegramSendSchedule.validate(
                    sectorSendMinute,
                    sectorSendIntervalMinutes,
                    telegramProperties.beforeMinutes(),
                    collectIntervalMinutes);
        } catch (IllegalStateException exception) {
            throw new BadRequestException(ErrorCode.TELEGRAM_SEND_SETTING_INVALID, exception, exception.getMessage());
        }
    }

    private TelegramSendPlan toPlan(TelegramSendSetting setting) {
        return new TelegramSendPlan(
                setting.isEnabled(), setting.getSectorSendMinute(), setting.getSectorSendIntervalMinutes());
    }

    private TelegramSendPlan defaultPlan() {
        return new TelegramSendPlan(false, telegramProperties.sendMinute(), telegramProperties.sendIntervalMinutes());
    }

    private TelegramSendSettingResponse toResponse(TelegramSendSetting setting) {
        TelegramSendPlan plan = defaultPlan();
        if (setting != null) {
            plan = toPlan(setting);
        }
        return new TelegramSendSettingResponse(
                plan.enabled(),
                plan.sendMinute(),
                plan.sendIntervalMinutes(),
                setting != null,
                setting == null ? null : setting.getUpdatedAt(),
                collectIntervalMinutes,
                startHour,
                endHour,
                telegramProperties.sendMinute(),
                telegramProperties.sendIntervalMinutes());
    }
}
