package kr.co.marketry.domain.auth.dto;

import java.time.LocalDate;

/** 하루의 신규 가입 수와, 그날이 끝났을 때까지의 누적 회원 수. */
public record AdminSignupDailyResponse(LocalDate date, long joined, long total) {}
