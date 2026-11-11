package kr.co.marketry.domain.auth.dto;

/** 검색 로봇 이름별 방문 횟수(기간 합). */
public record AdminBotNameCountResponse(String name, long visits) {}
