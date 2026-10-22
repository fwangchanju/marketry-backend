package dev.eolmae.marketry.domain.notification.service;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendPlan;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendSettingRequest;
import dev.eolmae.marketry.domain.notification.dto.TelegramSendSettingResponse;
import dev.eolmae.marketry.domain.notification.entity.TelegramSendSetting;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketry.domain.notification.repository.TelegramSendSettingRepository;
import dev.eolmae.marketry.domain.notification.schedule.TelegramSendSchedule;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 바꾸는 텔레그램 발송 설정 — 저장된 값이 없으면 꺼짐이고, 시각은 서버 설정값(telegram.*)을 쓴다. */
@Service
@RequiredArgsConstructor
public class TelegramSendSettingService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

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
                .map(setting -> new TelegramSendPlan(
                        setting.isEnabled(),
                        setting.getSectorSendMinute(),
                        setting.getSectorSendIntervalMinutes(),
                        setting.mapSendTimeList()))
                .orElseGet(this::defaultPlan);
    }

    @Transactional(readOnly = true)
    public TelegramSendSettingResponse find() {
        Optional<TelegramSendSetting> setting = repository.findById(TelegramSendSetting.SINGLETON_ID);
        return toResponse(setting.orElse(null));
    }

    @Transactional
    public TelegramSendSettingResponse update(TelegramSendSettingRequest request) {
        List<LocalTime> mapSendTimes = List.of();
        if (request.mapSendTimes() != null) {
            mapSendTimes = request.mapSendTimes().stream().distinct().sorted().toList();
        }
        validate(request.sectorSendMinute(), request.sectorSendIntervalMinutes(), mapSendTimes);
        TelegramSendSetting setting =
                repository.findById(TelegramSendSetting.SINGLETON_ID).orElseGet(TelegramSendSetting::create);
        setting.update(
                request.enabled(), request.sectorSendMinute(), request.sectorSendIntervalMinutes(), mapSendTimes);
        return toResponse(repository.save(setting));
    }

    // 서버가 시작할 때 설정값에 하던 검사와 같은 기준이다 — 저장된 값 때문에 발송 시각 판정이 깨지지 않게 한다.
    private void validate(int sectorSendMinute, int sectorSendIntervalMinutes, List<LocalTime> mapSendTimes) {
        try {
            TelegramSendSchedule.validate(
                    sectorSendMinute,
                    sectorSendIntervalMinutes,
                    telegramProperties.beforeMinutes(),
                    mapSendTimes,
                    collectIntervalMinutes,
                    startHour,
                    endHour);
        } catch (IllegalStateException exception) {
            throw new BadRequestException(ErrorCode.TELEGRAM_SEND_SETTING_INVALID, exception, exception.getMessage());
        }
    }

    private TelegramSendPlan defaultPlan() {
        return new TelegramSendPlan(
                false,
                telegramProperties.sendMinute(),
                telegramProperties.sendIntervalMinutes(),
                telegramProperties.mapSendTimes());
    }

    private TelegramSendSettingResponse toResponse(TelegramSendSetting setting) {
        TelegramSendPlan plan = defaultPlan();
        if (setting != null) {
            plan = new TelegramSendPlan(
                    setting.isEnabled(),
                    setting.getSectorSendMinute(),
                    setting.getSectorSendIntervalMinutes(),
                    setting.mapSendTimeList());
        }
        return new TelegramSendSettingResponse(
                plan.enabled(),
                plan.sendMinute(),
                plan.sendIntervalMinutes(),
                formatTimes(plan.mapSendTimes()),
                setting != null,
                setting == null ? null : setting.getUpdatedAt(),
                collectIntervalMinutes,
                startHour,
                endHour,
                telegramProperties.sendMinute(),
                telegramProperties.sendIntervalMinutes(),
                formatTimes(telegramProperties.mapSendTimes()));
    }

    private List<String> formatTimes(List<LocalTime> times) {
        return times.stream().map(TIME_FORMAT::format).toList();
    }
}
