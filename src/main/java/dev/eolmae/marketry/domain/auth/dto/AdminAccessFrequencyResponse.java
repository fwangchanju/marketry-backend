package dev.eolmae.marketry.domain.auth.dto;

/** 최근 30일 중 접속한 날 수 구간(예: "3~6일")과 그 구간에 속한 회원 수. */
public record AdminAccessFrequencyResponse(String label, long members) {}
