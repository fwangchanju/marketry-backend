package dev.eolmae.marketry.domain.auth.dto;

import java.time.LocalDate;

/** 하루의 비회원 방문자 수와 신규 가입 수. conversionRate는 가입 수 ÷ 방문자 수(%)이고, 방문자가 없는 날은 null이다. */
public record AdminVisitorDailyResponse(LocalDate date, long visitors, long joined, Double conversionRate) {}
