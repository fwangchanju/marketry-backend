package kr.co.marketry.domain.auth.dto;

import java.time.LocalDate;

/** 하루에 접속한 서로 다른 회원 수. */
public record AdminDailyAccessResponse(LocalDate date, long members) {}
