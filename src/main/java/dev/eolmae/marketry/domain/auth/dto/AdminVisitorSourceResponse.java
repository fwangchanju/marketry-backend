package dev.eolmae.marketry.domain.auth.dto;

import dev.eolmae.marketry.domain.auth.enums.VisitorSource;

/** 한 유입 경로로 들어온 비회원 방문자 수(연인원, 하루에 한 번씩 센 합). */
public record AdminVisitorSourceResponse(VisitorSource source, long visitors) {}
