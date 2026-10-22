package dev.eolmae.marketry.domain.notification.dto;

import java.time.LocalTime;
import java.util.List;

/** 지금 적용되는 텔레그램 발송 계획 — 저장된 설정이 있으면 그 값, 없으면 꺼짐과 서버 설정값. */
public record TelegramSendPlan(
        boolean enabled, int sendMinute, int sendIntervalMinutes, List<LocalTime> mapSendTimes) {}
