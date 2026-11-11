package kr.co.marketry.domain.auth.dto;

import java.time.LocalDate;

/** 하루의 검색 로봇 방문 횟수(모든 로봇 합). */
public record AdminBotDayCountResponse(LocalDate date, long visits) {}
